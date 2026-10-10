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

// 加速度模长的标准差小于等于 steadyAccelStd 时，认为整段行程很平稳（没有步伐冲击或踩踏振动）。
// 阈值必须先用带标签的录音（SHL 评估）校准；样本数不足时不参与判断。
data class MotionThresholds(
    val steadyAccelStd: Double,
    val minAccelSamples: Int,
) {
    init {
        require(steadyAccelStd.isFinite() && steadyAccelStd > 0.0) {
            "steadyAccelStd must be finite and positive."
        }
        require(minAccelSamples > 0) { "minAccelSamples must be positive." }
    }

    fun isSteady(features: SensorFeatures): Boolean =
        features.accelSampleCount >= minAccelSamples &&
            features.accelMagnitudeStd.isFinite() &&
            features.accelMagnitudeStd <= steadyAccelStd
}

// 校准后在这里填入阈值。为 null 时只用速度规则，App 和评估主结果都与之前一致。
val CALIBRATED_MOTION_THRESHOLDS: MotionThresholds? = null

class DefaultTransportClassifier(
    private val evidenceProvider: TransportEvidenceProvider,
    private val motionThresholds: MotionThresholds? = CALIBRATED_MOTION_THRESHOLDS,
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

        val bySpeed = when {
            average <= 2.2 && p95 <= 3.5 -> MotionHint.WALKING
            average >= 2.2 && p95 <= 10.0 && maximum <= 15.0 ->
                MotionHint.CYCLING
            p95 > 10.0 || maximum > 15.0 -> MotionHint.IN_VEHICLE
            else -> MotionHint.UNKNOWN
        }
        return correctWithMotion(bySpeed, features)
    }

    // 速度仍是主要依据；加速度只用来否决速度规则最容易判错的两种情况。
    private fun correctWithMotion(
        bySpeed: MotionHint,
        features: SensorFeatures,
    ): MotionHint {
        val thresholds = motionThresholds ?: return bySpeed
        if (!thresholds.isSteady(features)) return bySpeed

        return when (bySpeed) {
            // 速度像骑车却很平稳：多半是慢速行驶的公交或汽车，再用公交站点区分。
            MotionHint.CYCLING -> MotionHint.IN_VEHICLE
            // 速度像步行却没有步伐冲击：可能是堵车或原地等待，交给用户在确认页选择。
            MotionHint.WALKING -> MotionHint.UNKNOWN
            else -> bySpeed
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

        // 有公交候选路线但轨迹不足时，无法完成站点匹配。
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

        // 没有公交候选路线或未匹配到站点：暂判汽车，
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