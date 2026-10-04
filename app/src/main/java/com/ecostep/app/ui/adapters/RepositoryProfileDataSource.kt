package com.ecostep.app.ui.adapters

import com.ecostep.app.algorithm.EmissionFactors
import com.ecostep.app.data.model.FriendRequestState
import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.repository.JourneyRepository
import kotlinx.coroutines.CancellationException
import com.ecostep.app.data.model.LeaderboardEntry
import com.ecostep.app.data.model.LeaderboardPeriod
import com.ecostep.app.data.model.LeaderboardScope
import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.UserPreferences
import com.ecostep.app.data.model.UserRelationship
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.EcoPointsRepository
import com.ecostep.app.data.repository.FriendsRepository
import com.ecostep.app.data.repository.LeaderboardRepository
import com.ecostep.app.data.repository.ProfileRepository
import com.ecostep.app.ui.mock.CarbonRankingEntry
import com.ecostep.app.ui.mock.FriendRequestStatus
import com.ecostep.app.ui.mock.FriendSearchResult
import com.ecostep.app.ui.mock.IncomingFriendRequestUi
import com.ecostep.app.ui.mock.ProfileData
import com.ecostep.app.ui.mock.ProfileDataSource
import com.ecostep.app.ui.mock.ProfileImpactSummary
import com.ecostep.app.ui.mock.ProfilePreferences
import com.ecostep.app.ui.mock.RankingPeriod
import com.ecostep.app.ui.mock.RankingScope
import com.ecostep.app.ui.mock.UserProfileUi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Adapts the production repositories to the ProfileScreen's existing data-source contract, so
 * the UI keeps its models while data comes from Firestore and the trusted backend.
 */
class RepositoryProfileDataSource(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val ecoPointsRepository: EcoPointsRepository,
    private val friendsRepository: FriendsRepository,
    private val leaderboardRepository: LeaderboardRepository,
    /** Source of journeys the server has not counted yet; null keeps server totals only. */
    private val journeyRepository: JourneyRepository? = null,
) : ProfileDataSource {

    override suspend fun getProfileData(): ProfileData = coroutineScope {
        val uid = authRepository.currentUserId
            ?: throw IllegalStateException("Sign in to see your profile.")
        val profile = async { profileRepository.getProfile() }
        val stats = async { ecoPointsRepository.getStats() }
        val friends = async { friendsRepository.getFriendUids() }

        val confirmedJourneys = async { confirmedJourneys(uid) }

        val userProfile = profile.await()
        val preferences = userProfile?.preferences ?: UserPreferences()
        val userStats = stats.await()
        val localJourneys = confirmedJourneys.await()
        // The server totals lag behind (or the backend is not deployed): show what this
        // device already knows, with unverified journeys estimated by the shared formula.
        val awaitingServer = localJourneys.size > userStats.totalJourneys
        val totalJourneys = if (awaitingServer) localJourneys.size else userStats.totalJourneys
        val carbonSavedGrams = if (awaitingServer) {
            localJourneys.sumOf { journey ->
                journey.carbonSavedGrams
                    ?: EmissionFactors.carbonSavedVersusCarGrams(journey.distanceMeters, journey.transportMode)
            }
        } else {
            userStats.carbonSavedGrams
        }
        ProfileData(
            user = UserProfileUi(
                userId = uid,
                displayName = userProfile?.displayName
                    ?: authRepository.currentUserEmail?.substringBefore('@')
                    ?: "EcoStep User",
                email = authRepository.currentUserEmail.orEmpty(),
                friendsCount = friends.await().size,
            ),
            impactSummary = ProfileImpactSummary(
                // Points are only ever awarded by the trusted backend.
                ecoPointsBalance = userStats.pointsBalance,
                totalJourneys = totalJourneys,
                carbonSavedKg = carbonSavedGrams / 1000.0,
                isAwaitingServer = awaitingServer,
            ),
            preferences = ProfilePreferences(
                routineLearningEnabled = preferences.routineLearningEnabled,
                missionNotificationsEnabled = preferences.missionNotificationsEnabled,
                defaultReminderMinutes = preferences.defaultReminderMinutes,
                automaticJourneyDetectionEnabled = preferences.automaticJourneyDetectionEnabled,
                communityRankingEnabled = preferences.communityRankingEnabled,
            ),
        )
    }

    /** Confirmed journeys from Firestore (including this device's cache); empty when unavailable. */
    private suspend fun confirmedJourneys(uid: String): List<JourneySummary> {
        val repository = journeyRepository ?: return emptyList()
        return try {
            repository.observeJourneyHistory(uid).first()
                .filter { it.confirmationStatus == JourneyConfirmationStatus.CONFIRMED }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getCarbonRanking(
        scope: RankingScope,
        period: RankingPeriod,
    ): List<CarbonRankingEntry> {
        val leaderboard = leaderboardRepository.getLeaderboard(
            scope = when (scope) {
                RankingScope.FRIENDS -> LeaderboardScope.FRIENDS
                RankingScope.COMMUNITY -> LeaderboardScope.COMMUNITY
            },
            period = when (period) {
                RankingPeriod.THIS_WEEK -> LeaderboardPeriod.THIS_WEEK
                RankingPeriod.ALL_TIME -> LeaderboardPeriod.ALL_TIME
            },
        )
        // The user's own position is appended when it falls outside the top 10.
        return (leaderboard.entries + listOfNotNull(leaderboard.currentUserEntry))
            .map { it.toUi() }
    }

    override suspend fun searchUsers(query: String): List<FriendSearchResult> =
        friendsRepository.searchUsers(query)
            .filter { it.relationship != UserRelationship.SELF }
            .map { result ->
                FriendSearchResult(
                    userId = result.uid,
                    displayName = result.displayName,
                    email = result.maskedEmail.orEmpty(),
                    requestStatus = when (result.relationship) {
                        UserRelationship.FRIEND -> FriendRequestStatus.ALREADY_FRIENDS
                        UserRelationship.REQUEST_SENT -> FriendRequestStatus.REQUEST_SENT
                        UserRelationship.REQUEST_RECEIVED -> FriendRequestStatus.REQUEST_RECEIVED
                        UserRelationship.NONE,
                        UserRelationship.SELF,
                        -> FriendRequestStatus.NONE
                    },
                )
            }

    override suspend fun sendFriendRequest(userId: String) {
        friendsRepository.sendFriendRequest(userId)
    }

    override suspend fun getIncomingFriendRequests(): List<IncomingFriendRequestUi> {
        val uid = authRepository.currentUserId ?: return emptyList()
        return friendsRepository.observeFriendRequests().first()
            .filter { it.receiverUid == uid && it.status == FriendRequestState.PENDING }
            .map { IncomingFriendRequestUi(it.requestId, it.senderDisplayName) }
    }

    override suspend fun respondToFriendRequest(requestId: String, accept: Boolean) {
        friendsRepository.respondToFriendRequest(requestId, accept)
    }

    override suspend fun updateDisplayName(displayName: String) {
        profileRepository.updateDisplayName(displayName)
    }

    override suspend fun updateRoutineLearning(enabled: Boolean) {
        profileRepository.updatePreference(PreferenceUpdate.RoutineLearning(enabled))
    }

    override suspend fun updateMissionNotifications(enabled: Boolean) {
        profileRepository.updatePreference(PreferenceUpdate.MissionNotifications(enabled))
    }

    override suspend fun updateDefaultReminderMinutes(minutes: Int) {
        profileRepository.updatePreference(PreferenceUpdate.DefaultReminderMinutes(minutes))
    }

    override suspend fun updateAutomaticJourneyDetection(enabled: Boolean) {
        profileRepository.updatePreference(PreferenceUpdate.AutomaticJourneyDetection(enabled))
    }

    override suspend fun updateCommunityRankingParticipation(enabled: Boolean) {
        profileRepository.updatePreference(PreferenceUpdate.CommunityRanking(enabled))
    }
}

private fun LeaderboardEntry.toUi() = CarbonRankingEntry(
    rank = rank,
    userId = uid,
    displayName = displayName,
    completedJourneys = completedJourneys,
    carbonSavedKg = carbonSavedGrams / 1000.0,
    isCurrentUser = isCurrentUser,
)
