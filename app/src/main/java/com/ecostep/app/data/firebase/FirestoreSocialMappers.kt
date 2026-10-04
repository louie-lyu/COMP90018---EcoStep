package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.FriendRequest
import com.ecostep.app.data.model.FriendRequestState
import com.ecostep.app.data.model.RedemptionStatus
import com.ecostep.app.data.model.Reward
import com.ecostep.app.data.model.RewardRedemption
import com.ecostep.app.data.model.UserRelationship
import com.ecostep.app.data.model.UserSearchResult

/** Values aggregated by the backend for one user and one leaderboard period. */
internal data class LeaderboardTotals(
    val uid: String,
    val displayName: String,
    val carbonSavedGrams: Double,
    val completedJourneys: Int,
)

internal fun Map<String, Any?>.toFriendRequest(documentId: String): FriendRequest? {
    val sender = this["senderUid"] as? String ?: return null
    val receiver = this["receiverUid"] as? String ?: return null
    val status = FriendRequestState.fromFirestore(this["status"]) ?: return null
    return FriendRequest(
        requestId = documentId,
        senderUid = sender,
        receiverUid = receiver,
        senderDisplayName = this["senderDisplayName"] as? String ?: DEFAULT_DISPLAY_NAME,
        receiverDisplayName = this["receiverDisplayName"] as? String ?: DEFAULT_DISPLAY_NAME,
        status = status,
        createdAtMillis = timestampMillis(this["createdAt"]),
        updatedAtMillis = timestampMillis(this["updatedAt"]),
    )
}

/** Entry document at leaderboards/{periodId}/entries/{uid}. */
internal fun Map<String, Any?>.toLeaderboardTotals(uid: String): LeaderboardTotals =
    LeaderboardTotals(
        uid = uid,
        displayName = this["displayName"] as? String ?: DEFAULT_DISPLAY_NAME,
        carbonSavedGrams = nonNegativeDouble(this["carbonSavedGrams"]),
        completedJourneys = nonNegativeInt(this["completedJourneys"]),
    )

/**
 * Friend totals from leaderboardStats/{uid}. A `currentWeek` block recorded for an older week
 * counts as zero for this week.
 */
internal fun Map<String, Any?>.toFriendTotals(
    uid: String,
    periodId: String,
): LeaderboardTotals {
    val displayName = this["displayName"] as? String ?: DEFAULT_DISPLAY_NAME
    val block = if (periodId == "all_time") {
        this["allTime"] as? Map<*, *>
    } else {
        (this["currentWeek"] as? Map<*, *>)?.takeIf { it["weekId"] == periodId }
    }
    return LeaderboardTotals(
        uid = uid,
        displayName = displayName,
        carbonSavedGrams = nonNegativeDouble(block?.get("carbonSavedGrams")),
        completedJourneys = nonNegativeInt(block?.get("completedJourneys")),
    )
}

internal fun Map<String, Any?>.toReward(documentId: String): Reward? {
    val points = (this["pointsRequired"] as? Number)?.toLong() ?: return null
    if (points <= 0 || points > Int.MAX_VALUE) return null
    return Reward(
        rewardId = documentId,
        merchantName = this["merchantName"] as? String ?: "",
        title = this["title"] as? String ?: return null,
        description = this["description"] as? String ?: "",
        pointsRequired = points.toInt(),
        category = this["category"] as? String ?: "other",
        active = this["active"] as? Boolean ?: false,
        validFromMillis = timestampMillis(this["validFrom"]),
        validUntilMillis = timestampMillis(this["validUntil"]),
        inventory = (this["inventory"] as? Number)?.toInt()?.coerceAtLeast(0),
    )
}

/** Decodes a Firestore redemption document or a callable response (millis fields). */
internal fun Map<*, *>.toRewardRedemption(documentId: String? = null): RewardRedemption? {
    val id = documentId ?: this["redemptionId"] as? String ?: return null
    return RewardRedemption(
        redemptionId = id,
        rewardId = this["rewardId"] as? String ?: return null,
        rewardTitle = this["rewardTitle"] as? String ?: "",
        merchantName = this["merchantName"] as? String ?: "",
        rewardDescription = this["rewardDescription"] as? String ?: "",
        category = this["category"] as? String ?: "other",
        pointsSpent = nonNegativeInt(this["pointsSpent"]),
        redemptionCode = this["redemptionCode"] as? String ?: return null,
        status = RedemptionStatus.fromFirestore(this["status"]),
        redeemedAtMillis = timestampMillis(this["redeemedAt"] ?: this["redeemedAtMillis"]),
        expiresAtMillis = timestampMillis(this["expiresAt"] ?: this["expiresAtMillis"]),
        usedAtMillis = timestampMillis(this["usedAt"] ?: this["usedAtMillis"]),
    )
}

internal fun Map<*, *>.toUserSearchResult(): UserSearchResult? {
    val uid = this["uid"] as? String ?: return null
    return UserSearchResult(
        uid = uid,
        displayName = this["displayName"] as? String ?: DEFAULT_DISPLAY_NAME,
        maskedEmail = this["maskedEmail"] as? String,
        relationship = when (this["relationship"]) {
            "self" -> UserRelationship.SELF
            "friend" -> UserRelationship.FRIEND
            "request_sent" -> UserRelationship.REQUEST_SENT
            "request_received" -> UserRelationship.REQUEST_RECEIVED
            else -> UserRelationship.NONE
        },
    )
}

private fun nonNegativeDouble(value: Any?): Double =
    (value as? Number)?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

private fun nonNegativeInt(value: Any?): Int =
    (value as? Number)?.toLong()?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0
