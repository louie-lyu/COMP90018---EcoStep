package com.ecostep.app.algorithm

import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode

class DefaultCarbonCalculator(
    private val emissionFactorsGramsPerKm: Map<TransportMode, Double> =
        EmissionFactors.GRAMS_PER_KM,
) : CarbonCalculator {

    override fun calculate(journey: JourneySummary): CarbonResult {
        require(
            journey.distanceMeters.isFinite() &&
                journey.distanceMeters >= 0.0,
        ) {
            "Journey distance must be finite and non-negative."
        }

        val distanceKm = journey.distanceMeters / 1000.0

        val currentFactor =
            emissionFactorsGramsPerKm[journey.transportMode]
                ?: return CarbonResult(
                    emissionsGrams = 0.0,
                    lowerCarbonAlternatives = emptyList(),
                )

        val currentEmissions = distanceKm * currentFactor

        val alternatives =
            emissionFactorsGramsPerKm
                .filter { (mode, factor) ->
                    mode != journey.transportMode &&
                            mode != TransportMode.UNKNOWN &&
                            factor < currentFactor
                }
                .map { (mode, factor) ->
                    val alternativeEmissions = distanceKm * factor

                    CarbonAlternative(
                        mode = mode,
                        savingsGrams =
                            (currentEmissions - alternativeEmissions)
                                .coerceAtLeast(0.0),
                    )
                }

        return CarbonResult(
            emissionsGrams = currentEmissions,
            lowerCarbonAlternatives = alternatives,
        )
    }
}
