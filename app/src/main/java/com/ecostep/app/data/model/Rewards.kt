package com.ecostep.app.data.model

/** Catalog entry at rewards/{rewardId}; written only by administrators. */
data class Reward(
    val rewardId: String,
    val merchantName: String,
    val title: String,
    val description: String,
    val pointsRequired: Int,
    val category: String,
    val active: Boolean,
    val validFromMillis: Long?,
    val validUntilMillis: Long?,
    /** Remaining stock; null means unlimited. */
    val inventory: Int?,
) {
    fun isRedeemableAt(nowMillis: Long): Boolean =
        active &&
            (validFromMillis == null || validFromMillis <= nowMillis) &&
            (validUntilMillis == null || nowMillis < validUntilMillis) &&
            (inventory == null || inventory > 0)
}

enum class RedemptionStatus(val firestoreValue: String) {
    AVAILABLE("available"),
    USED("used"),
    EXPIRED("expired"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromFirestore(value: Any?): RedemptionStatus =
            entries.firstOrNull { it.firestoreValue == value } ?: AVAILABLE
    }
}

/**
 * Created by the backend at users/{uid}/rewardRedemptions/{redemptionId}. Reward details are
 * copied in so history survives catalog changes.
 */
data class RewardRedemption(
    val redemptionId: String,
    val rewardId: String,
    val rewardTitle: String,
    val merchantName: String,
    val rewardDescription: String,
    val category: String,
    val pointsSpent: Int,
    val redemptionCode: String,
    val status: RedemptionStatus,
    val redeemedAtMillis: Long?,
    val expiresAtMillis: Long?,
    val usedAtMillis: Long?,
)
