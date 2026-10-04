package com.ecostep.app.algorithm

import com.ecostep.app.data.model.TransportMode

/**
 * Single source of the client's emission factors (grams CO2e per km).
 *
 * The trusted backend mirrors these values in `functions/src/config/scoring.json`;
 * `ScoringConfigContractTest` fails if the two drift apart.
 *
 * Provisional prototype values: the team must confirm the final values with a referenced source.
 */
object EmissionFactors {

    val GRAMS_PER_KM: Map<TransportMode, Double> =
        mapOf(
            TransportMode.WALKING to 0.0,
            TransportMode.CYCLING to 0.0,
            TransportMode.PUBLIC_TRANSPORT to 89.0,
            TransportMode.CAR to 192.0,
        )

    /**
     * Carbon saved versus driving the same distance: `max(0, carEmissions - modeEmissions)`.
     * [TransportMode.UNKNOWN] saves nothing because its emissions cannot be verified.
     */
    fun carbonSavedVersusCarGrams(
        distanceMeters: Double,
        mode: TransportMode,
        factorsGramsPerKm: Map<TransportMode, Double> = GRAMS_PER_KM,
    ): Double {
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) {
            "Journey distance must be finite and non-negative."
        }
        val carFactor = factorsGramsPerKm[TransportMode.CAR] ?: return 0.0
        val modeFactor = factorsGramsPerKm[mode] ?: return 0.0
        return (distanceMeters / 1000.0 * (carFactor - modeFactor)).coerceAtLeast(0.0)
    }
}
