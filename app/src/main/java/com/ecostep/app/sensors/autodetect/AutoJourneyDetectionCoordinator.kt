package com.ecostep.app.sensors.autodetect

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.ecostep.app.sensors.tracking.RecordingStartResult
import com.google.android.gms.location.ActivityRecognition

/** Subscribes to Activity Recognition outside a recording. */
interface ActivityUpdates {
    /** Fine location (to record) and, on Android 10+, activity recognition are granted. */
    fun hasPermissions(): Boolean
    fun request()
    fun remove()
}

/**
 * Starts a recording when the user has clearly started moving.
 *
 * Scope of the first version: detection runs only while the app is open (in the foreground)
 * and the user enabled it, so no background-location permission is needed. Recording is
 * started through the same [startRecording] path as the Start button; it is never stopped or
 * saved automatically - the user ends or aborts it from the Home panel.
 */
class AutoJourneyDetectionCoordinator(
    private val activityUpdates: ActivityUpdates,
    private val isRecording: () -> Boolean,
    private val startRecording: () -> RecordingStartResult,
    private val trigger: MovementTrigger = MovementTrigger(),
    private val log: (String) -> Unit = {},
) {
    private var enabled = false
    private var inForeground = false
    private var listening = false

    /** Mirrors the signed-in user's preference; false after sign-out. */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        update()
    }

    @Synchronized
    fun setAppInForeground(inForeground: Boolean) {
        this.inForeground = inForeground
        update()
    }

    /** Re-checks permissions, e.g. after the user granted them. */
    @Synchronized
    fun refresh() = update()

    @Synchronized
    fun onActivity(movement: DetectedMovement, confidence: Int, atMillis: Long) {
        if (!listening) return
        if (isRecording()) {
            trigger.reset()
            return
        }
        if (trigger.onActivity(movement, confidence, atMillis)) {
            log("Automatic start: ${startRecording().javaClass.simpleName}")
        }
    }

    private fun update() {
        val shouldListen = enabled && inForeground && activityUpdates.hasPermissions()
        if (shouldListen && !listening) {
            activityUpdates.request()
            listening = true
        } else if (!shouldListen && listening) {
            activityUpdates.remove()
            listening = false
            trigger.reset()
        }
    }
}

/** Google Play Services implementation, delivering results to [AutoJourneyDetectionReceiver]. */
class PlayServicesActivityUpdates(context: Context) : ActivityUpdates {
    private val context = context.applicationContext

    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            this.context,
            REQUEST_CODE,
            Intent(this.context, AutoJourneyDetectionReceiver::class.java),
            // Activity Recognition fills in the result, so the intent must be mutable.
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
    }

    override fun hasPermissions(): Boolean {
        val location = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val activity = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            granted(Manifest.permission.ACTIVITY_RECOGNITION)
        return location && activity
    }

    @SuppressLint("MissingPermission") // Checked by hasPermissions() before every request.
    override fun request() {
        try {
            ActivityRecognition.getClient(context)
                .requestActivityUpdates(INTERVAL_MILLIS, pendingIntent)
                .addOnFailureListener { Log.w(TAG, "Automatic detection unavailable.") }
        } catch (_: SecurityException) {
            Log.w(TAG, "Automatic detection permission unavailable.")
        }
    }

    @SuppressLint("MissingPermission")
    override fun remove() {
        try {
            ActivityRecognition.getClient(context).removeActivityUpdates(pendingIntent)
        } catch (_: SecurityException) {
            Log.w(TAG, "Could not remove automatic detection updates.")
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "EcoStepAutoDetect"
        const val REQUEST_CODE = 2001
        const val INTERVAL_MILLIS = 15_000L
    }
}
