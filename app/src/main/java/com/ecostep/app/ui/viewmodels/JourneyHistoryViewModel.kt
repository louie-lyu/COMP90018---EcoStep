package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.repository.JourneyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class JourneyHistoryUiState(
    val journeys: List<JourneySummary> = emptyList(),
    /** Journeys whose latest local changes have not reached the server yet. */
    val pendingSyncJourneyIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val requiresSignIn: Boolean = false,
    val errorMessage: String? = null,
)

class JourneyHistoryViewModel(
    private val journeyRepository: JourneyRepository,
    /** Signed-in user's UID from the auth repository; null when signed out. */
    private val currentUserId: String?,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(JourneyHistoryUiState())

    val uiState: StateFlow<JourneyHistoryUiState> =
        _uiState.asStateFlow()

    init {
        observeJourneyHistory()
    }

    private fun observeJourneyHistory() {
        val userId = currentUserId
        if (userId == null) {
            _uiState.value = JourneyHistoryUiState(
                isLoading = false,
                requiresSignIn = true,
                errorMessage = "Sign in to see your journey history.",
            )
            return
        }

        viewModelScope.launch {
            journeyRepository
                .observeJourneyHistorySnapshots(userId)
                .catch { exception ->
                    _uiState.value =
                        JourneyHistoryUiState(
                            isLoading = false,
                            errorMessage =
                                exception.message
                                    ?: "Unable to load journey history.",
                        )
                }
                .collect { snapshots ->
                    _uiState.value =
                        JourneyHistoryUiState(
                            journeys =
                                snapshots
                                    .map { it.journey }
                                    .sortedByDescending { it.endTimeMillis },
                            pendingSyncJourneyIds =
                                snapshots
                                    .filter { it.hasPendingWrites }
                                    .mapTo(mutableSetOf()) { it.journey.journeyId },
                            isLoading = false,
                        )
                }
        }
    }
}
