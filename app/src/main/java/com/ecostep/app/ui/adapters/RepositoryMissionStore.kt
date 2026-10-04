package com.ecostep.app.ui.adapters

import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionOccurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.MissionTransitions
import com.ecostep.app.data.repository.MissionResultRepository
import com.ecostep.app.ui.mock.MissionPageItem
import com.ecostep.app.ui.mock.MissionRepositoryState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ecostep.app.data.repository.MissionRepository as MissionDataRepository
import com.ecostep.app.ui.mock.MissionRepository as MissionUiRepository

/**
 * Persistent mission state shared by Home and Missions. Adapts the Firestore mission and
 * occurrence repositories to the UI's existing MissionRepository contract; Firestore's
 * latency compensation updates [state] immediately after each local write, also offline.
 */
class RepositoryMissionStore(
    private val missionRepository: MissionDataRepository,
    private val missionResultRepository: MissionResultRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
    /** Development seed written only when the user has no missions at all. */
    private val seedMissions: () -> List<Mission> = { emptyList() },
) : MissionUiRepository {

    private val _state = MutableStateFlow(MissionRepositoryState())
    override val state: StateFlow<MissionRepositoryState> = _state.asStateFlow()

    private var missions: List<Mission> = emptyList()
    private var occurrences: Map<String, MissionOccurrence> = emptyMap()
    private var boundUid: String? = null
    private var observeJob: Job? = null

    /** (Re)subscribes for the signed-in user; call whenever a mission screen is shown. */
    fun bind(uid: String?) {
        if (uid == boundUid) return
        boundUid = uid
        observeJob?.cancel()
        missions = emptyList()
        occurrences = emptyMap()
        _state.value = MissionRepositoryState()
        if (uid == null) return

        observeJob = scope.launch {
            val seed = seedMissions()
            if (seed.isNotEmpty()) {
                launch { runCatchingData { missionRepository.seedIfEmpty(seed) } }
            }
            // Yesterday onwards, so an occurrence started before midnight is still found.
            val fromDate = today().minusDays(1).toString()
            combine(
                missionRepository.observeMissions(),
                missionResultRepository.observeOccurrences(fromDate),
            ) { missionList, occurrenceList -> missionList to occurrenceList }
                .catch { exception ->
                    _state.update { it.copy(errorMessage = exception.userMessage()) }
                }
                .collect { (missionList, occurrenceList) ->
                    missions = missionList
                    occurrences = occurrenceList.associateBy { it.resultId }
                    publish()
                }
        }
    }

    override fun acceptSuggestedMission() {
        val mission = suggestedMission() ?: return
        persist { missionRepository.updateMission(MissionTransitions.accept(mission, today())) }
    }

    override fun dismissSuggestedMission() {
        val mission = suggestedMission() ?: return
        persist { missionRepository.updateMission(MissionTransitions.dismiss(mission)) }
    }

    override fun startMission(missionId: String) {
        if (missions.any { it.status == MissionStatus.ACTIVE }) return
        val mission = find(missionId, MissionStatus.ACCEPTED) ?: return
        val today = today()
        // Today's occurrence is already done; the next one starts on its own date.
        if (occurrence(missionId, today.toString())?.completed == true) return
        persist {
            missionRepository.updateMission(MissionTransitions.start(mission, today))
            missionResultRepository.markStarted(missionId, today.toString(), clock())
        }
    }

    override fun skipMissionToday(missionId: String) {
        val mission = find(missionId, MissionStatus.ACCEPTED) ?: return
        val today = today()
        persist {
            missionResultRepository.markSkipped(missionId, today.toString(), clock())
            missionRepository.updateMission(MissionTransitions.skipToday(mission, today))
        }
    }

    override fun updateMission(updatedMission: MissionPageItem) {
        val mission = missions.firstOrNull { it.missionId == updatedMission.mission.missionId } ?: return
        val edited = MissionTransitions.edit(mission, mission.withEditsFrom(updatedMission), today())
        persist { missionRepository.updateMission(edited) }
    }

    override fun endActiveMission() {
        val mission = missions.firstOrNull { it.status == MissionStatus.ACTIVE } ?: return
        val date = mission.activeOccurrenceDate?.let(LocalDate::parse) ?: today()
        val completed = occurrence(mission.missionId, date.toString())?.completed == true
        persist { missionRepository.updateMission(MissionTransitions.end(mission, date, completed)) }
    }

    /** Mission linked to journeys recorded right now. */
    fun activeMissionId(): String? =
        missions.firstOrNull { it.status == MissionStatus.ACTIVE }?.missionId

    /**
     * Called after the user confirms a journey linked to [missionId]. Records the completed
     * occurrence (the backend then verifies the journey and awards EcoPoints once) and ends
     * the active occurrence. Repeating it is a no-op.
     */
    suspend fun completeOccurrence(missionId: String, journeyId: String) {
        val mission = missions.firstOrNull { it.missionId == missionId } ?: return
        val date = mission.activeOccurrenceDate ?: today().toString()
        if (occurrence(missionId, date)?.completed == true) return
        missionResultRepository.markCompleted(missionId, date, journeyId, clock())
        if (mission.status == MissionStatus.ACTIVE) {
            missionRepository.updateMission(
                MissionTransitions.end(mission, LocalDate.parse(date), completed = true),
            )
        }
    }

    private fun publish() {
        val today = today().toString()
        val suggested = suggestedMission()
        val active = missions.firstOrNull { it.status == MissionStatus.ACTIVE }
        // Missions already done (or skipped) today stay listed with their next date, after
        // the ones still open today.
        val upcoming = missions
            .filter { it.status == MissionStatus.ACCEPTED }
            .map { mission ->
                val todayOccurrence = occurrence(mission.missionId, today)
                mission.toPageItem().copy(
                    completedToday = todayOccurrence?.completed == true,
                    skippedToday = todayOccurrence?.skipped == true,
                )
            }
            .sortedWith(
                compareBy<MissionPageItem> { it.completedToday || it.skippedToday }
                    .thenBy { it.scheduledHour * 60 + it.scheduledMinute },
            )
        _state.update {
            it.copy(
                suggestedMission = suggested?.toPageItem(),
                upcomingMissions = upcoming,
                activeMission = active?.toPageItem(),
            )
        }
    }

    private fun suggestedMission(): Mission? =
        missions
            .filter { it.status == MissionStatus.SUGGESTED }
            .minByOrNull { it.createdAtMillis ?: Long.MAX_VALUE }

    private fun find(missionId: String, status: MissionStatus): Mission? =
        missions.firstOrNull { it.missionId == missionId && it.status == status }

    private fun occurrence(missionId: String, date: String): MissionOccurrence? =
        occurrences[MissionOccurrence.resultId(missionId, date)]

    private fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zoneId()).toLocalDate()

    private fun persist(block: suspend () -> Unit) {
        scope.launch { runCatchingData(block) }
    }

    private suspend fun runCatchingData(block: suspend () -> Unit) {
        try {
            _state.update { it.copy(errorMessage = null) }
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            _state.update { it.copy(errorMessage = exception.userMessage()) }
        }
    }
}

private fun Throwable.userMessage(): String = message ?: "Unable to update missions."
