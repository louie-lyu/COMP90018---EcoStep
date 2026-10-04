package com.ecostep.app.data.model

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.IsoFields

enum class FriendRequestState(val firestoreValue: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    DECLINED("declined"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromFirestore(value: Any?): FriendRequestState? =
            entries.firstOrNull { it.firestoreValue == value }
    }
}

/** Mirrored at users/{senderUid}/friendRequests/{id} and users/{receiverUid}/friendRequests/{id}. */
data class FriendRequest(
    val requestId: String,
    val senderUid: String,
    val receiverUid: String,
    val senderDisplayName: String,
    val receiverDisplayName: String,
    val status: FriendRequestState,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
)

enum class UserRelationship {
    NONE,
    SELF,
    FRIEND,
    REQUEST_SENT,
    REQUEST_RECEIVED,
}

/** Result of the backend user search. Email is never returned, only a masked hint. */
data class UserSearchResult(
    val uid: String,
    val displayName: String,
    val maskedEmail: String?,
    val relationship: UserRelationship,
)

enum class LeaderboardScope {
    FRIENDS,
    COMMUNITY,
}

enum class LeaderboardPeriod {
    THIS_WEEK,
    ALL_TIME,
}

data class LeaderboardEntry(
    val rank: Int,
    val uid: String,
    val displayName: String,
    val completedJourneys: Int,
    val carbonSavedGrams: Double,
    val isCurrentUser: Boolean,
)

data class Leaderboard(
    /** Top entries (community: at most 10). */
    val entries: List<LeaderboardEntry>,
    /** The signed-in user's own position when it is not among [entries]. */
    val currentUserEntry: LeaderboardEntry? = null,
)

/**
 * Leaderboard period IDs shared with the backend: `all_time`, or the UTC ISO week such as
 * `week-2026-W40`.
 */
object LeaderboardPeriods {
    const val ALL_TIME = "all_time"

    fun weekId(epochMillis: Long): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "week-%04d-W%02d".format(year, week)
    }

    fun periodId(period: LeaderboardPeriod, nowMillis: Long): String = when (period) {
        LeaderboardPeriod.ALL_TIME -> ALL_TIME
        LeaderboardPeriod.THIS_WEEK -> weekId(nowMillis)
    }
}
