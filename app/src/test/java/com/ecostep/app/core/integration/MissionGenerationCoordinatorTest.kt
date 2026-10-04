package com.ecostep.app.core.integration

import com.ecostep.app.algorithm.DefaultAiMissionGenerator
import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.algorithm.DefaultRecurringJourneyDetector
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.data.repository.WriteOutcome
import com.ecostep.app.network.ai.AiGateway
import com.ecostep.app.network.ai.AiMissionSuggestion
import com.ecostep.app.network.ai.AiWeeklyAdvice
import com.ecostep.app.testing.FakeAuthRepository
import com.ecostep.app.testing.FakeJourneyRepository
import com.ecostep.app.testing.FakeMissionRepository
import com.ecostep.app.testing.FakeProfileRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.testing.testJourney
import com.ecostep.app.ui.viewmodels.JourneyReviewViewModel
import java.io.IOException
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MissionGenerationCoordinatorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = 24L * 60 * 60 * 1_000
    private val firstTrip = 1_757_800_000_000L
    private val now = firstTrip + 15 * day

    private val auth = FakeAuthRepository(currentUserId = "user-a")
    private val journeys = FakeJourneyRepository(auth)
    private val profiles = FakeProfileRepository(auth)
    private val missions = FakeMissionRepository()

    private class FakeExternalData(
        var failWeather: Boolean = false,
        var failTransit: Boolean = false,
    ) : ExternalDataRepository {
        var transitCalls = 0

        override suspend fun getWeather(location: GeoPoint): WeatherData {
            if (failWeather) throw IOException("offline")
            return WeatherData(18.0, "Clear")
        }

        override suspend fun getRouteOptions(start: GeoPoint, end: GeoPoint): List<RouteInfo> =
            listOf(RouteInfo(TransportMode.CAR, 2_400.0, 420))

        override suspend fun getPublicTransportOptions(
            start: GeoPoint,
            end: GeoPoint,
        ): List<PublicTransportInfo> {
            transitCalls++
            if (failTransit) throw IOException("offline")
            return listOf(PublicTransportInfo("Tram 19", 0L, 900))
        }
    }

    private class FakeAi(
        var suggestion: AiMissionSuggestion? = null,
    ) : AiGateway {
        val prompts = mutableListOf<String>()

        override suspend fun generateMission(prompt: String): AiMissionSuggestion {
            prompts += prompt
            return suggestion ?: throw IOException("AI unavailable")
        }

        override suspend fun generateWeeklyAdvice(prompt: String): AiWeeklyAdvice =
            throw UnsupportedOperationException()
    }

    private val externalData = FakeExternalData()
    private val ai = FakeAi()

    private fun suggestion(mode: TransportMode) = AiMissionSuggestion(
        recommendedMode = mode,
        explanation = "Cycling this short commute is quick in clear weather.",
        confidence = 80.0,
        notificationTitle = "Ride today?",
        notificationMessage = "Your usual trip is a short ride.",
    )

    private fun coordinator(
        missionRepository: com.ecostep.app.data.repository.MissionRepository = missions,
    ) = MissionGenerationCoordinator(
        authRepository = auth,
        profileRepository = profiles,
        journeyRepository = journeys,
        missionRepository = missionRepository,
        externalDataRepository = externalData,
        recurringJourneyDetector = DefaultRecurringJourneyDetector(zoneId = ZoneOffset.UTC),
        carbonCalculator = DefaultCarbonCalculator(),
        ecoPointsCalculator = DefaultEcoPointsCalculator(),
        missionGenerator = DefaultAiMissionGenerator(aiGateway = ai),
        placeName = { point -> if (point.latitude < -37.805) "University" else "Home street" },
        clock = { now },
        zoneId = ZoneOffset.UTC,
    )

    /** Same route at the same time of day, one week apart. */
    private fun commute(mode: TransportMode = TransportMode.CAR, trips: Int = 3): JourneySummary {
        val all = (0 until trips).map { week ->
            testJourney(
                journeyId = "j$week",
                transportMode = mode,
                startTimeMillis = firstTrip + week * 7 * day,
            )
        }
        all.forEach { journeys.seed("user-a", it) }
        return all.last()
    }

    private fun createdMission(): Mission = missions.missions.value.values.single()

    @Test
    fun `a one-off route creates no mission`() = runTest {
        val journey = commute(trips = 1)

        assertEquals(MissionGenerationOutcome.NotRecurring, coordinator().onJourneyConfirmed(journey))
        assertTrue(missions.missions.value.isEmpty())
        assertTrue(ai.prompts.isEmpty())
    }

    @Test
    fun `routine learning switched off creates no mission`() = runTest {
        profiles.updatePreference(PreferenceUpdate.RoutineLearning(false))
        val journey = commute()

        assertEquals(MissionGenerationOutcome.Disabled, coordinator().onJourneyConfirmed(journey))
        assertTrue(missions.missions.value.isEmpty())
    }

    @Test
    fun `zero-emission routes have no lower-carbon alternative`() = runTest {
        val journey = commute(mode = TransportMode.WALKING)

        assertEquals(MissionGenerationOutcome.NoAlternative, coordinator().onJourneyConfirmed(journey))
        assertTrue(missions.missions.value.isEmpty())
    }

    @Test
    fun `AI suggestion becomes a suggested mission`() = runTest {
        ai.suggestion = suggestion(TransportMode.CYCLING)
        val journey = commute()

        val outcome = coordinator().onJourneyConfirmed(journey)

        assertEquals(MissionGenerationOutcome.Created("ai-j2-cycling", usedAi = true), outcome)
        val mission = createdMission()
        assertEquals(MissionStatus.SUGGESTED, mission.status)
        assertEquals(TransportMode.CYCLING, mission.targetTransportMode)
        assertEquals("Home street → University", mission.title)
        assertFalse("Raw coordinates must not reach the AI", ai.prompts.first().contains("144.96"))
    }

    @Test
    fun `public transport failure still allows walking or cycling`() = runTest {
        externalData.failTransit = true
        ai.suggestion = suggestion(TransportMode.PUBLIC_TRANSPORT)
        val journey = commute()

        val outcome = coordinator().onJourneyConfirmed(journey) as MissionGenerationOutcome.Created

        assertFalse(outcome.usedAi)
        val mission = createdMission()
        assertTrue(mission.targetTransportMode in setOf(TransportMode.WALKING, TransportMode.CYCLING))
        assertTrue(mission.estimates.none { it.mode == TransportMode.PUBLIC_TRANSPORT })
        assertFalse(ai.prompts.first().contains("- PUBLIC_TRANSPORT"))
    }

    @Test
    fun `AI failure persists the fallback mission`() = runTest {
        val journey = commute()

        val outcome = coordinator().onJourneyConfirmed(journey) as MissionGenerationOutcome.Created

        assertFalse(outcome.usedAi)
        assertTrue(createdMission().missionId.startsWith("fallback-j2-"))
        assertEquals(2, ai.prompts.size)
    }

    @Test
    fun `missing weather skips the AI and uses the fallback`() = runTest {
        externalData.failWeather = true
        ai.suggestion = suggestion(TransportMode.CYCLING)
        val journey = commute()

        val outcome = coordinator().onJourneyConfirmed(journey) as MissionGenerationOutcome.Created

        assertFalse(outcome.usedAi)
        assertTrue(ai.prompts.isEmpty())
    }

    @Test
    fun `the same journey is handled only once`() = runTest {
        ai.suggestion = suggestion(TransportMode.CYCLING)
        val journey = commute()
        val coordinator = coordinator()

        coordinator.onJourneyConfirmed(journey)
        val again = coordinator.onJourneyConfirmed(journey)
        val nextWeek = coordinator.onJourneyConfirmed(journey.copy(journeyId = "j3"))

        assertEquals(MissionGenerationOutcome.AlreadyHandled, again)
        assertEquals(MissionGenerationOutcome.AlreadyHandled, nextWeek)
        assertEquals(1, missions.missions.value.size)
    }

    @Test
    fun `mission-linked journeys never create suggestions`() = runTest {
        val journey = commute().copy(linkedMissionId = "m1")

        assertEquals(MissionGenerationOutcome.LinkedToMission, coordinator().onJourneyConfirmed(journey))
    }

    @Test
    fun `a storage failure is reported, not thrown, and can be retried`() = runTest {
        val failing = object : com.ecostep.app.data.repository.MissionRepository by missions {
            override suspend fun createMission(mission: Mission): WriteOutcome =
                throw IllegalStateException("PERMISSION_DENIED")
        }
        val journey = commute()
        val coordinator = coordinator(missionRepository = failing)

        val first = coordinator.onJourneyConfirmed(journey)
        val retry = coordinator.onJourneyConfirmed(journey)

        assertEquals(MissionGenerationOutcome.Failed("IllegalStateException"), first)
        assertEquals(MissionGenerationOutcome.Failed("IllegalStateException"), retry)
    }

    @Test
    fun `review still reports success when mission generation fails`() {
        val failing = object : com.ecostep.app.data.repository.MissionRepository by missions {
            override suspend fun createMission(mission: Mission): WriteOutcome =
                throw IllegalStateException("PERMISSION_DENIED")
        }
        val journey = commute()
        val coordinator = coordinator(missionRepository = failing)
        val review = JourneyReviewViewModel(
            journeyRepository = journeys,
            journeyId = journey.journeyId,
            onJourneyConfirmed = { coordinator.onJourneyConfirmed(it) },
        )

        review.saveJourney()

        assertTrue(review.uiState.value.isSaved)
        assertNull(review.uiState.value.saveErrorMessage)
    }
}
