package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.network.ai.AiGateway
import com.ecostep.app.network.ai.AiWeeklyAdvice
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

// Keeps calculated statistics separate from AI-generated text.
data class WeeklyCoachingResult(
    val report: WeeklyCoachReport,
    val insight: String,
    val action: String,
    val usedAi: Boolean,
)

class DefaultAiWeeklyCoach(
    private val aiGateway: AiGateway,
    private val weeklyCoach: WeeklyCoach = DefaultWeeklyCoach(),
) {

    suspend fun generate(
        results: List<MissionResult>,
        weekStartMillis: Long,
        weekEndMillis: Long,
    ): WeeklyCoachingResult {
        // Statistics are always calculated locally.
        val report = weeklyCoach.generate(
            results = results,
            weekStartMillis = weekStartMillis,
            weekEndMillis = weekEndMillis,
        )

        // There is no weekly activity to personalise yet.
        if (report.acceptedMissions == 0) {
            return fallbackResult(report)
        }

        for (attempt in 0 until 2) {
            val prompt = if (attempt == 0) {
                report.aiPrompt
            } else {
                report.aiPrompt +
                        "\nRetry: return a JSON object containing non-empty " +
                        "insight and action strings. Keep each within 1000 " +
                        "characters and use only the supplied weekly statistics."
            }

            try {
                val advice = aiGateway.generateWeeklyAdvice(prompt)

                if (isValid(advice)) {
                    return WeeklyCoachingResult(
                        report = report,
                        insight = advice.insight.trim(),
                        action = advice.action.trim(),
                        usedAi = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Retrying cannot fix permission or quota errors.
                if (
                    error is HttpException &&
                    error.code() in listOf(401, 403, 429)
                ) {
                    break
                }
            }
        }

        return fallbackResult(report)
    }

    private fun isValid(advice: AiWeeklyAdvice): Boolean {
        val insight = advice.insight.trim()
        val action = advice.action.trim()

        return insight.isNotEmpty() &&
                insight.length <= 1000 &&
                action.isNotEmpty() &&
                action.length <= 1000
    }

    private fun fallbackResult(
        report: WeeklyCoachReport,
    ): WeeklyCoachingResult {
        return WeeklyCoachingResult(
            report = report,
            insight = report.summary,
            action = report.fallbackMessage,
            usedAi = false,
        )
    }
}