package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.TransportMode
import java.util.Locale
import kotlin.math.roundToInt

class DefaultMissionPromptBuilder : MissionPromptBuilder {

    override fun buildPrompt(
        context: MissionContext,
    ): String {
        val validAlternatives = context.carbonResult
            .lowerCarbonAlternatives
            .filter { alternative ->
                alternative.mode != TransportMode.UNKNOWN &&
                        alternative.savingsGrams.isFinite() &&
                        alternative.savingsGrams > 0.0
            }

        require(validAlternatives.isNotEmpty()) {
            "At least one valid lower-carbon alternative is required"
        }

        val distanceKilometres = String.format(
            Locale.US,
            "%.1f",
            context.route.distanceMeters / 1_000.0,
        )

        val durationMinutes =
            (context.route.durationSeconds / 60.0).roundToInt()

        val alternativesText = validAlternatives.joinToString(
            separator = "\n",
        ) { alternative ->
            "- ${alternative.mode.name}: " +
                    "${alternative.savingsGrams.roundToInt()} g CO2 saving"
        }

        val publicTransportText =
            if (context.publicTransportOptions.isEmpty()) {
                "No verified public transport option"
            } else {
                context.publicTransportOptions.joinToString(
                    separator = ", ",
                ) { option ->
                    val optionDurationMinutes =
                        (option.estimatedDurationSeconds / 60.0).roundToInt()

                    "${option.line} ($optionDurationMinutes minutes)"
                }
            }

        val recentModesText =
            if (context.recentJourneyHistory.isEmpty()) {
                "No recent journey history"
            } else {
                context.recentJourneyHistory.joinToString(
                    separator = ", ",
                ) { journey ->
                    readableMode(journey.transportMode)
                }
            }

        return """
            You are EcoStep's mobility coach.

            Recommend one lower-carbon transport option using only the verified information below.
            Do not invent routes, weather, public transport services, carbon values, or user details.

            Journey information:
            - Current transport mode: ${context.transportResult.mode.name}
            - Route distance: $distanceKilometres km
            - Estimated duration: $durationMinutes minutes
            - Weather: ${context.weather.conditions}, ${context.weather.temperatureCelsius.roundToInt()} C
            - Public transport: $publicTransportText
            - Recent transport modes: $recentModesText

            Verified lower-carbon alternatives:
            $alternativesText

            Rules:
            1. Choose exactly one mode from the verified alternatives.
            2. Do not calculate or change the carbon-saving value.
            3. Keep the explanation practical and concise.
            4. Keep the notification message short.
            5. Return JSON only, without Markdown or additional text.

            Required JSON format:
            {
              "recommendedMode": "MODE_FROM_VERIFIED_ALTERNATIVES",
              "explanation": "One or two short sentences",
              "confidence": 0,
              "notificationTitle": "Short title",
              "notificationMessage": "Short message"
            }
        """.trimIndent()
    }

    private fun readableMode(
        mode: TransportMode,
    ): String {
        return when (mode) {
            TransportMode.WALKING -> "walking"
            TransportMode.CYCLING -> "cycling"
            TransportMode.PUBLIC_TRANSPORT -> "public transport"
            TransportMode.CAR -> "car"
            TransportMode.UNKNOWN -> "unknown"
        }
    }
}