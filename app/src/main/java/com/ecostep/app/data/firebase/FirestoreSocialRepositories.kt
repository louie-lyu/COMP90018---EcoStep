package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.FriendRequest
import com.ecostep.app.data.model.Leaderboard
import com.ecostep.app.data.model.LeaderboardEntry
import com.ecostep.app.data.model.LeaderboardPeriod
import com.ecostep.app.data.model.LeaderboardPeriods
import com.ecostep.app.data.model.LeaderboardScope
import com.ecostep.app.data.model.Reward
import com.ecostep.app.data.model.RewardRedemption
import com.ecostep.app.data.model.UserSearchResult
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.BackendException
import com.ecostep.app.data.repository.CloudFunctionsClient
import com.ecostep.app.data.repository.FriendsRepository
import com.ecostep.app.data.repository.LeaderboardRepository
import com.ecostep.app.data.repository.RewardsRepository
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private fun AuthRepository.requireUid(): String =
    currentUserId ?: throw IllegalStateException("You must be signed in.")

class FirestoreFriendsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val functions: CloudFunctionsClient,
) : FriendsRepository {

    override suspend fun getFriendUids(): Set<String> {
        val uid = authRepository.requireUid()
        return userDocument(uid).collection("friends").get().await()
            .documents.mapTo(mutableSetOf()) { it.id }
    }

    override fun observeFriendRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = authRepository.requireUid()
        val listener = userDocument(uid).collection("friendRequests")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    close(exception)
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { it.data?.toFriendRequest(it.id) }
                        .sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE },
                )
            }
        awaitClose { listener.remove() }
    }

    override suspend fun searchUsers(query: String): List<UserSearchResult> {
        val normalized = query.trim()
        if (normalized.length < MIN_SEARCH_LENGTH) return emptyList()
        val response = functions.call("searchUsers", mapOf("query" to normalized)) as? Map<*, *>
        return (response?.get("results") as? List<*>).orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.toUserSearchResult() }
    }

    override suspend fun sendFriendRequest(receiverUid: String) {
        require(receiverUid.isNotBlank()) { "Choose a user first." }
        functions.call("sendFriendRequest", mapOf("receiverUid" to receiverUid))
    }

    override suspend fun respondToFriendRequest(requestId: String, accept: Boolean) {
        functions.call(
            "respondToFriendRequest",
            mapOf("requestId" to requestId, "accept" to accept),
        )
    }

    override suspend fun cancelFriendRequest(requestId: String) {
        functions.call("cancelFriendRequest", mapOf("requestId" to requestId))
    }

    private fun userDocument(uid: String) = firestore.collection("users").document(uid)

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
    }
}

class FirestoreLeaderboardRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val friendsRepository: FriendsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : LeaderboardRepository {

    override suspend fun getLeaderboard(
        scope: LeaderboardScope,
        period: LeaderboardPeriod,
    ): Leaderboard {
        val uid = authRepository.requireUid()
        val periodId = LeaderboardPeriods.periodId(period, clock())
        return when (scope) {
            LeaderboardScope.COMMUNITY -> communityLeaderboard(uid, periodId)
            LeaderboardScope.FRIENDS -> friendsLeaderboard(uid, periodId)
        }
    }

    private suspend fun communityLeaderboard(uid: String, periodId: String): Leaderboard {
        val entries = firestore.collection("leaderboards").document(periodId)
            .collection("entries")
        val top = entries
            .orderBy("carbonSavedGrams", Query.Direction.DESCENDING)
            .limit(COMMUNITY_LIMIT.toLong())
            .get().await()
            .documents.mapNotNull { it.data?.toLeaderboardTotals(it.id) }
        val ranked = rankLeaderboard(top, uid)
        if (ranked.any { it.isCurrentUser }) return Leaderboard(ranked)

        // Outside the top 10 (or opted out): read the user's own position separately.
        val own = entries.document(uid).get().await().data?.toLeaderboardTotals(uid)
            ?: return Leaderboard(ranked)
        val ahead = entries.whereGreaterThan("carbonSavedGrams", own.carbonSavedGrams)
            .count().get(AggregateSource.SERVER).await().count
        return Leaderboard(
            entries = ranked,
            currentUserEntry = LeaderboardEntry(
                rank = (ahead + 1).toInt(),
                uid = uid,
                displayName = own.displayName,
                completedJourneys = own.completedJourneys,
                carbonSavedGrams = own.carbonSavedGrams,
                isCurrentUser = true,
            ),
        )
    }

    private suspend fun friendsLeaderboard(uid: String, periodId: String): Leaderboard =
        coroutineScope {
            val members = friendsRepository.getFriendUids() + uid
            val totals = members.map { memberUid ->
                async {
                    firestore.collection("leaderboardStats").document(memberUid)
                        .get().await().data
                        ?.toFriendTotals(memberUid, periodId)
                        ?: LeaderboardTotals(memberUid, DEFAULT_DISPLAY_NAME, 0.0, 0)
                }
            }.awaitAll()
            Leaderboard(rankLeaderboard(totals, uid))
        }

    private companion object {
        const val COMMUNITY_LIMIT = 10
    }
}

/** Orders by carbon saved, then completed journeys, then name; ranks start at 1. */
internal fun rankLeaderboard(
    totals: List<LeaderboardTotals>,
    currentUid: String,
): List<LeaderboardEntry> =
    totals
        .sortedWith(
            compareByDescending<LeaderboardTotals> { it.carbonSavedGrams }
                .thenByDescending { it.completedJourneys }
                .thenBy { it.displayName },
        )
        .mapIndexed { index, total ->
            LeaderboardEntry(
                rank = index + 1,
                uid = total.uid,
                displayName = total.displayName,
                completedJourneys = total.completedJourneys,
                carbonSavedGrams = total.carbonSavedGrams,
                isCurrentUser = total.uid == currentUid,
            )
        }

class FirestoreRewardsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val functions: CloudFunctionsClient,
) : RewardsRepository {

    override suspend fun getRewards(): List<Reward> {
        authRepository.requireUid()
        return firestore.collection("rewards")
            .whereEqualTo("active", true)
            .get().await()
            .documents.mapNotNull { it.data?.toReward(it.id) }
            .sortedBy { it.pointsRequired }
    }

    override suspend fun getRedemptions(): List<RewardRedemption> {
        val uid = authRepository.requireUid()
        return firestore.collection("users").document(uid)
            .collection("rewardRedemptions")
            .orderBy("redeemedAt", Query.Direction.DESCENDING)
            .get().await()
            .documents.mapNotNull { it.data?.toRewardRedemption(it.id) }
    }

    override suspend fun redeem(rewardId: String, requestId: String): RewardRedemption {
        authRepository.requireUid()
        val response = functions.call(
            "redeemReward",
            mapOf("rewardId" to rewardId, "requestId" to requestId),
        ) as? Map<*, *>
        return (response?.get("redemption") as? Map<*, *>)?.toRewardRedemption()
            ?: throw BackendException("Unexpected response from the server.", "INTERNAL")
    }
}
