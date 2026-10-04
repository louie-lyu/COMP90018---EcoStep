package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Produced by the Sensors & Database module (Zongcheng). See docs/DEPENDENCIES.md
 * "Shared Data Objects". Consumed by the Algorithm & AI module (Duo) and the External
 * API module (Jianing).
 *
 * Fields added for Firestore schema v2 all have defaults, so existing callers and
 * schema v1 documents keep working.
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
    /**
     * Effective transport mode: the user-confirmed mode when present, otherwise the detected
     * mode (read priority confirmed -> legacy `transportMode` -> UNKNOWN).
     */
    val transportMode: TransportMode,
    /** Aggregated sensor features; absent for manual and pre-v1 journeys. */
    val sensorFeatures: SensorFeatures? = null,
    /** Mode produced by the classifier when the journey was recorded. */
    val detectedTransportMode: TransportMode? = null,
    /** Mode the user confirmed on the review screen. */
    val confirmedTransportMode: TransportMode? = null,
    val confirmationStatus: JourneyConfirmationStatus = JourneyConfirmationStatus.PENDING,
    /** Trusted value written by the backend after confirmation; null until then. */
    val carbonSavedGrams: Double? = null,
    /** Trusted value written by the backend (mission award for this journey); null until then. */
    val ecoPoints: Int? = null,
    /** Mission that was active while this journey was recorded. */
    val linkedMissionId: String? = null,
    /**
     * Reserved for an encoded polyline if history map replay is added. No current screen
     * consumes a route, so it stays null and raw GPS points are never uploaded.
     */
    val routePolyline: String? = null,
    /** Server timestamps (UTC epoch millis); null while a write is still pending offline. */
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
)

@Serializable
enum class JourneyConfirmationStatus(val firestoreValue: String) {
    PENDING("pending"),
    CONFIRMED("confirmed"),
    ;

    companion object {
        fun fromFirestore(value: Any?): JourneyConfirmationStatus =
            entries.firstOrNull { it.firestoreValue == value } ?: PENDING
    }
}

/** Validation shared by every journey write path. */
fun JourneySummary.requireValidForWrite() {
    require(journeyId.isNotBlank()) { "Journey ID cannot be empty." }
    require(journeyId.length <= 128 && '/' !in journeyId) { "Journey ID is invalid." }
    require(distanceMeters.isFinite() && distanceMeters >= 0.0 && distanceMeters <= MAX_JOURNEY_DISTANCE_METERS) {
        "Journey distance must be finite, non-negative and at most 1000 km."
    }
    require(startTimeMillis > 0 && endTimeMillis >= startTimeMillis) {
        "Journey times are invalid."
    }
    require(startLocation.isValid() && endLocation.isValid()) {
        "Journey locations are invalid."
    }
    carbonSavedGrams?.let {
        require(it.isFinite() && it >= 0.0) { "Carbon saving must be finite and non-negative." }
    }
    ecoPoints?.let { require(it >= 0) { "EcoPoints cannot be negative." } }
}

private fun GeoPoint.isValid(): Boolean =
    latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0

const val MAX_JOURNEY_DISTANCE_METERS = 1_000_000.0
