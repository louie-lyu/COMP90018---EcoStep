package com.ecostep.app.sensors.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.sensors.service.JourneyTrackingService
import com.ecostep.app.sensors.tracking.JourneySummaryBuilder
import com.ecostep.app.sensors.tracking.JourneyTracker
import com.ecostep.app.sensors.tracking.TrackingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class TrackingUiState(
    val isSaving: Boolean = false,
    val savedJourneyId: String? = null,
    val message: String? = null,
)

class TrackingViewModel(
    private val context: Context,
    private val tracker: JourneyTracker,
    private val authRepository: AuthRepository,
    private val journeyRepository: JourneyRepository,
) : ViewModel() {
    val tracking: StateFlow<TrackingState> = tracker.state
    private val mutableUi = MutableStateFlow(TrackingUiState())
    val ui: StateFlow<TrackingUiState> = mutableUi.asStateFlow()

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            mutableUi.update { it.copy(message = "Precise location is required to record a journey.") }
            return
        }
        if (authRepository.currentUserId == null) {
            mutableUi.update { it.copy(message = "Please sign in first.") }
            return
        }
        if (!tracker.begin(System.currentTimeMillis())) return
        mutableUi.value = TrackingUiState()
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, JourneyTrackingService::class.java)
                    .setAction(JourneyTrackingService.ACTION_START),
            )
        } catch (exception: Exception) {
            tracker.discard()
            mutableUi.update { it.copy(message = "Could not start recording: ${exception.message}") }
        }
    }

    fun stop() {
        if (!tracker.state.value.isRecording || mutableUi.value.isSaving) return
        context.startService(
            Intent(context, JourneyTrackingService::class.java)
                .setAction(JourneyTrackingService.ACTION_STOP),
        )
        val result = tracker.finish(System.currentTimeMillis())
        if (result == null) {
            mutableUi.update { it.copy(message = "Journey too short: record at least 30 seconds and 2 valid GPS points. It was not saved.") }
            return
        }
        val uid = authRepository.currentUserId
        if (uid == null) {
            mutableUi.update { it.copy(message = "Sign in again to save your journey.") }
            return
        }
        val summary = JourneySummaryBuilder().build(result, uid)
        viewModelScope.launch {
            mutableUi.update { it.copy(isSaving = true, message = null) }
            try {
                // Firestore queues a write in its local cache while offline. Its Task may keep
                // waiting for the server, so cap the wait before opening the review screen.
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(3_000) { journeyRepository.saveJourney(summary) }
                }
                mutableUi.value = TrackingUiState(savedJourneyId = summary.journeyId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUi.update {
                    it.copy(isSaving = false, message = "Could not save journey: ${exception.message}")
                }
            }
        }
    }

    fun showPermissionMessage(message: String) {
        mutableUi.update { it.copy(message = message) }
    }

    fun onNavigated() {
        mutableUi.update { it.copy(savedJourneyId = null) }
    }
}
