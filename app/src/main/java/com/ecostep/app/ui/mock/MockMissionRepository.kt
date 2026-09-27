package com.ecostep.app.ui.mock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update




data class MissionRepositoryState(
    val suggestedMission: MissionPageItem? = null,
    val upcomingMissions: List<MissionPageItem> = emptyList(),
    val activeMission: MissionPageItem? = null,
)

interface MissionRepository {
    val state: StateFlow<MissionRepositoryState>

    fun acceptSuggestedMission()

    fun dismissSuggestedMission()

    fun startMission(missionId: String)

    fun skipMissionToday(missionId: String)

    fun updateMission(updatedMission: MissionPageItem)

    fun endActiveMission()
}

/**
 * Temporary in-memory mission repository shared by HomeScreen and
 * MissionScreen.
 *
 * TODO(Missions/Firebase): Replace this class with a production
 * MissionRepository implementation that:
 * - loads and saves missions for the signed-in user;
 * - persists accepted, dismissed, edited and active mission states;
 * - records "Skip today" for only the current occurrence without deleting
 *   the recurring mission;
 * - restores mission state after the app restarts.
 *
 * Journey tracking and reward calculation should remain the responsibility
 * of the tracking, journey and algorithm modules.
 */
class MockMissionRepository(
    dataSource: MissionScreenDataSource =
        MockMissionScreenDataSource(),
) : MissionRepository {

    private val _state =
        MutableStateFlow(
            MissionRepositoryState(
                suggestedMission =
                    dataSource.getSuggestedMission(),
                upcomingMissions =
                    dataSource.getUpcomingMissions(),
            ),
        )

    override val state: StateFlow<MissionRepositoryState> =
        _state.asStateFlow()

    override fun acceptSuggestedMission() {
        _state.update { currentState ->
            val suggestion =
                currentState.suggestedMission
                    ?: return@update currentState

            currentState.copy(
                suggestedMission = null,
                upcomingMissions =
                    currentState.upcomingMissions + suggestion,
            )
        }

        // TODO(Missions): Persist the accepted mission.
    }

    override fun dismissSuggestedMission() {
        _state.update { currentState ->
            currentState.copy(
                suggestedMission = null,
            )
        }

        // TODO(Missions): Persist the dismissal.
    }

    override fun startMission(missionId: String) {
        _state.update { currentState ->
            val selectedMission =
                currentState.upcomingMissions.firstOrNull {
                    it.mission.missionId == missionId
                } ?: return@update currentState

            currentState.copy(
                activeMission = selectedMission,
                upcomingMissions =
                    currentState.upcomingMissions.filterNot {
                        it.mission.missionId == missionId
                    },
            )
        }

        // TODO(Missions): Persist this mission occurrence as active.
        // Production journey tracking should be started separately by
        // the tracking coordinator or ViewModel.
    }

    override fun skipMissionToday(missionId: String) {
        _state.update { currentState ->
            currentState.copy(
                upcomingMissions =
                    currentState.upcomingMissions.filterNot {
                        it.mission.missionId == missionId
                    },
            )
        }

        // TODO(Missions): Persist today's skip while retaining
        // the recurring mission for its next occurrence.
    }

    override fun updateMission(
        updatedMission: MissionPageItem,
    ) {
        _state.update { currentState ->
            currentState.copy(
                suggestedMission =
                    currentState.suggestedMission?.let {
                            suggestion ->
                        if (
                            suggestion.mission.missionId ==
                            updatedMission.mission.missionId
                        ) {
                            updatedMission
                        } else {
                            suggestion
                        }
                    },
                upcomingMissions =
                    currentState.upcomingMissions.map { mission ->
                        if (
                            mission.mission.missionId ==
                            updatedMission.mission.missionId
                        ) {
                            updatedMission
                        } else {
                            mission
                        }
                    },
                activeMission =
                    currentState.activeMission?.let { active ->
                        if (
                            active.mission.missionId ==
                            updatedMission.mission.missionId
                        ) {
                            updatedMission
                        } else {
                            active
                        }
                    },
            )
        }

        // TODO(Missions): Persist the edited mission.
    }

    override fun endActiveMission() {
        _state.update { currentState ->
            currentState.copy(
                activeMission = null,
            )
        }

        /*
         * TODO(Missions): Persist this mission occurrence as ended.
         *
         * Partial journey creation, verified tracking data and reward
         * calculation are handled separately by the journey-tracking,
         * journey repository and algorithm modules.
         */
    }
}