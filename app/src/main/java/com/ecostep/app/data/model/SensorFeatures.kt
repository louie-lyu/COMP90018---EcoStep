package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Aggregated sensor features for one journey.
 *
 * Only derived statistics are persisted; raw GPS and motion samples remain on-device and are
 * discarded after the journey has been summarized.
 */
@Serializable
data class SensorFeatures(
    /** Feature extraction algorithm version. */
    val featureVersion: Int = 1,

    // GPS
    val averageSpeedMps: Double,
    val p95SpeedMps: Double,
    val maxSpeedMps: Double,
    val stopRatio: Double,
    val averageGpsAccuracyMeters: Double,
    val gpsSampleCount: Int,

    // Accelerometer
    val accelMagnitudeMean: Double,
    val accelMagnitudeStd: Double,
    val accelSampleCount: Int,

    // Gyroscope
    val gyroMagnitudeMean: Double,
    val gyroMagnitudeStd: Double,
    val gyroSampleCount: Int,
)
