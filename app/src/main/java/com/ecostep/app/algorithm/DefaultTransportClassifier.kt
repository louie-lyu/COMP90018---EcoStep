package com.ecostep.app.algorithm

import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.SensorFeatures
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.TransportResult
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Google 的活动结果会在下一步转换成这四种简单类型。
enum class MotionHint {
    WALKING, CYCLING, IN_VEHICLE, UNKNOWN
}

data class ActivityHint(
    val type: MotionHint,
    val confidence: Int,
)

// 轨迹只用于本次分类，不加入 JourneySummary，也不保存到 Firebase。
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
    // 每个内部列表是一条公交或电车路线，站点按行驶顺序排列。
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

        // 没有公交数据时不能断定是汽车。
        if (evidence.transitRoutes.isEmpty() || evidence.track.size < 4) {
            return result(
                TransportMode.UNKNOWN,
                40.0,
                listOf(
                    TransportMode.PUBLIC_TRANSPORT,
                    TransportMode.CAR,
                ),
            )
        }

        // 有轨迹和公交候选路线，但没有按顺序匹配到站点：暂判汽车，
        // 保留较低置信度，用户仍可在行程确认页更正。
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

            // 至少两次定位、持续约十秒，才算在此站停靠。
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