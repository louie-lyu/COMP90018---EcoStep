package com.ecostep.app.sensors.tracking
import com.ecostep.app.data.model.GeoPoint
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

/** Keeps summary statistics and a bounded in-memory GPS trace; raw motion samples are discarded. */
class FeatureAccumulator {
    private val accelMagnitude = RunningStats()
    private val accelDynamic = RunningStats()
    private val gyroMagnitude = RunningStats()
    private var gpsAccuracy = RunningStats()
    private val speeds = mutableListOf<Double>()
    private val recentTrace = mutableListOf<LocationSample>()

    fun snapshotTrace(): List<LocationSample> = recentTrace.toList()

    /** Retained trace plus the latest accepted fix, as map coordinates. */
    fun displayPath(): List<GeoPoint> {
        val points = recentTrace.mapTo(mutableListOf()) { GeoPoint(it.latitude, it.longitude) }
        last?.let { latest ->
            if (recentTrace.lastOrNull() !== latest) points += GeoPoint(latest.latitude, latest.longitude)
        }
        return points
    }

    var distanceMeters = 0.0
        private set
    var first: LocationSample? = null
        private set
    var last: LocationSample? = null
        private set
    private var distanceAnchor: LocationSample? = null
    private var relocationCandidate: LocationSample? = null
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

        var relocated = false
        if (previous != null && gap / seconds > MAX_PLAUSIBLE_SPEED_MPS) {
            // A jump. Comparing later fixes with the stale point would eventually look
            // plausible as the time gap grows and add the whole jump to the distance. Instead,
            // when the next fix agrees with the jumped-to position, continue from there
            // without counting the jump; a single outlier is simply dropped.
            val candidate = relocationCandidate
            if (candidate != null && isPlausibleStep(candidate, sample)) {
                relocated = true
            } else {
                relocationCandidate = sample
                return false
            }
        }
        relocationCandidate = null

        if (relocated && distanceMeters == 0.0) {
            // Nothing was travelled before the jump, so the earlier fixes were a wrong or stale
            // position (e.g. an emulator or provider still reporting an old place). The journey
            // starts here; keeping the old point would put the start in the wrong place and
            // draw the jump on the map.
            first = null
            recentTrace.clear()
            speeds.clear()
            gpsAccuracy = RunningStats()
        }
        if (first == null) first = sample
        // Measure from the last point that counted, not the previous fix: at walking speed
        // consecutive fixes (1-2 s apart) are under the jitter threshold, so comparing
        // neighbours alone would drop the whole journey.
        val anchor = distanceAnchor
        if (anchor == null || relocated) {
            distanceAnchor = sample
        } else {
            val moved = distance(anchor, sample)
            if (moved >= MIN_COUNTED_MOVE_METERS) {
                distanceMeters += moved
                distanceAnchor = sample
            }
        }
        last = sample
        // Keep one accepted GPS point about every five seconds, in memory only.
        if (
            recentTrace.isEmpty() ||
            sample.timeMillis - recentTrace.last().timeMillis >= 5_000L
        ) {
            recentTrace += sample
        }

        // Keep no more than the most recent hour or 720 points.
        while (
            recentTrace.isNotEmpty() &&
            sample.timeMillis - recentTrace.first().timeMillis > 3_600_000L
        ) {
            recentTrace.removeAt(0)
        }
        while (recentTrace.size > 720) {
            recentTrace.removeAt(0)
        }
        gpsAccuracy.add(sample.accuracyMeters.toDouble())
        val reportedSpeed = sample.speedMps?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }
        speeds += min(
            MAX_PLAUSIBLE_SPEED_MPS,
            reportedSpeed ?: if (previous == null || relocated) 0.0 else gap / seconds,
        )
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

    private fun isPlausibleStep(from: LocationSample, to: LocationSample): Boolean {
        val seconds = (to.timeMillis - from.timeMillis) / 1000.0
        return seconds > 0.0 && distance(from, to) / seconds <= MAX_PLAUSIBLE_SPEED_MPS
    }

    private companion object {
        /** Movement below this is treated as GPS jitter. */
        const val MIN_COUNTED_MOVE_METERS = 3.0

        /** Faster than any supported transport mode: treated as a GPS jump. */
        const val MAX_PLAUSIBLE_SPEED_MPS = 60.0
    }
}
