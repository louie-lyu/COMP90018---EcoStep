package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.JourneyRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val ecoPoints: Int = 0,
    val carbonSavedKg: Double = 0.0,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

class JourneyReviewViewModel(
    private val journeyRepository: JourneyRepository,
    private val journeyId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        JourneyReviewUiState(),
    )

    val uiState: StateFlow<JourneyReviewUiState> =
        _uiState.asStateFlow()

    init {
        loadJourney()
    }

    private fun loadJourney() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                val journey = journeyRepository.getJourney(journeyId)

                if (journey == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Journey not found.",
                        )
                    }

                    return@launch
                }

                _uiState.value = journey.toUiState()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            exception.message ?: "Unable to load journey.",
                    )
                }
            }
        }
    }

    fun selectTransportMode(mode: TransportMode) {
        val journey = _uiState.value.journey ?: return

        _uiState.update {
            it.copy(
                selectedMode = mode,
                ecoPoints = calculateMockEcoPoints(
                    mode = mode,
                    distanceMeters = journey.distanceMeters,
                ),
                carbonSavedKg = calculateMockCarbonSaved(
                    mode = mode,
                    distanceMeters = journey.distanceMeters,
                ),
            )
        }
    }

    fun saveJourney() {
        val currentState = _uiState.value
        val journey = currentState.journey ?: return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    errorMessage = null,
                )
            }

            try {
                val updatedJourney = journey.copy(
                    transportMode = currentState.selectedMode,
                )

                journeyRepository.saveJourney(updatedJourney)

                _uiState.update {
                    it.copy(
                        journey = updatedJourney,
                        isSaving = false,
                        isSaved = true,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage =
                            exception.message ?: "Unable to save journey.",
                    )
                }
            }
        }
    }

    fun dismissConfirmation() {
        _uiState.update {
            it.copy(isSaved = false)
        }
    }
}

private fun JourneySummary.toUiState(): JourneyReviewUiState {
    return JourneyReviewUiState(
        journey = this,
        selectedMode = transportMode,
        detectedMode = transportMode,
        startLocationText = startLocation.toDisplayText(),
        endLocationText = endLocation.toDisplayText(),
        durationText = formatDuration(
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
        ),
        distanceText = formatDistance(distanceMeters),
        dateText = formatDate(endTimeMillis),
        ecoPoints = calculateMockEcoPoints(
            mode = transportMode,
            distanceMeters = distanceMeters,
        ),
        carbonSavedKg = calculateMockCarbonSaved(
            mode = transportMode,
            distanceMeters = distanceMeters,
        ),
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
    val durationMinutes =
        ((endTimeMillis - startTimeMillis) / 60_000L)
            .coerceAtLeast(0L)

    return "$durationMinutes min"
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

/**
 * Temporary UI mock until the Algorithm & AI module provides EcoPoints.
 */
private fun calculateMockEcoPoints(
    mode: TransportMode,
    distanceMeters: Double,
): Int {
    val distanceKm = distanceMeters / 1000.0

    val pointsPerKm = when (mode) {
        TransportMode.WALKING -> 50.0
        TransportMode.CYCLING -> 40.0
        TransportMode.PUBLIC_TRANSPORT -> 20.0
        TransportMode.CAR -> 0.0
        TransportMode.UNKNOWN -> 0.0
    }

    return (distanceKm * pointsPerKm).roundToInt()
}

/**
 * Temporary UI mock until CarbonResult is supplied by the Algorithm & AI module.
 */
private fun calculateMockCarbonSaved(
    mode: TransportMode,
    distanceMeters: Double,
): Double {
    val distanceKm = distanceMeters / 1000.0
    val carEmissionsKgPerKm = 0.192

    val selectedModeEmissionsKgPerKm = when (mode) {
        TransportMode.WALKING -> 0.0
        TransportMode.CYCLING -> 0.0
        TransportMode.PUBLIC_TRANSPORT -> 0.089
        TransportMode.CAR -> carEmissionsKgPerKm
        TransportMode.UNKNOWN -> carEmissionsKgPerKm
    }

    return (
            distanceKm *
                    (carEmissionsKgPerKm - selectedModeEmissionsKgPerKm)
            ).coerceAtLeast(0.0)
}