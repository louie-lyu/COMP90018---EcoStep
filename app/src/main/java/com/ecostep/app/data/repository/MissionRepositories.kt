package com.ecostep.app.data.repository

import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionOccurrence
import kotlinx.coroutines.flow.Flow

/** The signed-in user's mission definitions (users/{uid}/missions). */
interface MissionRepository {
    fun observeMissions(): Flow<List<Mission>>

    /** First write of a mission (suggestion or seed); sets `createdAt`. */
    suspend fun createMission(mission: Mission): WriteOutcome

    /** Updates the client-owned fields of an existing mission; `createdAt` is never touched. */
    suspend fun updateMission(mission: Mission): WriteOutcome

    /**
     * Writes [missions] only when the server reports no missions at all (development seed).
     * Returns true when seeded.
     */
    suspend fun seedIfEmpty(missions: List<Mission>): Boolean
}

/**
 * Mission occurrences and results (users/{uid}/missionResults). One document per
 * (missionId, occurrenceDate), so repeating an action never duplicates a result.
 */
interface MissionResultRepository {
    /** Occurrences whose date is on or after [fromDate] ("YYYY-MM-DD"). */
    fun observeOccurrences(fromDate: String): Flow<List<MissionOccurrence>>

    suspend fun getOccurrences(fromDate: String): List<MissionOccurrence>

    suspend fun markStarted(missionId: String, occurrenceDate: String, atMillis: Long): WriteOutcome

    suspend fun markSkipped(missionId: String, occurrenceDate: String, atMillis: Long): WriteOutcome

    /**
     * Requests completion with the confirmed journey. The backend verifies the journey and
     * awards EcoPoints once; repeating the call is a no-op.
     */
    suspend fun markCompleted(
        missionId: String,
        occurrenceDate: String,
        journeyId: String,
        atMillis: Long,
    ): WriteOutcome
}
