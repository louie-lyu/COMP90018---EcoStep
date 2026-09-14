package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Produced by the Algorithm & AI module (Duo) via [com.ecostep.app.algorithm.CarbonCalculator].
 * Consumed by the UI module (Yu-Han).
 */
@Serializable
data class CarbonResult(
    val emissionsGrams: Double,
    val lowerCarbonAlternatives: List<CarbonAlternative>,
)

@Serializable
data class CarbonAlternative(
    val mode: TransportMode,
    val savingsGrams: Double,
)
