package com.ecostep.app.algorithm

import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.network.ai.AiGateway
import com.ecostep.app.network.ai.AiMissionSuggestion
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.util.Locale

// Keeps notification text alongside the mission without changing EcoMission.
data class MissionRecommendation(
    val mission: EcoMission,
    val notificationTitle: String,
    val notificationMessage: String,
    val usedAi: Boolean,
)

class DefaultAiMissionGenerator(
    private val aiGateway: AiGateway,
    private val promptBuilder: MissionPromptBuilder =
        DefaultMissionPromptBuilder(),
    private val validator: MissionValidator =
        DefaultMissionValidator(),
    private val fallback: MissionGenerator =
        FallbackMissionGenerator(),
) : MissionGenerator {

    override suspend fun generateMission(
        context: MissionContext,
    ): EcoMission {
        return generateRecommendation(context).mission
    }

    suspend fun generateRecommendation(
        context: MissionContext,
    ): MissionRecommendation {
        require(context.journey.journeyId.isNotBlank()) {
            "A journey ID is required."
        }

        // Public transport also requires an available service option.
        val alternatives = context.carbonResult.lowerCarbonAlternatives
            .filter {
                it.mode != TransportMode.UNKNOWN &&
                        it.savingsGrams.isFinite() &&
                        it.savingsGrams > 0.0 &&
                        (
                                it.mode != TransportMode.PUBLIC_TRANSPORT ||
                                        context.publicTransportOptions.isNotEmpty()
                                )
            }

        require(alternatives.isNotEmpty()) {
            "No valid lower-carbon alternative is available."
        }

        val verifiedContext = context.copy(
            carbonResult = context.carbonResult.copy(
                lowerCarbonAlternatives = alternatives,
            ),
        )

        val basePrompt = promptBuilder.buildPrompt(verifiedContext)

        // At most two AI attempts.
        for (attempt in 0 until 2) {
            val prompt = if (attempt == 0) {
                basePrompt
            } else {
                basePrompt +
                        "\nRetry: choose only a listed alternative. " +
                        "Include every required field, a confidence from 0 to 100, " +
                        "a notification title within 80 characters, " +
                        "and a notification message within 240 characters."
            }

            try {
                val suggestion = aiGateway.generateMission(prompt)
                val recommendation = validateSuggestion(
                    verifiedContext,
                    suggestion,
                )

                if (recommendation != null) {
                    return recommendation
                }
            } catch (error: CancellationException) {
                // Respect cancellation when the user leaves the screen.
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

        val mission = fallback.generateMission(verifiedContext)

        check(validator.isValid(mission)) {
            "Fallback returned an invalid mission."
        }

        return MissionRecommendation(
            mission = mission,
            notificationTitle = "Your EcoStep mission",
            notificationMessage = mission.explanation,
            usedAi = false,
        )
    }

    private fun validateSuggestion(
        context: MissionContext,
        suggestion: AiMissionSuggestion,
    ): MissionRecommendation? {
        val alternative = context.carbonResult.lowerCarbonAlternatives
            .firstOrNull {
                it.mode == suggestion.recommendedMode
            }
            ?: return null

        val explanation = suggestion.explanation.trim()
        val title = suggestion.notificationTitle.trim()
        val message = suggestion.notificationMessage.trim()

        if (explanation.isEmpty() || explanation.length > 1000) {
            return null
        }

        if (title.isEmpty() || title.length > 80) {
            return null
        }

        if (message.isEmpty() || message.length > 240) {
            return null
        }

        val mission = EcoMission(
            missionId = "ai-${context.journey.journeyId}-" +
                    suggestion.recommendedMode.name.lowercase(Locale.ROOT),
            recommendedMode = alternative.mode,
            estimatedCarbonSavingGrams = alternative.savingsGrams,
            explanation = explanation,
            confidence = suggestion.confidence,
        )

        if (!validator.isValid(mission)) {
            return null
        }

        return MissionRecommendation(
            mission = mission,
            notificationTitle = title,
            notificationMessage = message,
            usedAi = true,
        )
    }
}