package com.ecostep.app.sensors.motion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ecostep.app.EcoStepApp
import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.MotionHint
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

class ActivityRecognitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return

        val hint = result.probableActivities.mapNotNull { detected ->
            val type = when (detected.type) {
                DetectedActivity.WALKING -> MotionHint.WALKING
                DetectedActivity.ON_BICYCLE -> MotionHint.CYCLING
                DetectedActivity.IN_VEHICLE -> MotionHint.IN_VEHICLE
                else -> null
            }
            if (type == null || detected.confidence < 60) {
                null
            } else {
                ActivityHint(type, detected.confidence)
            }
        }.maxByOrNull { it.confidence } ?: return

        val app = context.applicationContext as? EcoStepApp ?: return
        app.appContainer.journeyTracker.onActivityHint(hint)
    }
}