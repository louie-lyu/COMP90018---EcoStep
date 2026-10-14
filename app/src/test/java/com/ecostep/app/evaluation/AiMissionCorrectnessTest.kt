package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.DefaultAiMissionGenerator
import com.ecostep.app.network.ai.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AiMissionCorrectnessTest(private val name: String, private val verify: suspend () -> Unit) {
    @Test fun handlesReplyAndBuildsSafePrompt() = runTest { verify() }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = AiMissionEvaluationCases.cases().map { case ->
            val verify: suspend () -> Unit = {
                val ai = ControlledAi(case.reply)
                val result = try {
                    DefaultAiMissionGenerator(ai).generateRecommendation(case.context)
                } catch (error: Exception) {
                    assertEquals("Unexpected exception", case.expected.error, error.javaClass.simpleName)
                    null
                }
                if (result != null) {
                    assertNull("Expected an exception", case.expected.error)
                    validateRecommendation(result, case.expected)
                    if (result.usedAi) {
                        val reply = ai.replies.last()
                        assertEquals(reply.explanation.trim(), result.mission.explanation)
                        assertEquals(reply.confidence, result.mission.confidence, 0.0)
                        assertEquals(reply.notificationTitle.trim(), result.notificationTitle)
                        assertEquals(reply.notificationMessage.trim(), result.notificationMessage)
                    }
                }
                assertEquals(case.expected.calls, ai.prompts.size)
                for (prompt in ai.prompts) {
                    for (journey in case.context.recentJourneyHistory + case.context.journey) {
                        val privateValues = listOf(journey.journeyId, journey.userId,
                            journey.startLocation.latitude.toString(), journey.startLocation.longitude.toString(),
                            journey.endLocation.latitude.toString(), journey.endLocation.longitude.toString())
                        assertTrue(privateValues.filter { it.isNotBlank() }.none { it in prompt })
                    }
                    assertTrue("5.0 km" in prompt && "15 minutes" in prompt && "Clear, 20 C" in prompt)
                    if (case.context.publicTransportOptions.isEmpty()) assertFalse("- PUBLIC_TRANSPORT:" in prompt)
                }
                if (ai.prompts.size == 2) {
                    assertTrue(ai.prompts.last().startsWith(ai.prompts.first()) && "Retry:" in ai.prompts.last())
                }
            }
            arrayOf(case.name, verify)
        }
    }

    private class ControlledAi(private val reply: (Int) -> AiMissionSuggestion) : AiGateway {
        val prompts = mutableListOf<String>()
        val replies = mutableListOf<AiMissionSuggestion>()
        override suspend fun generateMission(prompt: String): AiMissionSuggestion {
            prompts += prompt
            return reply(prompts.size).also { replies += it }
        }
        override suspend fun generateWeeklyAdvice(prompt: String): AiWeeklyAdvice = error("Not used")
    }
}
