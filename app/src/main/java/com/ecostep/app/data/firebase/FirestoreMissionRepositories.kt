package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionOccurrence
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.MissionRepository
import com.ecostep.app.data.repository.MissionResultRepository
import com.ecostep.app.data.repository.WriteOutcome
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreMissionRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val ackTimeoutMillis: Long = DEFAULT_ACK_TIMEOUT_MILLIS,
) : MissionRepository {

    override fun observeMissions(): Flow<List<Mission>> = callbackFlow {
        val uid = requireUid()
        val listener = missions(uid).addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                close(exception)
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.data?.toMission(it.id) })
        }
        awaitClose { listener.remove() }
    }

    override suspend fun createMission(mission: Mission): WriteOutcome {
        val uid = requireUid()
        mission.requireValidForWrite()
        return missions(uid).document(mission.missionId)
            .set(
                mission.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            .awaitWriteOutcome(ackTimeoutMillis)
    }

    override suspend fun updateMission(mission: Mission): WriteOutcome {
        val uid = requireUid()
        mission.requireValidForWrite()
        return missions(uid).document(mission.missionId)
            .set(
                mission.toFirestoreMap() + ("updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            .awaitWriteOutcome(ackTimeoutMillis)
    }

    override suspend fun seedIfEmpty(missions: List<Mission>): Boolean {
        val uid = requireUid()
        val existing = missions(uid).limit(1).get(Source.SERVER).await()
        if (!existing.isEmpty) return false
        val batch = firestore.batch()
        missions.forEach { mission ->
            mission.requireValidForWrite()
            batch.set(
                missions(uid).document(mission.missionId),
                mission.toFirestoreMap() + mapOf(
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.commit().await()
        return true
    }

    private fun requireUid(): String =
        authRepository.currentUserId
            ?: throw IllegalStateException("You must be signed in to access missions.")

    private fun missions(uid: String) =
        firestore.collection("users").document(uid).collection("missions")
}

class FirestoreMissionResultRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val ackTimeoutMillis: Long = DEFAULT_ACK_TIMEOUT_MILLIS,
) : MissionResultRepository {

    override fun observeOccurrences(fromDate: String): Flow<List<MissionOccurrence>> = callbackFlow {
        val uid = requireUid()
        val listener = results(uid)
            .whereGreaterThanOrEqualTo("occurrenceDate", fromDate)
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { it.data?.toMissionOccurrence(it.id) },
                )
            }
        awaitClose { listener.remove() }
    }

    override suspend fun getOccurrences(fromDate: String): List<MissionOccurrence> {
        val uid = requireUid()
        return results(uid)
            .whereGreaterThanOrEqualTo("occurrenceDate", fromDate)
            .get().await()
            .documents.mapNotNull { it.data?.toMissionOccurrence(it.id) }
    }

    override suspend fun markStarted(
        missionId: String,
        occurrenceDate: String,
        atMillis: Long,
    ): WriteOutcome = merge(
        missionId,
        occurrenceDate,
        mapOf("accepted" to true, "skipped" to false),
        atMillis,
    )

    override suspend fun markSkipped(
        missionId: String,
        occurrenceDate: String,
        atMillis: Long,
    ): WriteOutcome = merge(
        missionId,
        occurrenceDate,
        mapOf("accepted" to false, "skipped" to true, "completed" to false),
        atMillis,
    )

    override suspend fun markCompleted(
        missionId: String,
        occurrenceDate: String,
        journeyId: String,
        atMillis: Long,
    ): WriteOutcome {
        require(journeyId.isNotBlank()) { "A confirmed journey is required." }
        return merge(
            missionId,
            occurrenceDate,
            mapOf(
                "accepted" to true,
                "skipped" to false,
                "completed" to true,
                "linkedJourneyId" to journeyId,
                "completedAtMillis" to atMillis,
            ),
            atMillis,
        )
    }

    private suspend fun merge(
        missionId: String,
        occurrenceDate: String,
        fields: Map<String, Any?>,
        atMillis: Long,
    ): WriteOutcome {
        val uid = requireUid()
        return results(uid)
            .document(MissionOccurrence.resultId(missionId, occurrenceDate))
            .set(
                occurrenceMergeDocument(
                    missionId = missionId,
                    occurrenceDate = occurrenceDate,
                    fields = fields,
                    atMillis = atMillis,
                    serverTimestamp = FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .awaitWriteOutcome(ackTimeoutMillis)
    }

    private fun requireUid(): String =
        authRepository.currentUserId
            ?: throw IllegalStateException("You must be signed in to access missions.")

    private fun results(uid: String) =
        firestore.collection("users").document(uid).collection("missionResults")
}
