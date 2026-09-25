package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.SensorFeatures
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.JourneyRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class FirestoreJourneyRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
) : JourneyRepository {

    override fun observeJourneyHistory(userId: String): Flow<List<JourneySummary>> = callbackFlow {
        val uid = requireCurrentUserId()
        require(userId == uid) { "Cannot read another user's journeys." }

        val listener = journeys(uid).addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                close(exception)
                return@addSnapshotListener
            }

            try {
                val history = snapshot
                    ?.documents
                    .orEmpty()
                    .map { it.toJourneySummary() }
                    .sortedByDescending { it.startTimeMillis }
                trySend(history)
            } catch (mappingException: Exception) {
                close(mappingException)
            }
        }

        awaitClose { listener.remove() }
    }

    override suspend fun getJourney(journeyId: String): JourneySummary? {
        val uid = requireCurrentUserId()
        val document = journeys(uid).document(journeyId)
        // Pending offline writes are available in the cache before server acknowledgement.
        val cached = try {
            document.get(Source.CACHE).await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
        if (cached?.exists() == true && cached.metadata.hasPendingWrites()) {
            return cached.toJourneySummary()
        }
        val snapshot = document.get().await()
        return if (snapshot.exists()) snapshot.toJourneySummary() else null
    }

    override suspend fun saveJourney(journey: JourneySummary) {
        val uid = requireCurrentUserId()
        require(journey.journeyId.isNotBlank()) { "Journey ID cannot be empty." }

        val ownedJourney = journey.copy(userId = uid)
        journeys(uid)
            .document(ownedJourney.journeyId)
            .set(ownedJourney.toFirestoreMap())
            .await()
    }

    private fun requireCurrentUserId(): String =
        authRepository.currentUserId
            ?: throw IllegalStateException("You must be signed in to access journeys.")

    private fun journeys(uid: String) =
        firestore.collection("users").document(uid).collection("journeys")
}

internal fun JourneySummary.toFirestoreMap(): Map<String, Any> = buildMap {
    put("journeyId", journeyId)
    put("userId", userId)
    put(
        "startLocation",
        mapOf(
            "latitude" to startLocation.latitude,
            "longitude" to startLocation.longitude,
        ),
    )
    put(
        "endLocation",
        mapOf(
            "latitude" to endLocation.latitude,
            "longitude" to endLocation.longitude,
        ),
    )
    put("startTimeMillis", startTimeMillis)
    put("endTimeMillis", endTimeMillis)
    put("distanceMeters", distanceMeters)
    put("transportMode", transportMode.name)
    sensorFeatures?.let { put("sensorFeatures", it.toFirestoreMap()) }
}

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

private fun DocumentSnapshot.toJourneySummary(): JourneySummary {
    val data = data ?: throw IllegalStateException("Journey $id has no data.")
    return data.toJourneySummary(id)
}

internal fun Map<String, Any>.toJourneySummary(documentId: String): JourneySummary {
    return JourneySummary(
        journeyId = string("journeyId"),
        userId = string("userId"),
        startLocation = location("startLocation"),
        endLocation = location("endLocation"),
        startTimeMillis = number("startTimeMillis").toLong(),
        endTimeMillis = number("endTimeMillis").toLong(),
        distanceMeters = number("distanceMeters").toDouble(),
        transportMode = string("transportMode").toTransportMode(),
        sensorFeatures = sensorFeatures(documentId),
    )
}

private fun Map<String, Any>.sensorFeatures(documentId: String): SensorFeatures? {
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

private fun Map<String, Any>.string(field: String): String =
    this[field] as? String
        ?: throw IllegalStateException("Journey field '$field' is missing or invalid.")

private fun Map<String, Any>.number(field: String): Number =
    this[field] as? Number
        ?: throw IllegalStateException("Journey field '$field' is missing or invalid.")

private fun Map<String, Any>.location(field: String): GeoPoint {
    val location = this[field] as? Map<*, *>
        ?: throw IllegalStateException("Journey field '$field' is missing or invalid.")
    val latitude = location["latitude"] as? Number
        ?: throw IllegalStateException("Journey field '$field.latitude' is missing or invalid.")
    val longitude = location["longitude"] as? Number
        ?: throw IllegalStateException("Journey field '$field.longitude' is missing or invalid.")
    return GeoPoint(latitude.toDouble(), longitude.toDouble())
}

private fun String.toTransportMode(): TransportMode =
    TransportMode.entries.firstOrNull { it.name == this }
        ?: TransportMode.UNKNOWN

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        val exception = task.exception
        when {
            task.isSuccessful -> continuation.resume(task.result)
            exception != null -> continuation.resumeWithException(exception)
            else -> continuation.resumeWithException(
                IllegalStateException("Firebase request failed."),
            )
        }
    }
}
