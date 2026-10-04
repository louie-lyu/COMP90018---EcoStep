package com.ecostep.app.sensors.autodetect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecostep.app.EcoStepApp
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Receives the low-frequency activity updates requested by [AutoJourneyDetectionCoordinator]
 * outside a recording. Separate from the in-recording ActivityRecognitionReceiver, which only
 * feeds classification hints.
 */
class AutoJourneyDetectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val activity = result.mostProbableActivity
        val movement = when (activity.type) {
            DetectedActivity.WALKING, DetectedActivity.ON_FOOT, DetectedActivity.RUNNING ->
                DetectedMovement.WALKING
            DetectedActivity.ON_BICYCLE -> DetectedMovement.CYCLING
            DetectedActivity.IN_VEHICLE -> DetectedMovement.IN_VEHICLE
            else -> DetectedMovement.STILL_OR_OTHER
        }
        val app = context.applicationContext as? EcoStepApp ?: return
        app.appContainer.autoJourneyDetection.onActivity(movement, activity.confidence, result.time)
    }
}
