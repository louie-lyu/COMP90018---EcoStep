package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.network.ai.AiGateway
import com.ecostep.app.network.ai.AiMissionSuggestion
import com.ecostep.app.network.ai.AiWeeklyAdvice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class DefaultAiWeeklyCoachTest {

    @Test
    fun `combines AI advice with locally calculated statistics`() = runTest {
        val ai = FakeAi {
            goodAdvice()
        }

        val result = DefaultAiWeeklyCoach(ai).generate(
            results = listOf(
                mission("one", saving = 100.0),
                mission("two", saving = 150.0),
                mission("three", completed = false),
            ),
            weekStartMillis = 1_000L,
            weekEndMillis = 2_000L,
        )

        assertTrue(result.usedAi)
        assertEquals(3, result.report.acceptedMissions)
        assertEquals(2, result.report.completedMissions)
        assertEquals(
            250.0,
            result.report.totalCarbonSavingGrams,
            0.001,
        )
        assertEquals(goodAdvice().insight, result.insight)
        assertEquals(goodAdvice().action, result.action)
        assertEquals(1, ai.calls)
    }

    @Test
    fun `does not call AI for an empty week`() = runTest {
        val ai = FakeAi {
            error("AI should not be called.")
        }

        val result = DefaultAiWeeklyCoach(ai).generate(
            results = emptyList(),
            weekStartMillis = 1_000L,
            weekEndMillis = 2_000L,
        )

        assertFalse(result.usedAi)
        assertEquals(0, ai.calls)
        assertEquals(result.report.summary, result.insight)
        assertEquals(result.report.fallbackMessage, result.action)
    }

    @Test
    fun `retries blank advice and accepts a valid second response`() = runTest {
        val ai = FakeAi { attempt ->
            if (attempt == 1) {
                AiWeeklyAdvice(insight = " ", action = "")
            } else {
                goodAdvice()
            }
        }

        val result = DefaultAiWeeklyCoach(ai).generate(
            results = listOf(mission("one")),
            weekStartMillis = 1_000L,
            weekEndMillis = 2_000L,
        )

        assertTrue(result.usedAi)
        assertEquals(2, ai.calls)
        assertTrue(ai.prompts.last().contains("Retry:"))
    }

    @Test
    fun `uses fallback after two invalid responses`() = runTest {
        val ai = FakeAi {
            AiWeeklyAdvice(
                insight = "x".repeat(1001),
                action = "Try one small mission.",
            )
        }

        val result = DefaultAiWeeklyCoach(ai).generate(
            results = listOf(mission("one")),
            weekStartMillis = 1_000L,
            weekEndMillis = 2_000L,
        )

        assertFalse(result.usedAi)
        assertEquals(2, ai.calls)
        assertEquals(result.report.summary, result.insight)
        assertEquals(result.report.fallbackMessage, result.action)
    }

    @Test
    fun `uses fallback when the network fails`() = runTest {
        val ai = FakeAi {
            throw IOException("Simulated network failure")
        }

        val result = DefaultAiWeeklyCoach(ai).generate(
            results = listOf(mission("one")),
            weekStartMillis = 1_000L,
            weekEndMillis = 2_000L,
        )

        assertFalse(result.usedAi)
        assertEquals(2, ai.calls)
        assertTrue(result.action.isNotBlank())
    }

    @Test
    fun `does not retry a quota error`() = runTest {
        val ai = FakeAi {
            throw HttpException(
                Response.error<Any>(
                    429,
                    "Quota exceeded".toResponseBody(),
                ),
            )
        }

        val result = DefaultAiWeeklyCoach(ai).generate(
            results = listOf(mission("one")),
            weekStartMillis = 1_000L,
            weekEndMillis = 2_000L,
        )

        assertFalse(result.usedAi)
        assertEquals(1, ai.calls)
    }

    @Test
    fun `does not swallow cancellation`() = runTest {
        val ai = FakeAi {
            throw CancellationException("User left the screen")
        }

        try {
            DefaultAiWeeklyCoach(ai).generate(
                results = listOf(mission("one")),
                weekStartMillis = 1_000L,
                weekEndMillis = 2_000L,
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(1, ai.calls)
        }
    }

    @Test
    fun `sends weekly totals without mission identifiers`() = runTest {
        val ai = FakeAi {
            goodAdvice()
        }

        DefaultAiWeeklyCoach(ai).generate(
            results = listOf(
                mission("private-mission-123", saving = 80.0),
                mission(
                    "outside-week",
                    saving = 900.0,
                    timestamp = 3_000L,
                ),
            ),
            weekStartMillis = 1_000L,
            weekEndMillis = 2_000L,
        )

        val prompt = ai.prompts.single()

        assertTrue(prompt.contains("80.0 grams"))
        assertTrue(prompt.contains("Completed missions: 1"))
        assertFalse(prompt.contains("private-mission-123"))
        assertFalse(prompt.contains("outside-week"))
        assertFalse(prompt.contains("900.0"))
    }

    private fun goodAdvice(): AiWeeklyAdvice {
        return AiWeeklyAdvice(
            insight = "You made progress with your low-carbon missions.",
            action = "Choose one achievable mission next week.",
        )
    }

    private fun mission(
        id: String,
        completed: Boolean = true,
        saving: Double = 100.0,
        timestamp: Long = 1_500L,
    ): MissionResult {
        return MissionResult(
            missionId = id,
            accepted = true,
            completed = completed,
            actualTransportMode = TransportMode.WALKING,
            actualCarbonSavingGrams = saving,
            timestampMillis = timestamp,
        )
    }

    private class FakeAi(
        private val answer: (Int) -> AiWeeklyAdvice,
    ) : AiGateway {

        var calls = 0
        val prompts = mutableListOf<String>()

        override suspend fun generateWeeklyAdvice(
            prompt: String,
        ): AiWeeklyAdvice {
            calls++
            prompts.add(prompt)
            return answer(calls)
        }

        override suspend fun generateMission(
            prompt: String,
        ): AiMissionSuggestion {
            error("Mission generation is not used in these tests.")
        }
    }
}