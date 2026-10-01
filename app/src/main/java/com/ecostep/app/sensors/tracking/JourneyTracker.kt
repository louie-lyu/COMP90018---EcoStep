package com.ecostep.app.sensors.tracking
import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.MotionHint

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Synchronized because location and motion callbacks arrive on different threads. */
class JourneyTracker {
    private val mutableState = MutableStateFlow(TrackingState())
    val state = mutableState.asStateFlow()
    private var accumulator = FeatureAccumulator()
    private val activityVotes = mutableMapOf<MotionHint, Int>()
    private var lastPublishMillis = 0L

    @Synchronized fun begin(now: Long): Boolean {
        if (mutableState.value.isRecording) return false
        accumulator = FeatureAccumulator()
        activityVotes.clear()
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
    @Synchronized fun onActivityHint(hint: ActivityHint) {
        if (!mutableState.value.isRecording) return
        if (hint.confidence !in 60..100 || hint.type == MotionHint.UNKNOWN) return
        activityVotes[hint.type] = (activityVotes[hint.type] ?: 0) + hint.confidence
    }

    @Synchronized fun tick(now: Long) {
        if (mutableState.value.isRecording) publish(now, force = true)
    }

    @Synchronized fun fail(message: String) {
        mutableState.value = mutableState.value.copy(sensorError = message)
    }

    @Synchronized fun abort(message: String) {
        accumulator = FeatureAccumulator()
        activityVotes.clear()
        mutableState.value = TrackingState(sensorError = message)
    }

    @Synchronized fun finish(now: Long): RecordingResult? {
        val start = mutableState.value.startTimeMillis
        if (!mutableState.value.isRecording) return null
        val result = if (now - start >= 30_000 && accumulator.gpsCount >= 2) {
            RecordingResult(
                start, now, accumulator.first!!, accumulator.last!!,
                accumulator.distanceMeters, accumulator.toFeatures(now - start),
                trace = accumulator.snapshotTrace(),
                activityHint = dominantActivityHint(),
            )
        } else null
        mutableState.value = TrackingState()
        accumulator = FeatureAccumulator()
        activityVotes.clear()
        return result
    }

    @Synchronized fun discard() {
        mutableState.value = TrackingState()
        accumulator = FeatureAccumulator()
        activityVotes.clear()
    }

    private fun dominantActivityHint(): ActivityHint? {
        val winner = activityVotes.maxByOrNull { it.value } ?: return null
        val total = activityVotes.values.sum()
        return ActivityHint(winner.key, winner.value * 100 / total)
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
