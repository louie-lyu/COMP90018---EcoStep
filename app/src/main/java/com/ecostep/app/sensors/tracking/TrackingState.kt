package com.ecostep.app.sensors.tracking

data class TrackingState(
    val isRecording: Boolean = false,
    val startTimeMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val lastAccuracyMeters: Float? = null,
    val gpsCount: Int = 0,
    val accelCount: Long = 0,
    val gyroCount: Long = 0,
    val sensorError: String? = null,
)

data class RecordingResult(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val first: LocationSample,
    val last: LocationSample,
    val distanceMeters: Double,
    val features: com.ecostep.app.data.model.SensorFeatures,
)
