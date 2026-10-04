package com.ecostep.app.sensors.tracking

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.sensors.service.JourneyTrackingService

sealed interface RecordingStartResult {
    data object Started : RecordingStartResult
    data object MissingLocationPermission : RecordingStartResult
    data object NotSignedIn : RecordingStartResult
    data object AlreadyRecording : RecordingStartResult
    data class Failed(val message: String?) : RecordingStartResult
}

/**
 * The single way to begin a recording, shared by the Start button and automatic detection
 * so both check the same preconditions and use the same foreground service.
 */
class RecordingStarter(
    private val context: Context,
    private val tracker: JourneyTracker,
    private val authRepository: AuthRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun start(): RecordingStartResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return RecordingStartResult.MissingLocationPermission
        }
        if (authRepository.currentUserId == null) return RecordingStartResult.NotSignedIn
        if (!tracker.begin(clock())) return RecordingStartResult.AlreadyRecording
        return try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JourneyTrackingService::class.java)
                    .setAction(JourneyTrackingService.ACTION_START),
            )
            RecordingStartResult.Started
        } catch (exception: Exception) {
            tracker.discard()
            RecordingStartResult.Failed(exception.message)
        }
    }
}
