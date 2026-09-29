package com.ecostep.app.sensors.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ecostep.app.EcoStepApp
import com.ecostep.app.MainActivity
import com.ecostep.app.sensors.location.LocationTracker
import com.ecostep.app.sensors.motion.MotionSensorTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class JourneyTrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracker get() = (application as EcoStepApp).appContainer.journeyTracker
    private var samplingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (samplingJob != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            tracker.abort("Precise location permission is required.")
            stopSelf()
            return
        }
        try {
            createChannel()
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (exception: SecurityException) {
            tracker.abort("Location permission is unavailable. Enable it in Settings.")
            stopSelf()
            return
        }
        val motion = MotionSensorTracker(this)
        if (!motion.hasAccelerometer) tracker.fail("This device has no accelerometer.")
        else if (!motion.hasGyroscope) tracker.fail("This device has no gyroscope; recording without it.")
        samplingJob = scope.launch {
            launch {
                try {
                    LocationTracker(this@JourneyTrackingService).locations().collect(tracker::onLocation)
                } catch (exception: Exception) {
                    if (exception !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "GPS collection failed", exception)
                        tracker.abort("GPS collection failed. Check location settings.")
                        stop()
                    }
                }
            }
            launch {
                motion.samples().collect(tracker::onMotion)
            }
            launch {
                while (true) {
                    delay(1_000)
                    tracker.tick(System.currentTimeMillis())
                    val state = tracker.state.value
                    if (state.isRecording && state.elapsedSeconds % 10L == 0L) {
                        Log.i(TAG, "gps=${state.gpsCount} accel=${state.accelCount} gyro=${state.gyroCount}")
                    }
                }
            }
        }
    }

    private fun stop() {
        samplingJob?.cancel()
        samplingJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Journey tracking", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("EcoStep is recording your journey")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.ecostep.app.tracking.START"
        const val ACTION_STOP = "com.ecostep.app.tracking.STOP"
        private const val CHANNEL_ID = "journey_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val TAG = "EcoStepTracking"
    }
}
