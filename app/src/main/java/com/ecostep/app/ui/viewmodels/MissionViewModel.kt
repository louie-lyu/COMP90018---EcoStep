package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.core.integration.MissionRouteEstimate
import com.ecostep.app.core.integration.MissionRouteException
import com.ecostep.app.ui.adapters.withRouteEstimate
import com.ecostep.app.ui.mock.MissionJourneyRecorder
import com.ecostep.app.ui.mock.MissionRepository
import com.ecostep.app.ui.mock.MissionRepositoryState
import com.ecostep.app.ui.mock.MissionPageItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Recalculated impact for the route being edited. */
sealed interface RouteEstimateState {
    data object Idle : RouteEstimateState
    data class Loading(val startText: String, val destinationText: String) : RouteEstimateState
    data class Ready(val estimate: MissionRouteEstimate) : RouteEstimateState
    data class Failed(val startText: String, val destinationText: String, val message: String) :
        RouteEstimateState
}

data class MissionCreationState(
    val isCreating: Boolean = false,
    val createError: String? = null,
    val createdMissionId: String? = null,
)

class MissionViewModel(
    private val missionRepository: MissionRepository,
    /**
     * Preview/mock only: fabricates a journey when a mission ends. Production passes null;
     * real journeys come from tracking and complete the mission on review.
     */
    private val missionJourneyRecorder: MissionJourneyRecorder? = null,
    /** Recalculates CO₂ and EcoPoints for an edited route; null keeps the stored values. */
    private val routeEstimator: (suspend (start: String, destination: String) -> MissionRouteEstimate)? = null,
) : ViewModel() {

    val uiState: StateFlow<MissionRepositoryState> =
        missionRepository.state

    private val _journeyReviewEvents =
        MutableSharedFlow<String>()

    val journeyReviewEvents: SharedFlow<String> =
        _journeyReviewEvents.asSharedFlow()

    private val _routeEstimate = MutableStateFlow<RouteEstimateState>(RouteEstimateState.Idle)
    val routeEstimate: StateFlow<RouteEstimateState> = _routeEstimate.asStateFlow()

    private var estimateJob: Job? = null
    private val _creationState = MutableStateFlow(MissionCreationState())
    val creationState: StateFlow<MissionCreationState> = _creationState.asStateFlow()

    fun resetCreationState() {
        if (!_creationState.value.isCreating) _creationState.value = MissionCreationState()
    }

    fun acceptSuggestedMission() {
        missionRepository.acceptSuggestedMission()
    }

    fun dismissSuggestedMission() {
        missionRepository.dismissSuggestedMission()
    }

    fun startMission(missionId: String) {
        missionRepository.startMission(missionId)
    }

    fun skipMissionToday(missionId: String) {
        missionRepository.skipMissionToday(missionId)
    }

    /** Recalculates the estimates for a start and destination typed in the editor. */
    fun estimateRoute(start: String, destination: String) {
        val estimator = routeEstimator
        val startText = start.trim()
        val destinationText = destination.trim()
        if (estimator == null || startText.isEmpty() || destinationText.isEmpty()) {
            clearRouteEstimate()
            return
        }
        when (val current = _routeEstimate.value) {
            is RouteEstimateState.Ready -> if (current.estimate.matches(startText, destinationText)) return
            is RouteEstimateState.Loading ->
                if (current.startText == startText && current.destinationText == destinationText) return
            else -> Unit
        }

        estimateJob?.cancel()
        _routeEstimate.value = RouteEstimateState.Loading(startText, destinationText)
        estimateJob = viewModelScope.launch {
            _routeEstimate.value = try {
                RouteEstimateState.Ready(estimator(startText, destinationText))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: MissionRouteException) {
                RouteEstimateState.Failed(startText, destinationText, exception.message.orEmpty())
            } catch (_: Exception) {
                RouteEstimateState.Failed(
                    startText,
                    destinationText,
                    "Could not calculate this route right now. Saved values are kept.",
                )
            }
        }
    }

    fun clearRouteEstimate() {
        estimateJob?.cancel()
        _routeEstimate.value = RouteEstimateState.Idle
    }

    /**
     * Saves an edit. When the start or destination changed, the estimates are recalculated
     * for the new route first; if that fails, the previous estimates are kept.
     */
    fun updateMission(updatedMission: MissionPageItem) {
        saveWithRouteEstimate(updatedMission, routeChanged(updatedMission), missionRepository::updateMission)
    }

    fun createMission(mission: MissionPageItem) {
        if (_creationState.value.isCreating) return
        _creationState.value = MissionCreationState(isCreating = true)
        viewModelScope.launch {
            try {
                missionRepository.createMission(withCurrentRouteEstimate(mission, true))
                _creationState.value = MissionCreationState(createdMissionId = mission.mission.missionId)
            } catch (exception: CancellationException) {
                _creationState.value = MissionCreationState()
                throw exception
            } catch (exception: Exception) {
                _creationState.value = MissionCreationState(
                    createError = exception.message ?: "Unable to create mission. Please retry.",
                )
            }
        }
    }

    private fun saveWithRouteEstimate(
        updatedMission: MissionPageItem,
        needsEstimate: Boolean,
        save: (MissionPageItem) -> Unit,
    ) {
        viewModelScope.launch { save(withCurrentRouteEstimate(updatedMission, needsEstimate)) }
    }

    private suspend fun withCurrentRouteEstimate(
        updatedMission: MissionPageItem,
        needsEstimate: Boolean,
    ): MissionPageItem {
        val estimator = routeEstimator
        if (estimator == null || !needsEstimate) {
            return updatedMission
        }

        val ready = (_routeEstimate.value as? RouteEstimateState.Ready)?.estimate
            ?.takeIf { it.matches(updatedMission.startLocation, updatedMission.destination) }
        clearRouteEstimate()
        if (ready != null) {
            return updatedMission.withRouteEstimate(ready)
        }

        val estimate = try {
            estimator(updatedMission.startLocation.trim(), updatedMission.destination.trim())
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
        return estimate?.let(updatedMission::withRouteEstimate) ?: updatedMission
    }

    private fun routeChanged(updated: MissionPageItem): Boolean {
        val state = missionRepository.state.value
        val original = (listOfNotNull(state.activeMission, state.suggestedMission) + state.upcomingMissions)
            .firstOrNull { it.mission.missionId == updated.mission.missionId }
            ?: return true
        return original.startLocation.trim() != updated.startLocation.trim() ||
            original.destination.trim() != updated.destination.trim()
    }

    fun endActiveMission() {
        val activeMission =
            missionRepository.state.value.activeMission
                ?: return

        val recorder = missionJourneyRecorder
        if (recorder == null) {
            missionRepository.endActiveMission()
            return
        }

        viewModelScope.launch {
            val journeyId =
                recorder.createPartialJourney(
                    mission = activeMission,
                )

            missionRepository.endActiveMission()

            _journeyReviewEvents.emit(journeyId)
        }
    }
}
