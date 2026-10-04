package com.ecostep.app.data.model

/**
 * Backend-maintained summary at userStats/{uid}. Clients can only read it; the balance is
 * derived from the pointTransactions ledger by trusted code.
 */
data class UserStats(
    val pointsBalance: Int = 0,
    val totalJourneys: Int = 0,
    val carbonSavedGrams: Double = 0.0,
    val completedMissions: Int = 0,
    val updatedAtMillis: Long? = null,
)

enum class PointTransactionType(val firestoreValue: String) {
    MISSION_AWARD("mission_award"),
    REWARD_REDEMPTION("reward_redemption"),
    ADJUSTMENT("adjustment"),
    ;

    companion object {
        fun fromFirestore(value: Any?): PointTransactionType? =
            entries.firstOrNull { it.firestoreValue == value }
    }
}

/** One immutable ledger entry at users/{uid}/pointTransactions/{transactionId}. */
data class PointTransaction(
    val transactionId: String,
    val type: PointTransactionType,
    val amount: Int,
    val sourceId: String,
    val createdAtMillis: Long?,
)
