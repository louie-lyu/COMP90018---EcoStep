package com.ecostep.app.testing

import com.ecostep.app.data.model.FriendRequest
import com.ecostep.app.data.model.Leaderboard
import com.ecostep.app.data.model.LeaderboardPeriod
import com.ecostep.app.data.model.LeaderboardScope
import com.ecostep.app.data.model.PointTransaction
import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.UserProfile
import com.ecostep.app.data.model.UserSearchResult
import com.ecostep.app.data.model.UserStats
import com.ecostep.app.data.model.normalizeDisplayName
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.EcoPointsRepository
import com.ecostep.app.data.repository.FriendsRepository
import com.ecostep.app.data.repository.LeaderboardRepository
import com.ecostep.app.data.repository.ProfileRepository
import com.ecostep.app.data.repository.WriteOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Profiles keyed by UID; every call is scoped to the signed-in user, like Firestore. */
class FakeProfileRepository(private val auth: AuthRepository) : ProfileRepository {
    val profiles = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    var failNextWrite: Exception? = null
    var ensureCalls = 0
        private set

    private fun uid() = auth.currentUserId ?: throw IllegalStateException("signed out")

    override fun observeProfile(): Flow<UserProfile?> {
        val uid = uid()
        return profiles.map { it[uid] }
    }

    override suspend fun getProfile(): UserProfile? = profiles.value[uid()]

    override suspend fun ensureProfile(defaultDisplayName: String): Boolean {
        ensureCalls++
        val uid = uid()
        if (profiles.value.containsKey(uid)) return false
        profiles.value = profiles.value + (uid to UserProfile(uid, defaultDisplayName))
        return true
    }

    override suspend fun updateDisplayName(displayName: String): WriteOutcome {
        failNextWrite?.let { failNextWrite = null; throw it }
        val name = normalizeDisplayName(displayName)
        edit { it.copy(displayName = name) }
        return WriteOutcome.SYNCED
    }

    override suspend fun updatePreference(update: PreferenceUpdate): WriteOutcome {
        failNextWrite?.let { failNextWrite = null; throw it }
        edit { profile ->
            val p = profile.preferences
            profile.copy(
                preferences = when (update) {
                    is PreferenceUpdate.RoutineLearning -> p.copy(routineLearningEnabled = update.enabled)
                    is PreferenceUpdate.MissionNotifications -> p.copy(missionNotificationsEnabled = update.enabled)
                    is PreferenceUpdate.DefaultReminderMinutes -> p.copy(defaultReminderMinutes = update.minutes)
                    is PreferenceUpdate.AutomaticJourneyDetection -> p.copy(automaticJourneyDetectionEnabled = update.enabled)
                    is PreferenceUpdate.CommunityRanking -> p.copy(communityRankingEnabled = update.enabled)
                },
            )
        }
        return WriteOutcome.SYNCED
    }

    private fun edit(change: (UserProfile) -> UserProfile) {
        val uid = uid()
        val current = profiles.value[uid] ?: UserProfile(uid, "EcoStep User")
        profiles.value = profiles.value + (uid to change(current))
    }
}

class FakeEcoPointsRepository(var stats: UserStats = UserStats()) : EcoPointsRepository {
    override fun observeStats(): Flow<UserStats> = flowOf(stats)
    override suspend fun getStats(): UserStats = stats
    override fun observeTransactions(limit: Int): Flow<List<PointTransaction>> = flowOf(emptyList())
}

class FakeFriendsRepository : FriendsRepository {
    var friendUids: Set<String> = emptySet()
    var requests: List<FriendRequest> = emptyList()
    var searchResults: List<UserSearchResult> = emptyList()
    val sent = mutableListOf<String>()
    val responses = mutableListOf<Pair<String, Boolean>>()

    override suspend fun getFriendUids(): Set<String> = friendUids
    override fun observeFriendRequests(): Flow<List<FriendRequest>> = flowOf(requests)
    override suspend fun searchUsers(query: String): List<UserSearchResult> = searchResults
    override suspend fun sendFriendRequest(receiverUid: String) {
        sent += receiverUid
    }

    override suspend fun respondToFriendRequest(requestId: String, accept: Boolean) {
        responses += requestId to accept
    }

    override suspend fun cancelFriendRequest(requestId: String) = Unit
}

class FakeLeaderboardRepository(var leaderboard: Leaderboard = Leaderboard(emptyList())) : LeaderboardRepository {
    val requests = mutableListOf<Pair<LeaderboardScope, LeaderboardPeriod>>()
    override suspend fun getLeaderboard(scope: LeaderboardScope, period: LeaderboardPeriod): Leaderboard {
        requests += scope to period
        return leaderboard
    }
}
