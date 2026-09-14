package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Produced by the Algorithm & AI module (Duo) via [com.ecostep.app.algorithm.TransportClassifier].
 * Consumed by the External API module (Jianing) and the UI module (Yu-Han).
 */
@Serializable
data class TransportResult(
    val mode: TransportMode,
    /** 0–100, per docs/DEPENDENCIES.md ("Confidence score (0–100%)"). */
    val confidence: Double,
    val alternativesConsidered: List<TransportMode>,
)
