package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Produced by the UI module (Yu-Han) after the user acts on an [EcoMission].
 * Consumed by the Algorithm & AI module (Duo) for EcoPoints and weekly-insight logic.
 */
@Serializable
data class MissionResult(
    val missionId: String,
    val accepted: Boolean,
    val completed: Boolean,
    val actualTransportMode: TransportMode?,
    val actualCarbonSavingGrams: Double?,
    val timestampMillis: Long,
)
