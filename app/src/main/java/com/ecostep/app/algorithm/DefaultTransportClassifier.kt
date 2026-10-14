package com.ecostep.app.algorithm

import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.SensorFeatures
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.TransportResult
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Google activity results are mapped to these four simple types.
enum class MotionHint {
    WALKING, CYCLING, IN_VEHICLE, UNKNOWN
}

data class ActivityHint(
    val type: MotionHint,
    val confidence: Int,
)

// The trace is used only for this classification, not added to JourneySummary or saved to Firebase.
data class TransportTrackPoint(
    val latitude: Double,
    val longitude: Double,
    val timeMillis: Long,
    val speedMps: Double,
    val accuracyMeters: Double,
)

data class TransitStop(
    val latitude: Double,
    val longitude: Double,
)

data class TransportEvidence(
    val activity: ActivityHint? = null,
    val track: List<TransportTrackPoint> = emptyList(),
    // Each inner list represents a bus or tram route, with stops in travel order.
    val transitRoutes: List<List<TransitStop>> = emptyList(),
)

interface TransportEvidenceProvider {
    suspend fun getEvidence(journey: JourneySummary): TransportEvidence
}

class DefaultTransportClassifier(
    private val evidenceProvider: TransportEvidenceProvider,
) : TransportClassifier {

    override suspend fun classify(journey: JourneySummary): TransportResult {
        val evidence = evidenceProvider.getEvidence(journey)
        val activity = evidence.activity
            ?.takeIf { it.confidence >= 60 }
            ?.type
            ?: inferFromSensors(journey.sensorFeatures)

        return when (activity) {
            MotionHint.WALKING -> result(
                TransportMode.WALKING,
                70.0,
                listOf(TransportMode.CYCLING),
            )

            MotionHint.CYCLING -> result(
                TransportMode.CYCLING,
                70.0,
                listOf(TransportMode.WALKING),
            )

            MotionHint.IN_VEHICLE -> classifyVehicle(evidence)

            MotionHint.UNKNOWN -> result(
                TransportMode.UNKNOWN,
                0.0,
                listOf(
                    TransportMode.WALKING,
                    TransportMode.CYCLING,
                    TransportMode.PUBLIC_TRANSPORT,
                    TransportMode.CAR,
                ),
            )
        }
    }

    private fun inferFromSensors(features: SensorFeatures?): MotionHint {
        if (features == null || features.gpsSampleCount < 2) {
            return MotionHint.UNKNOWN
        }

        val average = features.averageSpeedMps
        val p95 = features.p95SpeedMps
        val maximum = features.maxSpeedMps
        if (!average.isFinite() || !p95.isFinite() || !maximum.isFinite()) {
            return MotionHint.UNKNOWN
        }

        return when {
            average <= 2.2 && p95 <= 3.5 -> MotionHint.WALKING
            average >= 2.2 && p95 <= 10.0 && maximum <= 15.0 ->
                MotionHint.CYCLING
            p95 > 10.0 || maximum > 15.0 -> MotionHint.IN_VEHICLE
            else -> MotionHint.UNKNOWN
        }
    }

    private fun classifyVehicle(evidence: TransportEvidence): TransportResult {
        val matchedRoute = evidence.transitRoutes.any { stops ->
            countMatchedStops(evidence.track, stops) >= 2
        }

        if (matchedRoute) {
            return result(
                TransportMode.PUBLIC_TRANSPORT,
                75.0,
                listOf(TransportMode.CAR),
            )
        }

        // Transit candidates exist, but the trace is too short to match stops.
        if (evidence.transitRoutes.isNotEmpty() && evidence.track.size < 4) {
            return result(
                TransportMode.UNKNOWN,
                40.0,
                listOf(
                    TransportMode.PUBLIC_TRANSPORT,
                    TransportMode.CAR,
                ),
            )
        }

        // With no transit candidates or matching stops, provisionally classify as car,
        // keeping low confidence so the user can correct it on the review screen.
        return result(
            TransportMode.CAR,
            55.0,
            listOf(TransportMode.PUBLIC_TRANSPORT),
        )
    }

    private fun countMatchedStops(
        track: List<TransportTrackPoint>,
        stops: List<TransitStop>,
    ): Int {
        val validTrack = track
            .filter { it.accuracyMeters in 0.0..30.0 }
            .sortedBy { it.timeMillis }

        var lastMatchedTime = Long.MIN_VALUE
        var count = 0

        for (stop in stops) {
            val nearby = validTrack.filter { point ->
                point.timeMillis > lastMatchedTime &&
                        point.speedMps <= 1.5 &&
                        distanceMeters(
                            point.latitude,
                            point.longitude,
                            stop.latitude,
                            stop.longitude,
                        ) <= 60.0
            }

            // A stop requires at least two locations spanning about ten seconds.
            if (
                nearby.size >= 2 &&
                nearby.last().timeMillis -
                nearby.first().timeMillis >= 10_000L
            ) {
                count++
                lastMatchedTime = nearby.last().timeMillis
            }
        }

        return count
    }

    private fun distanceMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double,
    ): Double {
        val deltaLat = Math.toRadians(latitude2 - latitude1)
        val deltaLon = Math.toRadians(longitude2 - longitude1)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(Math.toRadians(latitude1)) *
                cos(Math.toRadians(latitude2)) *
                sin(deltaLon / 2) * sin(deltaLon / 2)

        return 12_742_000.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun result(
        mode: TransportMode,
        confidence: Double,
        alternatives: List<TransportMode>,
    ) = TransportResult(
        mode = mode,
        confidence = confidence,
        alternativesConsidered = alternatives,
    )
}