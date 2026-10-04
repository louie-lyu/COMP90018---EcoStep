package com.ecostep.app.sensors.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.algorithm.DefaultTransportClassifier
import com.ecostep.app.algorithm.TransportEvidenceProvider
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.data.repository.WriteOutcome
import com.ecostep.app.sensors.service.JourneyTrackingService
import com.ecostep.app.sensors.tracking.JourneySummaryBuilder
import com.ecostep.app.sensors.tracking.JourneyTracker
import com.ecostep.app.sensors.tracking.LocationSample
import com.ecostep.app.sensors.tracking.RecordedJourneySaver
import com.ecostep.app.sensors.tracking.RecordingStartResult
import com.ecostep.app.sensors.tracking.RecordingStarter
import com.ecostep.app.sensors.tracking.RecordingResult
import com.ecostep.app.sensors.tracking.TrackingState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class TrackingUiState(
    val isSaving: Boolean = false,
    val savedJourneyId: String? = null,
    /** Saved to the local Firestore cache; syncs once the network returns. */
    val isPendingSync: Boolean = false,
    val message: String? = null,
)

class TrackingViewModel(
    private val context: Context,
    private val tracker: JourneyTracker,
    private val authRepository: AuthRepository,
    private val journeyRepository: JourneyRepository,
    private val transportEvidenceProviderFactory: (RecordingResult) -> TransportEvidenceProvider,
    /** Mission that is active while recording, linked to the saved journey. */
    private val activeMissionIdProvider: () -> String? = { null },
    /** Live device locations for the map; requires fine location permission. */
    private val locationUpdates: (() -> Flow<LocationSample>)? = null,
) : ViewModel() {
    private val journeySaver = RecordedJourneySaver(journeyRepository, activeMissionIdProvider)
    private val recordingStarter = RecordingStarter(context, tracker, authRepository)
    val tracking: StateFlow<TrackingState> = tracker.state
    private val mutableUi = MutableStateFlow(TrackingUiState())
    val ui: StateFlow<TrackingUiState> = mutableUi.asStateFlow()

    fun start() {
        when (val result = recordingStarter.start()) {
            RecordingStartResult.Started -> mutableUi.value = TrackingUiState()
            RecordingStartResult.MissingLocationPermission ->
                mutableUi.update { it.copy(message = "Precise location is required to record a journey.") }
            RecordingStartResult.NotSignedIn ->
                mutableUi.update { it.copy(message = "Please sign in first.") }
            RecordingStartResult.AlreadyRecording -> Unit
            is RecordingStartResult.Failed ->
                mutableUi.update { it.copy(message = "Could not start recording: ${result.message}") }
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
        val unclassifiedSummary = JourneySummaryBuilder().build(result, uid)
        viewModelScope.launch {
            mutableUi.update { it.copy(isSaving = true, message = null) }
            try {
                val summary = withContext(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(5_000) {
                            val evidenceProvider = transportEvidenceProviderFactory(result)
                            val mode = DefaultTransportClassifier(evidenceProvider)
                                .classify(unclassifiedSummary)
                                .mode
                            unclassifiedSummary.copy(transportMode = mode)
                        } ?: unclassifiedSummary
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        unclassifiedSummary
                    }
                }
                // Firestore writes to its local cache first; QUEUED means the review screen can
                // already read the journey while the upload waits for the network.
                val saved = withContext(Dispatchers.IO) {
                    journeySaver.save(summary)
                }
                mutableUi.value = TrackingUiState(
                    savedJourneyId = saved.journeyId,
                    isPendingSync = saved.outcome == WriteOutcome.QUEUED,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                mutableUi.update {
                    it.copy(isSaving = false, message = "Could not save journey: ${exception.message}")
                }
            }
        }
    }

    private val mutableCurrentLocation = MutableStateFlow<GeoPoint?>(null)

    /** Latest device position, independent of recording; null until the first fix. */
    val currentLocation: StateFlow<GeoPoint?> = mutableCurrentLocation.asStateFlow()

    private var locationJob: Job? = null

    /** Starts following the device location for the map. Safe to call repeatedly. */
    fun startLocationUpdates() {
        val updates = locationUpdates ?: return
        if (locationJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        locationJob = viewModelScope.launch {
            updates()
                .catch { /* Location unavailable: the map keeps its last or default position. */ }
                .collect { sample ->
                    mutableCurrentLocation.value = GeoPoint(sample.latitude, sample.longitude)
                }
        }
    }

    /** Stops recording without saving anything (the user aborted the journey). */
    fun discard() {
        if (mutableUi.value.isSaving) return
        if (tracker.state.value.isRecording) {
            context.startService(
                Intent(context, JourneyTrackingService::class.java)
                    .setAction(JourneyTrackingService.ACTION_STOP),
            )
            tracker.discard()
        }
        mutableUi.value = TrackingUiState()
    }

    fun clearMessage() {
        mutableUi.update { it.copy(message = null) }
    }

    fun showPermissionMessage(message: String) {
        mutableUi.update { it.copy(message = message) }
    }

    fun onNavigated() {
        mutableUi.update { it.copy(savedJourneyId = null) }
    }
}
