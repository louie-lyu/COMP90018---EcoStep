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
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

class JourneyHistoryViewModel(
    private val journeyRepository: JourneyRepository,
    private val userId: String,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(JourneyHistoryUiState())

    val uiState: StateFlow<JourneyHistoryUiState> =
        _uiState.asStateFlow()

    init {
        observeJourneyHistory()
    }

    private fun observeJourneyHistory() {
        viewModelScope.launch {
            journeyRepository
                .observeJourneyHistory(userId)
                .catch { exception ->
                    _uiState.value =
                        JourneyHistoryUiState(
                            isLoading = false,
                            errorMessage =
                                exception.message
                                    ?: "Unable to load journey history.",
                        )
                }
                .collect { journeys ->
                    _uiState.value =
                        JourneyHistoryUiState(
                            journeys =
                                journeys.sortedByDescending {
                                    it.endTimeMillis
                                },
                            isLoading = false,
                        )
                }
        }
    }
}