package com.ecostep.app.algorithm

import com.ecostep.app.data.model.TransportMode

/**
 * Single source of the client's emission factors (grams CO2e per km).
 *
 * The trusted backend mirrors these values in `functions/src/config/scoring.json`;
 * `ScoringConfigContractTest` fails if the two drift apart.
 *
 * Source: UK Government GHG Conversion Factors 2026, full set.
 * https://www.gov.uk/government/publications/greenhouse-gas-reporting-conversion-factors-2026
 * Car: average car, unknown fuel, 0.16591 kg CO2e per vehicle-km (Passenger vehicles, X54).
 * Assumes one occupant, so all car emissions are attributed to the user.
 * Public transport: average local bus, 0.10151 kg CO2e per passenger-km
 * (Business travel- land, D81), used as a simplified proxy for all public transport modes.
 * Walking and non-electric cycling have zero direct operational emissions.
 * These UK-based estimates exclude full lifecycle emissions and are not
 * Melbourne-specific measurements. Comparisons assume the same distance.
 */
object EmissionFactors {

    val GRAMS_PER_KM: Map<TransportMode, Double> =
        mapOf(
            TransportMode.WALKING to 0.0,
            TransportMode.CYCLING to 0.0,
            TransportMode.PUBLIC_TRANSPORT to 101.51,
            TransportMode.CAR to 165.91,
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
