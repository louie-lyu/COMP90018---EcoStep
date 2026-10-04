package com.ecostep.app.testing

import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionOccurrence
import com.ecostep.app.data.repository.MissionRepository
import com.ecostep.app.data.repository.MissionResultRepository
import com.ecostep.app.data.repository.WriteOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Survives "app restarts" in tests: create a new store over the same fake to simulate one. */
class FakeMissionRepository : MissionRepository {
    val missions = MutableStateFlow<Map<String, Mission>>(emptyMap())
    var writes = 0
        private set

    override fun observeMissions(): Flow<List<Mission>> = missions.map { it.values.toList() }

    override suspend fun createMission(mission: Mission): WriteOutcome {
        writes++
        check(!missions.value.containsKey(mission.missionId)) { "exists" }
        missions.value = missions.value + (mission.missionId to mission.copy(createdAtMillis = writes.toLong()))
        return WriteOutcome.SYNCED
    }

    override suspend fun updateMission(mission: Mission): WriteOutcome {
        writes++
        val createdAt = missions.value[mission.missionId]?.createdAtMillis
        missions.value = missions.value + (mission.missionId to mission.copy(createdAtMillis = createdAt))
        return WriteOutcome.SYNCED
    }

    override suspend fun seedIfEmpty(missions: List<Mission>): Boolean {
        if (this.missions.value.isNotEmpty()) return false
        missions.forEach { createMission(it) }
        return true
    }
}

/** Keyed by deterministic result ID, exactly like the Firestore collection. */
class FakeMissionResultRepository : MissionResultRepository {
    val results = MutableStateFlow<Map<String, MissionOccurrence>>(emptyMap())
    var completeCalls = 0
        private set

    override fun observeOccurrences(fromDate: String): Flow<List<MissionOccurrence>> =
        results.map { all -> all.values.filter { it.occurrenceDate >= fromDate } }

    override suspend fun getOccurrences(fromDate: String): List<MissionOccurrence> =
        results.value.values.filter { it.occurrenceDate >= fromDate }

    override suspend fun markStarted(missionId: String, occurrenceDate: String, atMillis: Long) =
        merge(missionId, occurrenceDate) { it.copy(accepted = true, skipped = false, lastActionAtMillis = atMillis) }

    override suspend fun markSkipped(missionId: String, occurrenceDate: String, atMillis: Long) =
        merge(missionId, occurrenceDate) {
            it.copy(accepted = false, skipped = true, completed = false, lastActionAtMillis = atMillis)
        }

    override suspend fun markCompleted(
        missionId: String,
        occurrenceDate: String,
        journeyId: String,
        atMillis: Long,
    ): WriteOutcome {
        completeCalls++
        return merge(missionId, occurrenceDate) {
            it.copy(
                accepted = true,
                skipped = false,
                completed = true,
                linkedJourneyId = journeyId,
                completedAtMillis = atMillis,
                lastActionAtMillis = atMillis,
            )
        }
    }

    private fun merge(
        missionId: String,
        date: String,
        change: (MissionOccurrence) -> MissionOccurrence,
    ): WriteOutcome {
        val id = MissionOccurrence.resultId(missionId, date)
        val current = results.value[id] ?: MissionOccurrence(id, missionId, date)
        results.value = results.value + (id to change(current))
        return WriteOutcome.SYNCED
    }
}
