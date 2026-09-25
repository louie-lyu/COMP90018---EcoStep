package com.ecostep.app.algorithm

import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode

class DefaultCarbonCalculator(
    private val emissionFactorsGramsPerKm: Map<TransportMode, Double> =
        DEFAULT_EMISSION_FACTORS,
) : CarbonCalculator {

    override fun calculate(journey: JourneySummary): CarbonResult {
        require(journey.distanceMeters >= 0) {
            "Journey distance cannot be negative."
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

    private companion object {
        /*
         * Provisional prototype values.
         * The team must confirm the final values with a referenced source.
         */
        val DEFAULT_EMISSION_FACTORS =
            mapOf(
                TransportMode.WALKING to 0.0,
                TransportMode.CYCLING to 0.0,
                TransportMode.PUBLIC_TRANSPORT to 89.0,
                TransportMode.CAR to 192.0,
            )
    }
}