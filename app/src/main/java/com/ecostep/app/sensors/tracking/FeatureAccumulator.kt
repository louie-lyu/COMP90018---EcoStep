package com.ecostep.app.sensors.tracking

import com.ecostep.app.data.model.SensorFeatures
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val timeMillis: Long,
)

enum class MotionType { ACCELEROMETER, GYROSCOPE }
data class MotionSample(val type: MotionType, val x: Float, val y: Float, val z: Float)

/** Keeps only endpoints and summary statistics, never the raw track or motion samples. */
class FeatureAccumulator {
    private val accelMagnitude = RunningStats()
    private val accelDynamic = RunningStats()
    private val gyroMagnitude = RunningStats()
    private val gpsAccuracy = RunningStats()
    private val speeds = mutableListOf<Double>()

    var distanceMeters = 0.0
        private set
    var first: LocationSample? = null
        private set
    var last: LocationSample? = null
        private set
    val gpsCount: Int get() = speeds.size
    val accelCount: Long get() = accelMagnitude.count
    val gyroCount: Long get() = gyroMagnitude.count

    fun onMotion(sample: MotionSample) {
        val x = sample.x.toDouble()
        val y = sample.y.toDouble()
        val z = sample.z.toDouble()
        val magnitude = sqrt(x * x + y * y + z * z)
        when (sample.type) {
            MotionType.ACCELEROMETER -> {
                accelMagnitude.add(magnitude)
                accelDynamic.add(abs(magnitude - 9.80665))
            }
            MotionType.GYROSCOPE -> gyroMagnitude.add(magnitude)
        }
    }

    fun onLocation(sample: LocationSample): Boolean {
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite() ||
            sample.latitude !in -90.0..90.0 || sample.longitude !in -180.0..180.0 ||
            !sample.accuracyMeters.isFinite() || sample.accuracyMeters < 0f ||
            sample.accuracyMeters > 30f
        ) return false

        val previous = last
        val gap = if (previous == null) 0.0 else distance(previous, sample)
        val seconds = if (previous == null) 0.0 else
            (sample.timeMillis - previous.timeMillis) / 1000.0
        if (previous != null && seconds <= 0.0) return false
        if (previous != null && gap / seconds > 60.0) return false

        if (first == null) first = sample
        if (previous != null && gap >= 3.0) distanceMeters += gap
        last = sample
        gpsAccuracy.add(sample.accuracyMeters.toDouble())
        val reportedSpeed = sample.speedMps?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }
        speeds += min(60.0, reportedSpeed ?: if (previous == null) 0.0 else gap / seconds)
        return true
    }

    fun toFeatures(durationMillis: Long): SensorFeatures {
        val sorted = speeds.sorted()
        return SensorFeatures(
            averageSpeedMps = if (durationMillis > 0) distanceMeters * 1000.0 / durationMillis else 0.0,
            p95SpeedMps = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 0.95).toInt()],
            maxSpeedMps = sorted.lastOrNull() ?: 0.0,
            stopRatio = if (speeds.isEmpty()) 0.0 else speeds.count { it < 0.5 }.toDouble() / speeds.size,
            averageGpsAccuracyMeters = gpsAccuracy.mean(),
            gpsSampleCount = gpsCount,
            accelMagnitudeMean = accelDynamic.mean(),
            accelMagnitudeStd = accelMagnitude.std(),
            accelSampleCount = accelCount.toInt(),
            gyroMagnitudeMean = gyroMagnitude.mean(),
            gyroMagnitudeStd = gyroMagnitude.std(),
            gyroSampleCount = gyroCount.toInt(),
        )
    }

    private fun distance(a: LocationSample, b: LocationSample): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2) * sin(lat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
            sin(lon / 2) * sin(lon / 2)
        return 12_742_000.0 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
}
