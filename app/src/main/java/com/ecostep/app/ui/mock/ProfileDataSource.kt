package com.ecostep.app.ui.mock

import android.content.Context

data class UserProfileUi(
    val userId: String,
    val displayName: String,
    val email: String,
    val friendsCount: Int,
)

data class ProfileImpactSummary(
    val ecoPointsBalance: Int,
    val totalJourneys: Int,
    val carbonSavedKg: Double,
)

data class ProfilePreferences(
    val routineLearningEnabled: Boolean,
    val missionNotificationsEnabled: Boolean,
    val defaultReminderMinutes: Int,
    val automaticJourneyDetectionEnabled: Boolean,
    val communityRankingEnabled: Boolean,
)

data class ProfileData(
    val user: UserProfileUi,
    val impactSummary: ProfileImpactSummary,
    val preferences: ProfilePreferences,
)

enum class RankingScope(
    val displayName: String,
) {
    FRIENDS("Friends"),
    COMMUNITY("Community"),
}

enum class RankingPeriod(
    val displayName: String,
) {
    THIS_WEEK("This Week"),
    ALL_TIME("All Time"),
}

data class CarbonRankingEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val completedJourneys: Int,
    val carbonSavedKg: Double,
    val isCurrentUser: Boolean,
)

enum class FriendRequestStatus {
    NONE,
    ALREADY_FRIENDS,
    REQUEST_SENT,
}

data class FriendSearchResult(
    val userId: String,
    val displayName: String,
    val email: String,
    val requestStatus: FriendRequestStatus,
)

interface ProfileDataSource {

    suspend fun getProfileData(): ProfileData

    suspend fun getCarbonRanking(
        scope: RankingScope,
        period: RankingPeriod,
    ): List<CarbonRankingEntry>

    suspend fun searchUsers(
        query: String,
    ): List<FriendSearchResult>

    suspend fun sendFriendRequest(
        userId: String,
    )

    suspend fun updateDisplayName(
        displayName: String,
    )

    suspend fun updateRoutineLearning(
        enabled: Boolean,
    )

    suspend fun updateMissionNotifications(
        enabled: Boolean,
    )

    suspend fun updateDefaultReminderMinutes(
        minutes: Int,
    )

    suspend fun updateAutomaticJourneyDetection(
        enabled: Boolean,
    )

    suspend fun updateCommunityRankingParticipation(
        enabled: Boolean,
    )
}

/*
 * Temporary data source used to build and demonstrate ProfileScreen.
 *
 * TODO(Profile/Auth):
 * Replace the mock user with the signed-in user supplied by the
 * authentication module.
 *
 * TODO(Profile/Impact):
 * Replace the mock impact summary with the user's shared EcoPoints balance
 * and journey statistics calculated from verified JourneySummary and carbon
 * calculation results.
 *
 * TODO(Profile/Ranking):
 * Replace the mock rankings with aggregated verified carbon-saving results.
 * Rankings must be ordered by carbonSavedKg for the selected period.
 * The community ranking should return only the top 10 users, while the
 * current user's own position may be returned separately if outside the
 * top 10.
 *
 * TODO(Profile/Friends):
 * Replace mock user search and friend requests with persisted user and
 * friendship data after the team agrees on ownership of those records.
 *
 * TODO(Profile/Preferences):
 * Persist profile, reminder, journey-detection and ranking-visibility
 * settings so they can be restored after the app restarts.
 */
class MockProfileDataSource : ProfileDataSource {

    private var displayName =
        "EcoStep User"

    private var routineLearningEnabled =
        true

    private var missionNotificationsEnabled =
        true

    private var defaultReminderMinutes =
        60

    private var automaticJourneyDetectionEnabled =
        true

    private var communityRankingEnabled =
        true

    private val sentFriendRequestUserIds =
        mutableSetOf<String>()

    override suspend fun getProfileData(): ProfileData {
        return ProfileData(
            user = UserProfileUi(
                userId = CURRENT_USER_ID,
                displayName = displayName,
                email = "user@example.com",
                friendsCount = 8,
            ),
            impactSummary = ProfileImpactSummary(
                ecoPointsBalance = 780,
                totalJourneys = 24,
                carbonSavedKg = 18.6,
            ),
            preferences = ProfilePreferences(
                routineLearningEnabled =
                    routineLearningEnabled,
                missionNotificationsEnabled =
                    missionNotificationsEnabled,
                defaultReminderMinutes =
                    defaultReminderMinutes,
                automaticJourneyDetectionEnabled =
                    automaticJourneyDetectionEnabled,
                communityRankingEnabled =
                    communityRankingEnabled,
            ),
        )
    }

    override suspend fun getCarbonRanking(
        scope: RankingScope,
        period: RankingPeriod,
    ): List<CarbonRankingEntry> {
        return when (scope) {
            RankingScope.FRIENDS ->
                getFriendsRanking(period)

            RankingScope.COMMUNITY ->
                getCommunityRanking(period)
                    .take(COMMUNITY_RANKING_LIMIT)
        }
    }

    override suspend fun searchUsers(
        query: String,
    ): List<FriendSearchResult> {
        val normalisedQuery = query.trim()

        if (normalisedQuery.isBlank()) {
            return emptyList()
        }

        return mockSearchableUsers
            .filter { user ->
                user.displayName.contains(
                    normalisedQuery,
                    ignoreCase = true,
                ) ||
                        user.email.contains(
                            normalisedQuery,
                            ignoreCase = true,
                        )
            }
            .map { user ->
                if (
                    user.userId in sentFriendRequestUserIds
                ) {
                    user.copy(
                        requestStatus =
                            FriendRequestStatus.REQUEST_SENT,
                    )
                } else {
                    user
                }
            }
    }

    override suspend fun sendFriendRequest(
        userId: String,
    ) {
        sentFriendRequestUserIds.add(userId)

        /*
         * TODO(Profile/Friends):
         * Persist the request and notify the receiving user through the
         * shared user/friend repository.
         */
    }

    override suspend fun updateDisplayName(
        displayName: String,
    ) {
        val updatedName = displayName.trim()

        if (updatedName.isNotBlank()) {
            this.displayName = updatedName
        }
    }

    override suspend fun updateRoutineLearning(
        enabled: Boolean,
    ) {
        routineLearningEnabled = enabled
    }

    override suspend fun updateMissionNotifications(
        enabled: Boolean,
    ) {
        missionNotificationsEnabled = enabled
    }

    override suspend fun updateDefaultReminderMinutes(
        minutes: Int,
    ) {
        defaultReminderMinutes =
            minutes.coerceAtLeast(0)
    }

    override suspend fun updateAutomaticJourneyDetection(
        enabled: Boolean,
    ) {
        automaticJourneyDetectionEnabled = enabled
    }

    override suspend fun updateCommunityRankingParticipation(
        enabled: Boolean,
    ) {
        communityRankingEnabled = enabled
    }

    private fun getFriendsRanking(
        period: RankingPeriod,
    ): List<CarbonRankingEntry> {
        return when (period) {
            RankingPeriod.THIS_WEEK ->
                listOf(
                    CarbonRankingEntry(
                        rank = 1,
                        userId = "friend-maya",
                        displayName = "Maya",
                        completedJourneys = 7,
                        carbonSavedKg = 4.8,
                        isCurrentUser = false,
                    ),
                    CarbonRankingEntry(
                        rank = 2,
                        userId = CURRENT_USER_ID,
                        displayName = displayName,
                        completedJourneys = 5,
                        carbonSavedKg = 3.6,
                        isCurrentUser = true,
                    ),
                    CarbonRankingEntry(
                        rank = 3,
                        userId = "friend-liam",
                        displayName = "Liam",
                        completedJourneys = 4,
                        carbonSavedKg = 3.1,
                        isCurrentUser = false,
                    ),
                    CarbonRankingEntry(
                        rank = 4,
                        userId = "friend-sophie",
                        displayName = "Sophie",
                        completedJourneys = 4,
                        carbonSavedKg = 2.7,
                        isCurrentUser = false,
                    ),
                )

            RankingPeriod.ALL_TIME ->
                listOf(
                    CarbonRankingEntry(
                        rank = 1,
                        userId = "friend-maya",
                        displayName = "Maya",
                        completedJourneys = 31,
                        carbonSavedKg = 26.4,
                        isCurrentUser = false,
                    ),
                    CarbonRankingEntry(
                        rank = 2,
                        userId = "friend-sophie",
                        displayName = "Sophie",
                        completedJourneys = 28,
                        carbonSavedKg = 22.1,
                        isCurrentUser = false,
                    ),
                    CarbonRankingEntry(
                        rank = 3,
                        userId = CURRENT_USER_ID,
                        displayName = displayName,
                        completedJourneys = 24,
                        carbonSavedKg = 18.6,
                        isCurrentUser = true,
                    ),
                    CarbonRankingEntry(
                        rank = 4,
                        userId = "friend-liam",
                        displayName = "Liam",
                        completedJourneys = 20,
                        carbonSavedKg = 15.9,
                        isCurrentUser = false,
                    ),
                )
        }
    }

    private fun getCommunityRanking(
        period: RankingPeriod,
    ): List<CarbonRankingEntry> {
        return when (period) {
            RankingPeriod.THIS_WEEK ->
                listOf(
                    rankingEntry(1, "community-1", "Amelia", 9, 7.8),
                    rankingEntry(2, "community-2", "Noah", 8, 7.1),
                    rankingEntry(3, "community-3", "Olivia", 8, 6.5),
                    rankingEntry(4, "community-4", "William", 7, 5.9),
                    rankingEntry(5, "community-5", "Ava", 7, 5.4),
                    rankingEntry(6, "community-6", "Leo", 6, 4.9),
                    rankingEntry(7, "community-7", "Isla", 6, 4.2),
                    CarbonRankingEntry(
                        rank = 8,
                        userId = CURRENT_USER_ID,
                        displayName = displayName,
                        completedJourneys = 5,
                        carbonSavedKg = 3.6,
                        isCurrentUser = true,
                    ),
                    rankingEntry(9, "community-9", "Jack", 5, 3.2),
                    rankingEntry(10, "community-10", "Mia", 4, 2.9),
                )

            RankingPeriod.ALL_TIME ->
                listOf(
                    rankingEntry(1, "community-1", "Amelia", 64, 52.8),
                    rankingEntry(2, "community-2", "Noah", 59, 47.3),
                    rankingEntry(3, "community-3", "Olivia", 55, 42.9),
                    rankingEntry(4, "community-4", "William", 49, 37.6),
                    rankingEntry(5, "community-5", "Ava", 43, 32.8),
                    rankingEntry(6, "community-6", "Leo", 38, 27.5),
                    rankingEntry(7, "community-7", "Isla", 31, 23.4),
                    CarbonRankingEntry(
                        rank = 8,
                        userId = CURRENT_USER_ID,
                        displayName = displayName,
                        completedJourneys = 24,
                        carbonSavedKg = 18.6,
                        isCurrentUser = true,
                    ),
                    rankingEntry(9, "community-9", "Jack", 21, 16.9),
                    rankingEntry(10, "community-10", "Mia", 19, 15.2),
                )
        }
    }

    private fun rankingEntry(
        rank: Int,
        userId: String,
        displayName: String,
        completedJourneys: Int,
        carbonSavedKg: Double,
    ): CarbonRankingEntry {
        return CarbonRankingEntry(
            rank = rank,
            userId = userId,
            displayName = displayName,
            completedJourneys = completedJourneys,
            carbonSavedKg = carbonSavedKg,
            isCurrentUser = false,
        )
    }

    private companion object {
        const val CURRENT_USER_ID =
            "mock-user"

        const val COMMUNITY_RANKING_LIMIT =
            10

        val mockSearchableUsers =
            listOf(
                FriendSearchResult(
                    userId = "search-user-emily",
                    displayName = "Emily Chen",
                    email = "emily@example.com",
                    requestStatus =
                        FriendRequestStatus.NONE,
                ),
                FriendSearchResult(
                    userId = "search-user-daniel",
                    displayName = "Daniel Kim",
                    email = "daniel@example.com",
                    requestStatus =
                        FriendRequestStatus.NONE,
                ),
                FriendSearchResult(
                    userId = "friend-maya",
                    displayName = "Maya",
                    email = "maya@example.com",
                    requestStatus =
                        FriendRequestStatus.ALREADY_FRIENDS,
                ),
            )
    }
}