package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.requireValidForWrite
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.data.repository.JourneySnapshot
import com.ecostep.app.data.repository.WriteOutcome
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreJourneyRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val ackTimeoutMillis: Long = DEFAULT_ACK_TIMEOUT_MILLIS,
) : JourneyRepository {

    override fun observeJourneyHistory(userId: String): Flow<List<JourneySummary>> = callbackFlow {
        val uid = requireCurrentUserId()
        require(userId == uid) { "Cannot read another user's journeys." }

        val listener = journeys(uid).addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                close(exception)
                return@addSnapshotListener
            }
            val history = snapshot
                ?.documents
                .orEmpty()
                // A single corrupt document must not hide the rest of the history.
                .mapNotNull { it.data?.toJourneySummaryOrNull(it.id) }
                .sortedByDescending { it.startTimeMillis }
            trySend(history)
        }

        awaitClose { listener.remove() }
    }

    override fun observeJourneyHistorySnapshots(userId: String): Flow<List<JourneySnapshot>> =
        callbackFlow {
            val uid = requireCurrentUserId()
            require(userId == uid) { "Cannot read another user's journeys." }

            val listener = journeys(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }
                val history = snapshot
                    ?.documents
                    .orEmpty()
                    .mapNotNull { document ->
                        document.data?.toJourneySummaryOrNull(document.id)?.let {
                            JourneySnapshot(it, document.metadata.hasPendingWrites())
                        }
                    }
                    .sortedByDescending { it.journey.startTimeMillis }
                trySend(history)
            }

            awaitClose { listener.remove() }
        }

    override suspend fun getJourney(journeyId: String): JourneySummary? {
        val uid = requireCurrentUserId()
        val document = journeys(uid).document(journeyId)
        // Pending offline writes are available in the cache before server acknowledgement.
        val cached = try {
            document.get(Source.CACHE).await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }
        if (cached?.exists() == true && cached.metadata.hasPendingWrites()) {
            return cached.toJourneySummary()
        }
        val snapshot = try {
            document.get().await()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            // Offline: fall back to the cached copy rather than reporting "not found".
            if (cached?.exists() == true) return cached.toJourneySummary()
            throw exception
        }
        return if (snapshot.exists()) snapshot.toJourneySummary() else null
    }

    override fun observeJourney(journeyId: String): Flow<JourneySnapshot?> = callbackFlow {
        val uid = requireCurrentUserId()
        val listener = journeys(uid)
            .document(journeyId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                if (!snapshot.exists()) {
                    // A cache miss is not proof the journey is missing; wait for the server.
                    if (!snapshot.metadata.isFromCache) trySend(null)
                    return@addSnapshotListener
                }
                try {
                    trySend(
                        JourneySnapshot(
                            journey = snapshot.toJourneySummary(),
                            hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                        ),
                    )
                } catch (mappingException: IllegalStateException) {
                    close(mappingException)
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun createJourney(journey: JourneySummary): WriteOutcome {
        val uid = requireCurrentUserId()
        val ownedJourney = journey.copy(
            userId = uid,
            // Backend-only values are never sent by the client.
            carbonSavedGrams = null,
            ecoPoints = null,
            createdAtMillis = null,
            updatedAtMillis = null,
        )
        ownedJourney.requireValidForWrite()

        // Plain set: security rules reject it if the document already exists, so a create can
        // never overwrite fields added later (confirmation, backend results).
        return journeys(uid)
            .document(ownedJourney.journeyId)
            .set(ownedJourney.toFirestoreCreateMap(FieldValue.serverTimestamp()))
            .awaitWriteOutcome(ackTimeoutMillis)
    }

    override suspend fun confirmTransportMode(
        journeyId: String,
        mode: TransportMode,
    ): WriteOutcome {
        val uid = requireCurrentUserId()
        require(journeyId.isNotBlank()) { "Journey ID cannot be empty." }
        require(mode != TransportMode.UNKNOWN) { "Choose a transport mode to confirm." }

        return journeys(uid)
            .document(journeyId)
            .update(confirmationUpdate(mode, FieldValue.serverTimestamp()))
            .awaitWriteOutcome(ackTimeoutMillis)
    }

    private fun requireCurrentUserId(): String =
        authRepository.currentUserId
            ?: throw IllegalStateException("You must be signed in to access journeys.")

    private fun journeys(uid: String) =
        firestore.collection("users").document(uid).collection("journeys")
}

private fun DocumentSnapshot.toJourneySummary(): JourneySummary {
    val data = data ?: throw IllegalStateException("Journey $id has no data.")
    return data.toJourneySummary(id)
}
