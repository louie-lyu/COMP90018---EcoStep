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
import com.ecostep.app.network.ai.AiGateway
import com.ecostep.app.network.ai.AiMissionSuggestion
import com.ecostep.app.network.ai.AiWeeklyAdvice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class DefaultAiMissionGeneratorTest {

    @Test
    fun `uses AI choice with exact verified carbon savings`() = runTest {
        val ai = FakeAi {
            suggestion(TransportMode.CYCLING)
        }

        val result = DefaultAiMissionGenerator(ai)
            .generateRecommendation(context())

        assertTrue(result.usedAi)
        assertEquals(TransportMode.CYCLING, result.mission.recommendedMode)
        assertEquals(
            350.25,
            result.mission.estimatedCarbonSavingGrams,
            0.001,
        )
        assertEquals("Try a low-carbon journey", result.notificationTitle)
        assertEquals(1, ai.calls)
    }

    @Test
    fun `retries an unlisted mode and accepts the second response`() = runTest {
        val ai = FakeAi { attempt ->
            if (attempt == 1) {
                suggestion(TransportMode.CAR)
            } else {
                suggestion(TransportMode.WALKING)
            }
        }

        val result = DefaultAiMissionGenerator(ai)
            .generateRecommendation(context())

        assertTrue(result.usedAi)
        assertEquals(TransportMode.WALKING, result.mission.recommendedMode)
        assertEquals(2, ai.calls)
        assertTrue(ai.prompts.last().contains("Retry:"))
    }

    @Test
    fun `uses fallback after two invalid responses`() = runTest {
        val ai = FakeAi {
            suggestion(TransportMode.UNKNOWN)
        }

        val result = DefaultAiMissionGenerator(ai)
            .generateRecommendation(context())

        assertFalse(result.usedAi)
        assertEquals(TransportMode.WALKING, result.mission.recommendedMode)
        assertEquals(500.0, result.mission.estimatedCarbonSavingGrams, 0.001)
        assertEquals(2, ai.calls)
    }

    @Test
    fun `uses fallback when network requests fail`() = runTest {
        val ai = FakeAi {
            throw IOException("Simulated network failure")
        }

        val result = DefaultAiMissionGenerator(ai)
            .generateRecommendation(context())

        assertFalse(result.usedAi)
        assertTrue(result.notificationMessage.isNotBlank())
        assertEquals(2, ai.calls)
    }

    @Test
    fun `rejects blank notification text`() = runTest {
        val ai = FakeAi {
            suggestion(TransportMode.WALKING).copy(
                notificationMessage = " ",
            )
        }

        val result = DefaultAiMissionGenerator(ai)
            .generateRecommendation(context())

        assertFalse(result.usedAi)
        assertEquals(2, ai.calls)
    }

    @Test
    fun `does not swallow cancellation`() = runTest {
        val ai = FakeAi {
            throw CancellationException("User left the screen")
        }

        try {
            DefaultAiMissionGenerator(ai)
                .generateRecommendation(context())
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, ai.calls)
        }
    }

    @Test
    fun `rejects public transport without a service option`() = runTest {
        val ai = FakeAi {
            suggestion(TransportMode.PUBLIC_TRANSPORT)
        }

        val input = context().copy(
            carbonResult = CarbonResult(
                emissionsGrams = 800.0,
                lowerCarbonAlternatives = listOf(
                    CarbonAlternative(
                        mode = TransportMode.PUBLIC_TRANSPORT,
                        savingsGrams = 300.0,
                    ),
                ),
            ),
        )

        try {
            DefaultAiMissionGenerator(ai)
                .generateRecommendation(input)
            fail("Expected missing-alternative error")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, ai.calls)
        }
    }

    private fun suggestion(
        mode: TransportMode,
    ): AiMissionSuggestion {
        return AiMissionSuggestion(
            recommendedMode = mode,
            explanation = "Consider this available lower-carbon option.",
            confidence = 80.0,
            notificationTitle = "Try a low-carbon journey",
            notificationMessage = "Choose an available low-carbon option today.",
        )
    }

    private fun context(): MissionContext {
        val journey = JourneySummary(
            journeyId = "test-journey",
            userId = "test-user",
            startLocation = GeoPoint(-37.8136, 144.9631),
            endLocation = GeoPoint(-37.8100, 144.9700),
            startTimeMillis = 1_000L,
            endTimeMillis = 601_000L,
            distanceMeters = 2_000.0,
            transportMode = TransportMode.CAR,
        )

        return MissionContext(
            journey = journey,
            transportResult = TransportResult(
                mode = TransportMode.CAR,
                confidence = 90.0,
                alternativesConsidered = listOf(
                    TransportMode.WALKING,
                    TransportMode.CYCLING,
                ),
            ),
            carbonResult = CarbonResult(
                emissionsGrams = 800.0,
                lowerCarbonAlternatives = listOf(
                    CarbonAlternative(TransportMode.WALKING, 500.0),
                    CarbonAlternative(TransportMode.CYCLING, 350.25),
                ),
            ),
            route = RouteInfo(
                mode = TransportMode.CAR,
                distanceMeters = 2_000.0,
                durationSeconds = 600L,
            ),
            weather = WeatherData(
                temperatureCelsius = 20.0,
                conditions = "Clear",
            ),
            publicTransportOptions = emptyList(),
            recentJourneyHistory = emptyList(),
        )
    }

    private class FakeAi(
        private val answer: (Int) -> AiMissionSuggestion,
    ) : AiGateway {

        var calls = 0
        val prompts = mutableListOf<String>()

        override suspend fun generateMission(
            prompt: String,
        ): AiMissionSuggestion {
            calls++
            prompts.add(prompt)
            return answer(calls)
        }

        override suspend fun generateWeeklyAdvice(
            prompt: String,
        ): AiWeeklyAdvice {
            error("Weekly advice is not used in these tests.")
        }
    }
}