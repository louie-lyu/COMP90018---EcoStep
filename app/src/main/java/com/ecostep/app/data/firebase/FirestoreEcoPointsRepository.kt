package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.PointTransaction
import com.ecostep.app.data.model.PointTransactionType
import com.ecostep.app.data.model.UserStats
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.EcoPointsRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreEcoPointsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
) : EcoPointsRepository {

    override fun observeStats(): Flow<UserStats> = callbackFlow {
        val uid = requireCurrentUserId()
        val listener = stats(uid).addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                close(exception)
                return@addSnapshotListener
            }
            trySend(snapshot?.data?.toUserStats() ?: UserStats())
        }
        awaitClose { listener.remove() }
    }

    override suspend fun getStats(): UserStats {
        val uid = requireCurrentUserId()
        return stats(uid).get().await().data?.toUserStats() ?: UserStats()
    }

    override fun observeTransactions(limit: Int): Flow<List<PointTransaction>> = callbackFlow {
        val uid = requireCurrentUserId()
        val listener = firestore.collection("users").document(uid)
            .collection("pointTransactions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { document ->
                        document.data?.toPointTransaction(document.id)
                    },
                )
            }
        awaitClose { listener.remove() }
    }

    private fun requireCurrentUserId(): String =
        authRepository.currentUserId
            ?: throw IllegalStateException("You must be signed in to view EcoPoints.")

    private fun stats(uid: String) = firestore.collection("userStats").document(uid)
}

internal fun Map<String, Any?>.toUserStats(): UserStats = UserStats(
    pointsBalance = nonNegativeInt("pointsBalance"),
    totalJourneys = nonNegativeInt("totalJourneys"),
    carbonSavedGrams = (this["carbonSavedGrams"] as? Number)?.toDouble()
        ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
    completedMissions = nonNegativeInt("completedMissions"),
    updatedAtMillis = timestampMillis(this["updatedAt"]),
)

internal fun Map<String, Any?>.toPointTransaction(documentId: String): PointTransaction? {
    val type = PointTransactionType.fromFirestore(this["type"]) ?: return null
    val amount = (this["amount"] as? Number)?.toLong() ?: return null
    if (amount !in Int.MIN_VALUE..Int.MAX_VALUE) return null
    return PointTransaction(
        transactionId = documentId,
        type = type,
        amount = amount.toInt(),
        sourceId = this["sourceId"] as? String ?: "",
        createdAtMillis = timestampMillis(this["createdAt"]),
    )
}

private fun Map<String, Any?>.nonNegativeInt(field: String): Int =
    (this[field] as? Number)?.toLong()?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0
