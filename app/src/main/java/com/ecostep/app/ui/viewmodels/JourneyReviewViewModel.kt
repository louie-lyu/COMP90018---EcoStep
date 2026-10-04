package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.algorithm.CarbonCalculator
import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.algorithm.EcoPointsCalculator
import com.ecostep.app.algorithm.EmissionFactors
import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.data.repository.JourneySnapshot
import com.ecostep.app.data.repository.WriteOutcome
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JourneyReviewUiState(
    val journey: JourneySummary? = null,
    val selectedMode: TransportMode = TransportMode.UNKNOWN,
    val detectedMode: TransportMode = TransportMode.UNKNOWN,
    val startLocationText: String = "",
    val endLocationText: String = "",
    val durationText: String = "",
    val distanceText: String = "",
    val dateText: String = "",
    /** Backend-awarded value once available, otherwise a client-side estimate. */
    val ecoPoints: Int = 0,
    val carbonSavedKg: Double = 0.0,
    /** True when [ecoPoints]/[carbonSavedKg] are the backend's trusted values. */
    val isImpactVerified: Boolean = false,
    /** Estimated emissions of the selected mode; not the same as [carbonSavedKg]. */
    val emissionsGrams: Double = 0.0,
    /** Estimated lower-carbon modes for this trip, largest saving first. */
    val lowerCarbonAlternatives: List<CarbonAlternative> = emptyList(),
    val isConfirmed: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** Local changes are cached and waiting for the network. */
    val isPendingSync: Boolean = false,
    /** The server has the latest version of this journey. */
    val isSynced: Boolean = false,
    /** Load failure; replaces the screen content. */
    val errorMessage: String? = null,
    /** Save failure; shown next to the confirm button so the user can retry. */
    val saveErrorMessage: String? = null,
)

class JourneyReviewViewModel(
    private val journeyRepository: JourneyRepository,
    private val journeyId: String,
    /** Optional: turns coordinates into place names; coordinates are shown until it returns. */
    private val placeNameResolver: (suspend (GeoPoint) -> String?)? = null,
    private val ecoPointsCalculator: EcoPointsCalculator = DefaultEcoPointsCalculator(),
    private val carbonCalculator: CarbonCalculator = DefaultCarbonCalculator(),
    /** Called after a confirmation is stored, e.g. to complete the linked mission occurrence. */
    private val onJourneyConfirmed: suspend (JourneySummary) -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JourneyReviewUiState(),
    )

    val uiState: StateFlow<JourneyReviewUiState> =
        _uiState.asStateFlow()

    private var userSelectedMode = false
    private var placeNamesRequested = false

    init {
        observeJourney()
    }

    private fun observeJourney() {
        viewModelScope.launch {
            journeyRepository.observeJourney(journeyId)
                .catch { exception ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "Unable to load journey.",
                        )
                    }
                }
                .collect { snapshot ->
                    if (snapshot == null) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Journey not found.")
                        }
                    } else {
                        onSnapshot(snapshot)
                    }
                }
        }
    }

    private fun onSnapshot(snapshot: JourneySnapshot) {
        val journey = snapshot.journey
        _uiState.update { current ->
            val selectedMode =
                if (userSelectedMode) current.selectedMode else journey.transportMode
            val base = if (current.journey == null) journey.toUiState() else current
            base.copy(
                journey = journey,
                selectedMode = selectedMode,
                detectedMode = journey.detectedTransportMode ?: journey.transportMode,
                isConfirmed = journey.confirmationStatus == JourneyConfirmationStatus.CONFIRMED,
                isPendingSync = snapshot.hasPendingWrites,
                isSynced = !snapshot.hasPendingWrites,
                isLoading = false,
                errorMessage = null,
            ).withImpact(journey, selectedMode)
        }
        if (!placeNamesRequested) {
            placeNamesRequested = true
            resolvePlaceNames(journey)
        }
    }

    private fun resolvePlaceNames(journey: JourneySummary) {
        val resolver = placeNameResolver ?: return
        viewModelScope.launch {
            resolver(journey.startLocation)?.let { name ->
                _uiState.update { it.copy(startLocationText = name) }
            }
        }
        viewModelScope.launch {
            resolver(journey.endLocation)?.let { name ->
                _uiState.update { it.copy(endLocationText = name) }
            }
        }
    }

    fun selectTransportMode(mode: TransportMode) {
        val journey = _uiState.value.journey ?: return
        userSelectedMode = true

        _uiState.update {
            it.copy(
                selectedMode = mode,
                saveErrorMessage = null,
            ).withImpact(journey, mode)
        }
    }

    fun saveJourney() {
        val currentState = _uiState.value
        val journey = currentState.journey ?: return
        if (currentState.isSaving) return
        val mode = currentState.selectedMode
        if (mode == TransportMode.UNKNOWN) {
            _uiState.update {
                it.copy(saveErrorMessage = "Choose how you travelled before confirming.")
            }
            return
        }

        _uiState.update {
            it.copy(
                isSaving = true,
                saveErrorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val outcome = journeyRepository.confirmTransportMode(journeyId, mode)
                userSelectedMode = false
                val confirmedJourney = journey.copy(
                    transportMode = mode,
                    confirmedTransportMode = mode,
                    confirmationStatus = JourneyConfirmationStatus.CONFIRMED,
                )
                _uiState.update {
                    it.copy(
                        isConfirmed = true,
                        isSaving = false,
                        isSaved = true,
                        isPendingSync = outcome == WriteOutcome.QUEUED || it.isPendingSync,
                        isSynced = outcome == WriteOutcome.SYNCED && !it.isPendingSync,
                    )
                }
                notifyConfirmed(confirmedJourney)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveErrorMessage =
                            exception.message ?: "Unable to save journey.",
                    )
                }
            }
        }
    }

    private suspend fun notifyConfirmed(journey: JourneySummary) {
        try {
            onJourneyConfirmed(journey)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The journey itself is saved; confirming again retries the follow-up idempotently.
            _uiState.update {
                it.copy(saveErrorMessage = "Journey saved, but the mission could not be updated.")
            }
        }
    }

    fun dismissConfirmation() {
        _uiState.update {
            it.copy(isSaved = false)
        }
    }

    /**
     * Trusted backend values are shown once the server has processed the confirmed mode;
     * until then the same shared formulas give an estimate.
     */
    private fun JourneyReviewUiState.withImpact(
        journey: JourneySummary,
        mode: TransportMode,
    ): JourneyReviewUiState =
        withSavings(journey, mode).withAlternatives(journey, mode)

    /** Emissions and lower-carbon options for the mode the user currently has selected. */
    private fun JourneyReviewUiState.withAlternatives(
        journey: JourneySummary,
        mode: TransportMode,
    ): JourneyReviewUiState {
        val result = carbonCalculator.calculate(journey.copy(transportMode = mode))
        return copy(
            emissionsGrams = result.emissionsGrams,
            lowerCarbonAlternatives = result.lowerCarbonAlternatives
                .filter { it.savingsGrams > 0.0 }
                .sortedByDescending { it.savingsGrams },
        )
    }

    private fun JourneyReviewUiState.withSavings(
        journey: JourneySummary,
        mode: TransportMode,
    ): JourneyReviewUiState {
        val verifiedCarbon = journey.carbonSavedGrams
        if (
            verifiedCarbon != null &&
            journey.confirmationStatus == JourneyConfirmationStatus.CONFIRMED &&
            journey.transportMode == mode
        ) {
            return copy(
                carbonSavedKg = verifiedCarbon / 1000.0,
                ecoPoints = journey.ecoPoints ?: estimatePoints(journey, mode, verifiedCarbon),
                isImpactVerified = journey.ecoPoints != null,
            )
        }
        val carbonGrams = EmissionFactors.carbonSavedVersusCarGrams(journey.distanceMeters, mode)
        return copy(
            carbonSavedKg = carbonGrams / 1000.0,
            ecoPoints = estimatePoints(journey, mode, carbonGrams),
            isImpactVerified = false,
        )
    }

    /** EcoPoints are awarded for completed missions only, so unlinked journeys earn none. */
    private fun estimatePoints(
        journey: JourneySummary,
        mode: TransportMode,
        carbonGrams: Double,
    ): Int {
        val missionId = journey.linkedMissionId ?: return 0
        return ecoPointsCalculator.calculatePoints(
            MissionResult(
                missionId = missionId,
                accepted = true,
                completed = true,
                actualTransportMode = mode,
                actualCarbonSavingGrams = carbonGrams,
                timestampMillis = journey.endTimeMillis,
                actualDistanceMeters = journey.distanceMeters,
            ),
        )
    }
}

private fun JourneySummary.toUiState(): JourneyReviewUiState {
    return JourneyReviewUiState(
        journey = this,
        selectedMode = transportMode,
        detectedMode = detectedTransportMode ?: transportMode,
        startLocationText = startLocation.toDisplayText(),
        endLocationText = endLocation.toDisplayText(),
        durationText = formatDuration(
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
        ),
        distanceText = formatDistance(distanceMeters),
        dateText = formatDate(endTimeMillis),
        isLoading = false,
    )
}

private fun GeoPoint.toDisplayText(): String {
    return String.format(
        Locale.getDefault(),
        "%.4f, %.4f",
        latitude,
        longitude,
    )
}

private fun formatDuration(
    startTimeMillis: Long,
    endTimeMillis: Long,
): String {
    val totalSeconds =
        ((endTimeMillis - startTimeMillis) / 1_000L)
            .coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

private fun formatDistance(distanceMeters: Double): String {
    return if (distanceMeters >= 1000.0) {
        String.format(
            Locale.getDefault(),
            "%.1f km",
            distanceMeters / 1000.0,
        )
    } else {
        "${distanceMeters.roundToInt()} m"
    }
}

private fun formatDate(timeMillis: Long): String {
    val formatter = SimpleDateFormat(
        "d MMM",
        Locale.getDefault(),
    )

    return formatter.format(Date(timeMillis))
}
