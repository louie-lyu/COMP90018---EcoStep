package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.SensorFeatures
import com.ecostep.app.data.model.TransportMode
import com.google.firebase.Timestamp
import java.util.Date

/*
 * Firestore <-> JourneySummary mapping for users/{uid}/journeys/{journeyId}.
 *
 * Schema v1 (no schemaVersion): journeyId, userId, start/endLocation, start/endTimeMillis,
 * distanceMeters, transportMode, optional sensorFeatures.
 * Schema v2 adds detected/confirmedTransportMode, confirmationStatus, linkedMissionId,
 * routePolyline, createdAt/updatedAt, plus backend-only carbonSavedGrams and ecoPoints.
 * Every v2 field is optional on read, so v1 documents decode unchanged.
 */

internal const val JOURNEY_SCHEMA_VERSION = 2

/** Client-owned data fields. Never contains backend-only fields (carbonSavedGrams, ecoPoints). */
internal fun JourneySummary.toFirestoreMap(): Map<String, Any?> = buildMap {
    put("schemaVersion", JOURNEY_SCHEMA_VERSION)
    put("journeyId", journeyId)
    put("userId", userId)
    put("startLocation", startLocation.toFirestoreMap())
    put("endLocation", endLocation.toFirestoreMap())
    put("startTimeMillis", startTimeMillis)
    put("endTimeMillis", endTimeMillis)
    put("distanceMeters", distanceMeters)
    // Kept for older app versions, which only read this field.
    put("transportMode", transportMode.name)
    detectedTransportMode?.let { put("detectedTransportMode", it.name) }
    confirmedTransportMode?.let { put("confirmedTransportMode", it.name) }
    put("confirmationStatus", confirmationStatus.firestoreValue)
    put("linkedMissionId", linkedMissionId)
    put("routePolyline", routePolyline)
    sensorFeatures?.let { put("sensorFeatures", it.toFirestoreMap()) }
}

/** Document body for the first write; [serverTimestamp] is `FieldValue.serverTimestamp()`. */
internal fun JourneySummary.toFirestoreCreateMap(serverTimestamp: Any): Map<String, Any?> =
    toFirestoreMap() + mapOf(
        "createdAt" to serverTimestamp,
        "updatedAt" to serverTimestamp,
    )

/** Fields changed when the user confirms a mode; nothing else is touched. */
internal fun confirmationUpdate(mode: TransportMode, serverTimestamp: Any): Map<String, Any> =
    mapOf(
        "confirmedTransportMode" to mode.name,
        "transportMode" to mode.name,
        "confirmationStatus" to JourneyConfirmationStatus.CONFIRMED.firestoreValue,
        "updatedAt" to serverTimestamp,
    )

private fun GeoPoint.toFirestoreMap(): Map<String, Any> = mapOf(
    "latitude" to latitude,
    "longitude" to longitude,
)

private fun SensorFeatures.toFirestoreMap(): Map<String, Any> = mapOf(
    "featureVersion" to featureVersion,
    "averageSpeedMps" to averageSpeedMps,
    "p95SpeedMps" to p95SpeedMps,
    "maxSpeedMps" to maxSpeedMps,
    "stopRatio" to stopRatio,
    "averageGpsAccuracyMeters" to averageGpsAccuracyMeters,
    "gpsSampleCount" to gpsSampleCount,
    "accelMagnitudeMean" to accelMagnitudeMean,
    "accelMagnitudeStd" to accelMagnitudeStd,
    "accelSampleCount" to accelSampleCount,
    "gyroMagnitudeMean" to gyroMagnitudeMean,
    "gyroMagnitudeStd" to gyroMagnitudeStd,
    "gyroSampleCount" to gyroSampleCount,
)

/** Decodes a journey document; throws [IllegalStateException] when required data is invalid. */
internal fun Map<String, Any?>.toJourneySummary(documentId: String): JourneySummary {
    val detected = optionalMode("detectedTransportMode")
    val confirmed = optionalMode("confirmedTransportMode")
    val legacyMode = optionalMode("transportMode")
    val distance = number("distanceMeters").toDouble()
    if (!distance.isFinite() || distance < 0.0) {
        throw IllegalStateException("Journey $documentId field 'distanceMeters' is invalid.")
    }
    return JourneySummary(
        journeyId = (this["journeyId"] as? String)?.takeIf { it.isNotBlank() } ?: documentId,
        userId = string("userId"),
        startLocation = location("startLocation"),
        endLocation = location("endLocation"),
        startTimeMillis = number("startTimeMillis").toLong(),
        endTimeMillis = number("endTimeMillis").toLong(),
        distanceMeters = distance,
        transportMode = confirmed ?: legacyMode ?: detected ?: TransportMode.UNKNOWN,
        sensorFeatures = sensorFeatures(documentId),
        detectedTransportMode = detected,
        confirmedTransportMode = confirmed,
        confirmationStatus = JourneyConfirmationStatus.fromFirestore(this["confirmationStatus"]),
        carbonSavedGrams = (this["carbonSavedGrams"] as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it >= 0.0 },
        ecoPoints = (this["ecoPoints"] as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it >= 0.0 && it <= Int.MAX_VALUE }
            ?.toInt(),
        linkedMissionId = (this["linkedMissionId"] as? String)?.takeIf { it.isNotBlank() },
        routePolyline = this["routePolyline"] as? String,
        createdAtMillis = timestampMillis(this["createdAt"]),
        updatedAtMillis = timestampMillis(this["updatedAt"]),
    )
}

/** Like [toJourneySummary] but returns null for a corrupt document instead of failing a list. */
internal fun Map<String, Any?>.toJourneySummaryOrNull(documentId: String): JourneySummary? =
    try {
        toJourneySummary(documentId)
    } catch (_: IllegalStateException) {
        null
    }

/** Pending offline server timestamps read as null; Firestore may also hand back Date. */
internal fun timestampMillis(value: Any?): Long? = when (value) {
    is Timestamp -> value.toDate().time
    is Date -> value.time
    is Number -> value.toLong()
    else -> null
}

private fun Map<String, Any?>.sensorFeatures(documentId: String): SensorFeatures? {
    val rawFeatures = this["sensorFeatures"] ?: return null
    val features = rawFeatures as? Map<*, *>
        ?: throw IllegalStateException(
            "Journey $documentId field 'sensorFeatures' is invalid.",
        )

    fun number(field: String): Number = features[field] as? Number
        ?: throw IllegalStateException(
            "Journey $documentId field 'sensorFeatures.$field' is missing or invalid.",
        )

    return SensorFeatures(
        featureVersion = (features["featureVersion"] as? Number)?.toInt() ?: 1,
        averageSpeedMps = number("averageSpeedMps").toDouble(),
        p95SpeedMps = number("p95SpeedMps").toDouble(),
        maxSpeedMps = number("maxSpeedMps").toDouble(),
        stopRatio = number("stopRatio").toDouble(),
        averageGpsAccuracyMeters = number("averageGpsAccuracyMeters").toDouble(),
        gpsSampleCount = number("gpsSampleCount").toInt(),
        accelMagnitudeMean = number("accelMagnitudeMean").toDouble(),
        accelMagnitudeStd = number("accelMagnitudeStd").toDouble(),
        accelSampleCount = number("accelSampleCount").toInt(),
        gyroMagnitudeMean = number("gyroMagnitudeMean").toDouble(),
        gyroMagnitudeStd = number("gyroMagnitudeStd").toDouble(),
        gyroSampleCount = number("gyroSampleCount").toInt(),
    )
}

private fun Map<String, Any?>.string(field: String): String =
    this[field] as? String
        ?: throw IllegalStateException("Journey field '$field' is missing or invalid.")

private fun Map<String, Any?>.number(field: String): Number =
    this[field] as? Number
        ?: throw IllegalStateException("Journey field '$field' is missing or invalid.")

private fun Map<String, Any?>.optionalMode(field: String): TransportMode? =
    (this[field] as? String)?.toTransportMode()

private fun Map<String, Any?>.location(field: String): GeoPoint {
    val location = this[field] as? Map<*, *>
        ?: throw IllegalStateException("Journey field '$field' is missing or invalid.")
    val latitude = location["latitude"] as? Number
        ?: throw IllegalStateException("Journey field '$field.latitude' is missing or invalid.")
    val longitude = location["longitude"] as? Number
        ?: throw IllegalStateException("Journey field '$field.longitude' is missing or invalid.")
    return GeoPoint(latitude.toDouble(), longitude.toDouble())
}

/** Unknown or future mode names fall back to UNKNOWN instead of failing the document. */
internal fun String.toTransportMode(): TransportMode =
    TransportMode.entries.firstOrNull { it.name == this }
        ?: TransportMode.UNKNOWN
