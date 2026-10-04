package com.ecostep.app.data.repository

import com.ecostep.app.data.model.FriendRequest
import com.ecostep.app.data.model.Leaderboard
import com.ecostep.app.data.model.LeaderboardPeriod
import com.ecostep.app.data.model.LeaderboardScope
import com.ecostep.app.data.model.Reward
import com.ecostep.app.data.model.RewardRedemption
import com.ecostep.app.data.model.UserSearchResult
import kotlinx.coroutines.flow.Flow

/**
 * Friend relationships. Every state change goes through the trusted backend so a client can
 * never add itself to someone else's friend list.
 */
interface FriendsRepository {
    suspend fun getFriendUids(): Set<String>

    fun observeFriendRequests(): Flow<List<FriendRequest>>

    suspend fun searchUsers(query: String): List<UserSearchResult>

    suspend fun sendFriendRequest(receiverUid: String)

    suspend fun respondToFriendRequest(requestId: String, accept: Boolean)

    suspend fun cancelFriendRequest(requestId: String)
}

/** Backend-aggregated carbon rankings; read-only for clients. */
interface LeaderboardRepository {
    suspend fun getLeaderboard(scope: LeaderboardScope, period: LeaderboardPeriod): Leaderboard
}

interface RewardsRepository {
    suspend fun getRewards(): List<Reward>

    suspend fun getRedemptions(): List<RewardRedemption>

    /**
     * Asks the backend to redeem [rewardId]. [requestId] makes retries idempotent: repeating a
     * request with the same ID returns the original redemption and never charges twice.
     */
    suspend fun redeem(rewardId: String, requestId: String): RewardRedemption
}

/** Calls a named HTTPS callable function; keeps Firebase types out of repositories' callers. */
fun interface CloudFunctionsClient {
    suspend fun call(name: String, data: Map<String, Any?>): Any?
}

/** A backend rejection with a message that is safe to show to the user. */
class BackendException(
    message: String,
    val code: String,
    cause: Throwable? = null,
) : Exception(message, cause)
