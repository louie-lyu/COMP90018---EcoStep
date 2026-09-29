package com.ecostep.app.algorithm

import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.TransportResult
import com.ecostep.app.data.model.WeatherData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FallbackMissionGeneratorTest {

    private val generator = FallbackMissionGenerator()

    @Test
    fun `selects the alternative with the greatest saving`() = runTest {
        val context = createContext(
            alternatives = listOf(
                CarbonAlternative(
                    mode = TransportMode.PUBLIC_TRANSPORT,
                    savingsGrams = 500.0,
                ),
                CarbonAlternative(
                    mode = TransportMode.CYCLING,
                    savingsGrams = 900.0,
                ),
            ),
        )

        val mission = generator.generateMission(context)

        assertEquals(
            TransportMode.CYCLING,
            mission.recommendedMode,
        )
        assertEquals(
            900.0,
            mission.estimatedCarbonSavingGrams,
            0.001,
        )
        assertEquals(100.0, mission.confidence, 0.001)
    }

    @Test
    fun `ignores invalid alternatives`() = runTest {
        val context = createContext(
            alternatives = listOf(
                CarbonAlternative(
                    mode = TransportMode.UNKNOWN,
                    savingsGrams = 2_000.0,
                ),
                CarbonAlternative(
                    mode = TransportMode.CYCLING,
                    savingsGrams = Double.NaN,
                ),
                CarbonAlternative(
                    mode = TransportMode.PUBLIC_TRANSPORT,
                    savingsGrams = 0.0,
                ),
                CarbonAlternative(
                    mode = TransportMode.WALKING,
                    savingsGrams = 400.0,
                ),
            ),
        )

        val mission = generator.generateMission(context)

        assertEquals(
            TransportMode.WALKING,
            mission.recommendedMode,
        )
    }

    @Test
    fun `creates a clear fallback mission`() = runTest {
        val context = createContext(
            alternatives = listOf(
                CarbonAlternative(
                    mode = TransportMode.WALKING,
                    savingsGrams = 350.0,
                ),
            ),
        )

        val mission = generator.generateMission(context)

        assertTrue(mission.missionId.contains("journey-1"))
        assertTrue(mission.explanation.contains("walking"))
        assertTrue(mission.explanation.contains("350"))
    }

    @Test
    fun `throws when no valid alternative exists`() = runTest {
        val context = createContext(
            alternatives = emptyList(),
        )

        try {
            generator.generateMission(context)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected result.
        }
    }

    private fun createContext(
        alternatives: List<CarbonAlternative>,
    ): MissionContext {
        val journey = JourneySummary(
            journeyId = "journey-1",
            userId = "user-1",
            startLocation = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
            endLocation = GeoPoint(
                latitude = -37.8100,
                longitude = 144.9700,
            ),
            startTimeMillis = 1_000L,
            endTimeMillis = 901_000L,
            distanceMeters = 5_000.0,
            transportMode = TransportMode.CAR,
        )

        return MissionContext(
            journey = journey,
            transportResult = TransportResult(
                mode = TransportMode.CAR,
                confidence = 90.0,
                alternativesConsidered = listOf(
                    TransportMode.PUBLIC_TRANSPORT,
                    TransportMode.CYCLING,
                    TransportMode.WALKING,
                ),
            ),
            carbonResult = CarbonResult(
                emissionsGrams = 1_200.0,
                lowerCarbonAlternatives = alternatives,
            ),
            route = RouteInfo(
                mode = TransportMode.CAR,
                distanceMeters = 5_000.0,
                durationSeconds = 900L,
            ),
            weather = WeatherData(
                temperatureCelsius = 20.0,
                conditions = "Clear",
            ),
            publicTransportOptions = emptyList(),
            recentJourneyHistory = emptyList(),
        )
    }
}