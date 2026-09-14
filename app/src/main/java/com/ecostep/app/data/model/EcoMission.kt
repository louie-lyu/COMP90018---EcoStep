package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Produced by the Algorithm & AI module (Duo), from an AI response or the non-AI fallback.
 * Consumed by the UI module (Yu-Han).
 */
@Serializable
data class EcoMission(
    val missionId: String,
    val recommendedMode: TransportMode,
    val estimatedCarbonSavingGrams: Double,
    val explanation: String,
    /** 0–100, same scale as [TransportResult.confidence] — don't mix scales across the algorithm module. */
    val confidence: Double,
)
