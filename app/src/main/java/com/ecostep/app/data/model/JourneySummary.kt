package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Produced by the Sensors & Database module (Zongcheng). See docs/DEPENDENCIES.md
 * "Shared Data Objects". Consumed by the Algorithm & AI module (Duo) and the External
 * API module (Jianing).
 */
@Serializable
data class JourneySummary(
    val journeyId: String,
    val userId: String,
    val startLocation: GeoPoint,
    val endLocation: GeoPoint,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    /** Estimated from sensors, or user-corrected after review. */
    val distanceMeters: Double,
    /** Estimated transport mode, pending user confirmation. */
    val transportMode: TransportMode,
)
