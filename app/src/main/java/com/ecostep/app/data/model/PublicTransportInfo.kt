package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/** Produced by the External API module (Jianing), e.g. from PTV. Part of [MissionContext]. */
@Serializable
data class PublicTransportInfo(
    val line: String,
    val departureTimeMillis: Long,
    val estimatedDurationSeconds: Long,
)
