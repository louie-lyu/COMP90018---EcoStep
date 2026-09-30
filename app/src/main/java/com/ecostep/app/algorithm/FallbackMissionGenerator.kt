package com.ecostep.app.algorithm

import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.TransportMode
import kotlin.math.roundToInt

class FallbackMissionGenerator : MissionGenerator {

    override suspend fun generateMission(
        context: MissionContext,
    ): EcoMission {
        val bestAlternative = context.carbonResult
            .lowerCarbonAlternatives
            .filter { alternative ->
                alternative.mode != TransportMode.UNKNOWN &&
                        alternative.savingsGrams.isFinite() &&
                        alternative.savingsGrams > 0.0
            }
            .maxByOrNull { alternative ->
                alternative.savingsGrams
            }
            ?: throw IllegalArgumentException(
                "No valid lower-carbon alternative is available",
            )

        val readableMode = when (bestAlternative.mode) {
            TransportMode.WALKING -> "walking"
            TransportMode.CYCLING -> "cycling"
            TransportMode.PUBLIC_TRANSPORT -> "public transport"
            TransportMode.CAR -> "car"
            TransportMode.UNKNOWN -> "another transport mode"
        }

        return EcoMission(
            missionId = buildMissionId(
                journeyId = context.journey.journeyId,
                mode = bestAlternative.mode,
            ),
            recommendedMode = bestAlternative.mode,
            estimatedCarbonSavingGrams = bestAlternative.savingsGrams,
            explanation = "Try $readableMode for this journey to save about " +
                    "${bestAlternative.savingsGrams.roundToInt()} g of CO2.",
            confidence = 100.0,
        )
    }

    private fun buildMissionId(
        journeyId: String,
        mode: TransportMode,
    ): String {
        return "fallback-$journeyId-${mode.name.lowercase()}"
    }
}