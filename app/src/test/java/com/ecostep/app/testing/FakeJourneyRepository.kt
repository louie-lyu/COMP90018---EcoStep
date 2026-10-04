package com.ecostep.app.testing

import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.requireValidForWrite
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.data.repository.JourneySnapshot
import com.ecostep.app.data.repository.WriteOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * In-memory JourneyRepository with the same contract as the Firestore one: data is keyed by
 * the signed-in UID, creates never overwrite, confirmation only touches confirmation fields,
 * and [online] = false queues writes (hasPendingWrites) until [goOnline].
 */
class FakeJourneyRepository(
    private val auth: AuthRepository,
) : JourneyRepository {

    private data class Stored(val journey: JourneySummary, val pending: Boolean)

    private val store = MutableStateFlow<Map<String, Map<String, Stored>>>(emptyMap())

    var online = true
    var createCalls = 0
        private set
    var confirmCalls = 0
        private set
    var failNextWrite: Exception? = null

    private fun uid(): String =
        auth.currentUserId ?: throw IllegalStateException("You must be signed in to access journeys.")

    override fun observeJourneyHistory(userId: String): Flow<List<JourneySummary>> =
        observeJourneyHistorySnapshots(userId).map { list -> list.map { it.journey } }

    override fun observeJourneyHistorySnapshots(userId: String): Flow<List<JourneySnapshot>> = flow {
        val uid = uid()
        require(userId == uid) { "Cannot read another user's journeys." }
        emitAll(
            store.map { all ->
                all[uid].orEmpty().values
                    .map { JourneySnapshot(it.journey, it.pending) }
                    .sortedByDescending { it.journey.startTimeMillis }
            },
        )
    }

    override suspend fun getJourney(journeyId: String): JourneySummary? =
        store.value[uid()]?.get(journeyId)?.journey

    override fun observeJourney(journeyId: String): Flow<JourneySnapshot?> = flow {
        val uid = uid()
        emitAll(
            store.map { all ->
                all[uid]?.get(journeyId)?.let { JourneySnapshot(it.journey, it.pending) }
            },
        )
    }

    override suspend fun createJourney(journey: JourneySummary): WriteOutcome {
        createCalls++
        failNextWrite?.let { failNextWrite = null; throw it }
        val uid = uid()
        val owned = journey.copy(userId = uid, carbonSavedGrams = null, ecoPoints = null)
        owned.requireValidForWrite()
        if (store.value[uid]?.containsKey(owned.journeyId) == true) {
            throw IllegalStateException("PERMISSION_DENIED: journey already exists")
        }
        put(uid, owned)
        return outcome()
    }

    override suspend fun confirmTransportMode(journeyId: String, mode: TransportMode): WriteOutcome {
        confirmCalls++
        failNextWrite?.let { failNextWrite = null; throw it }
        require(mode != TransportMode.UNKNOWN)
        val uid = uid()
        val existing = store.value[uid]?.get(journeyId)?.journey
            ?: throw IllegalStateException("NOT_FOUND")
        put(
            uid,
            existing.copy(
                transportMode = mode,
                confirmedTransportMode = mode,
                confirmationStatus = JourneyConfirmationStatus.CONFIRMED,
            ),
        )
        return outcome()
    }

    /** Simulates the backend writing trusted values after confirmation. */
    fun applyBackendResult(uid: String, journeyId: String, carbonSavedGrams: Double, ecoPoints: Int) {
        val existing = store.value.getValue(uid).getValue(journeyId)
        store.value = store.value + (
            uid to store.value.getValue(uid) + (
                journeyId to existing.copy(
                    journey = existing.journey.copy(
                        carbonSavedGrams = carbonSavedGrams,
                        ecoPoints = ecoPoints,
                    ),
                )
                )
            )
    }

    fun goOnline() {
        online = true
        store.value = store.value.mapValues { (_, journeys) ->
            journeys.mapValues { (_, stored) -> stored.copy(pending = false) }
        }
    }

    fun seed(uid: String, journey: JourneySummary) = put(uid, journey.copy(userId = uid), pending = false)

    private fun put(uid: String, journey: JourneySummary, pending: Boolean = !online) {
        val userJourneys = store.value[uid].orEmpty() + (journey.journeyId to Stored(journey, pending))
        store.value = store.value + (uid to userJourneys)
    }

    private fun outcome() = if (online) WriteOutcome.SYNCED else WriteOutcome.QUEUED
}
