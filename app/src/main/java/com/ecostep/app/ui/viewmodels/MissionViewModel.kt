package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.ui.mock.MissionJourneyRecorder
import com.ecostep.app.ui.mock.MissionRepository
import com.ecostep.app.ui.mock.MissionRepositoryState
import com.ecostep.app.ui.mock.MissionPageItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class MissionViewModel(
    private val missionRepository: MissionRepository,
    private val missionJourneyRecorder: MissionJourneyRecorder,
) : ViewModel() {

    val uiState: StateFlow<MissionRepositoryState> =
        missionRepository.state

    private val _journeyReviewEvents =
        MutableSharedFlow<String>()

    val journeyReviewEvents: SharedFlow<String> =
        _journeyReviewEvents.asSharedFlow()

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

    fun updateMission(updatedMission: MissionPageItem) {
        missionRepository.updateMission(updatedMission)
    }

    fun endActiveMission() {
        val activeMission =
            missionRepository.state.value.activeMission
                ?: return

        viewModelScope.launch {
            /*
             * TODO(Tracking): Replace this temporary journey creation with the
             * verified journey result from the sensor/location tracking module.
             *
             * TODO(Error handling): Expose an error state if journey creation or
             * persistence fails. The active mission must only be cleared after the
             * journey has been saved successfully.
             */
            val journeyId =
                missionJourneyRecorder.createPartialJourney(
                    mission = activeMission,
                )

            missionRepository.endActiveMission()

            _journeyReviewEvents.emit(journeyId)
        }
    }
}