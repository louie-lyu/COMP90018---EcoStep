package com.ecostep.app.sensors.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Synchronized because location and motion callbacks arrive on different threads. */
class JourneyTracker {
    private val mutableState = MutableStateFlow(TrackingState())
    val state = mutableState.asStateFlow()
    private var accumulator = FeatureAccumulator()
    private var lastPublishMillis = 0L

    @Synchronized fun begin(now: Long): Boolean {
        if (mutableState.value.isRecording) return false
        accumulator = FeatureAccumulator()
        lastPublishMillis = now
        mutableState.value = TrackingState(isRecording = true, startTimeMillis = now)
        return true
    }

    @Synchronized fun onLocation(sample: LocationSample) {
        if (!mutableState.value.isRecording) return
        if (accumulator.onLocation(sample)) publish(System.currentTimeMillis())
    }

    @Synchronized fun onMotion(sample: MotionSample) {
        if (!mutableState.value.isRecording) return
        accumulator.onMotion(sample)
        publish(System.currentTimeMillis())
    }

    @Synchronized fun tick(now: Long) {
        if (mutableState.value.isRecording) publish(now, force = true)
    }

    @Synchronized fun fail(message: String) {
        mutableState.value = mutableState.value.copy(sensorError = message)
    }

    @Synchronized fun abort(message: String) {
        accumulator = FeatureAccumulator()
        mutableState.value = TrackingState(sensorError = message)
    }

    @Synchronized fun finish(now: Long): RecordingResult? {
        val start = mutableState.value.startTimeMillis
        if (!mutableState.value.isRecording) return null
        val result = if (now - start >= 30_000 && accumulator.gpsCount >= 2) {
            RecordingResult(
                start, now, accumulator.first!!, accumulator.last!!,
                accumulator.distanceMeters, accumulator.toFeatures(now - start),
            )
        } else null
        mutableState.value = TrackingState()
        accumulator = FeatureAccumulator()
        return result
    }

    @Synchronized fun discard() {
        mutableState.value = TrackingState()
        accumulator = FeatureAccumulator()
    }

    private fun publish(now: Long, force: Boolean = false) {
        if (!force && now - lastPublishMillis < 1000) return
        lastPublishMillis = now
        val old = mutableState.value
        mutableState.value = old.copy(
            elapsedSeconds = ((now - old.startTimeMillis) / 1000).coerceAtLeast(0),
            distanceMeters = accumulator.distanceMeters,
            lastAccuracyMeters = accumulator.last?.accuracyMeters,
            gpsCount = accumulator.gpsCount,
            accelCount = accumulator.accelCount,
            gyroCount = accumulator.gyroCount,
        )
    }
}
