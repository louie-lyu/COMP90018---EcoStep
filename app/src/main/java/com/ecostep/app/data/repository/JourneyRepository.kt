package com.ecostep.app.data.repository

import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owner: Zongcheng Jiang (Sensors, Journey Tracking, Auth & Database module).
 * Due: JourneySummary by 20 Sep, per docs/WORK_PLAN.md.
 *
 * Implemented against GPS/accelerometer/gyroscope sensor data, Firebase Auth and Firestore.
 * Other modules build against this interface, not the concrete implementation, so they can
 * start work with mock data before it's ready (see docs/DEPENDENCIES.md "Using Mock Data").
 *
 * Every implementation scopes reads and writes to the signed-in user; a `userId` passed by
 * the caller is only checked, never trusted.
 */
interface JourneyRepository {
    fun observeJourneyHistory(userId: String): Flow<List<JourneySummary>>

    /** History plus per-journey sync state, for "pending sync" markers. */
    fun observeJourneyHistorySnapshots(userId: String): Flow<List<JourneySnapshot>> =
        observeJourneyHistory(userId).map { journeys ->
            journeys.map { JourneySnapshot(it, hasPendingWrites = false) }
        }

    suspend fun getJourney(journeyId: String): JourneySummary?

    /** Live view of one journey, including whether local edits are still waiting to sync. */
    fun observeJourney(journeyId: String): Flow<JourneySnapshot?>

    /**
     * First write of a recorded journey (detected mode, status pending). Never overwrites
     * an existing document.
     */
    suspend fun createJourney(journey: JourneySummary): WriteOutcome

    /**
     * Records the user-confirmed mode. Repeating the same confirmation is an idempotent
     * update; only confirmation fields and `updatedAt` change.
     */
    suspend fun confirmTransportMode(journeyId: String, mode: TransportMode): WriteOutcome

    @Deprecated(
        "Use createJourney for new journeys or confirmTransportMode for review updates.",
        ReplaceWith("createJourney(journey)"),
    )
    suspend fun saveJourney(journey: JourneySummary) {
        createJourney(journey)
    }
}

data class JourneySnapshot(
    val journey: JourneySummary,
    /** True while the document only exists in (or differs from the server in) the local cache. */
    val hasPendingWrites: Boolean,
)

/** Result of a local-first write. */
enum class WriteOutcome {
    /** The server acknowledged the write. */
    SYNCED,

    /** Saved to the local cache and queued; it syncs automatically when the network returns. */
    QUEUED,
}
