package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/** Produced by the External API module (Jianing). Part of [MissionContext]. */
@Serializable
data class RouteInfo(
    val mode: TransportMode,
    val distanceMeters: Double,
    val durationSeconds: Long,
)
