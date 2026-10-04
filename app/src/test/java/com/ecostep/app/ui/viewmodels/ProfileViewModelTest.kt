package com.ecostep.app.ui.viewmodels

import com.ecostep.app.data.model.FriendRequest
import com.ecostep.app.data.model.FriendRequestState
import com.ecostep.app.data.model.Leaderboard
import com.ecostep.app.data.model.LeaderboardEntry
import com.ecostep.app.data.model.LeaderboardPeriod
import com.ecostep.app.data.model.LeaderboardScope
import com.ecostep.app.data.model.UserProfile
import com.ecostep.app.data.model.UserRelationship
import com.ecostep.app.data.model.UserSearchResult
import com.ecostep.app.data.model.UserStats
import com.ecostep.app.testing.FakeAuthRepository
import com.ecostep.app.testing.FakeEcoPointsRepository
import com.ecostep.app.testing.FakeFriendsRepository
import com.ecostep.app.testing.FakeLeaderboardRepository
import com.ecostep.app.testing.FakeProfileRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.adapters.RepositoryProfileDataSource
import com.ecostep.app.ui.mock.FriendRequestStatus
import com.ecostep.app.ui.mock.RankingPeriod
import com.ecostep.app.ui.mock.RankingScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(currentUserId = "user-a", currentUserEmail = "ada@example.com")
    private val profiles = FakeProfileRepository(auth)
    private val points = FakeEcoPointsRepository(
        UserStats(pointsBalance = 420, totalJourneys = 7, carbonSavedGrams = 3_250.0),
    )
    private val friends = FakeFriendsRepository()
    private val leaderboard = FakeLeaderboardRepository()

    private fun viewModel() = ProfileViewModel(
        RepositoryProfileDataSource(auth, profiles, points, friends, leaderboard),
    )

    @Test
    fun `profile combines Firestore profile, backend stats and Auth email`() {
        profiles.profiles.value = mapOf("user-a" to UserProfile("user-a", "Ada"))
        friends.friendUids = setOf("f1", "f2")

        val data = viewModel().uiState.value.profileData!!

        assertEquals("Ada", data.user.displayName)
        assertEquals("ada@example.com", data.user.email)
        assertEquals(2, data.user.friendsCount)
        assertEquals(420, data.impactSummary.ecoPointsBalance)
        assertEquals(7, data.impactSummary.totalJourneys)
        assertEquals(3.25, data.impactSummary.carbonSavedKg, 1e-9)
    }

    @Test
    fun `preference change persists and survives a reload`() {
        val first = viewModel()

        first.setCommunityRankingEnabled(false)
        first.setDefaultReminderMinutes(15)

        val reloaded = viewModel().uiState.value.profileData!!.preferences
        assertFalse(reloaded.communityRankingEnabled)
        assertEquals(15, reloaded.defaultReminderMinutes)
        assertNull(first.uiState.value.errorMessage)
    }

    @Test
    fun `failed preference write rolls back the optimistic change`() {
        val viewModel = viewModel()
        profiles.failNextWrite = IllegalStateException("PERMISSION_DENIED")

        viewModel.setMissionNotificationsEnabled(false)

        assertTrue(viewModel.uiState.value.profileData!!.preferences.missionNotificationsEnabled)
        assertEquals("PERMISSION_DENIED", viewModel.uiState.value.errorMessage)
        assertTrue(profiles.profiles.value["user-a"]?.preferences?.missionNotificationsEnabled ?: true)
    }

    @Test
    fun `display name update is stored for the signed-in user only`() {
        profiles.profiles.value = mapOf(
            "user-a" to UserProfile("user-a", "Ada"),
            "user-b" to UserProfile("user-b", "Bob"),
        )
        val viewModel = viewModel()

        viewModel.updateDisplayName("  Ada L ")

        assertEquals("Ada L", profiles.profiles.value.getValue("user-a").displayName)
        assertEquals("Bob", profiles.profiles.value.getValue("user-b").displayName)
    }

    @Test
    fun `own community position outside the top 10 is appended`() {
        leaderboard.leaderboard = Leaderboard(
            entries = (1..10).map { LeaderboardEntry(it, "u$it", "U$it", 1, 1_000.0 * (11 - it), false) },
            currentUserEntry = LeaderboardEntry(23, "user-a", "Ada", 2, 120.0, true),
        )
        val viewModel = viewModel()

        viewModel.selectRankingScope(RankingScope.COMMUNITY)
        viewModel.selectRankingPeriod(RankingPeriod.ALL_TIME)

        val entries = viewModel.uiState.value.rankingEntries
        assertEquals(11, entries.size)
        assertEquals(23, entries.last().rank)
        assertTrue(entries.last().isCurrentUser)
        assertEquals(LeaderboardScope.COMMUNITY to LeaderboardPeriod.ALL_TIME, leaderboard.requests.last())
    }

    @Test
    fun `search hides self and maps relationship states`() {
        friends.searchResults = listOf(
            UserSearchResult("user-a", "Me", null, UserRelationship.SELF),
            UserSearchResult("u2", "Friend", "f***@example.com", UserRelationship.FRIEND),
            UserSearchResult("u3", "Asker", null, UserRelationship.REQUEST_RECEIVED),
            UserSearchResult("u4", "New", null, UserRelationship.NONE),
        )
        val viewModel = viewModel()

        viewModel.updateFriendSearchQuery("ex")
        viewModel.searchFriends()
        viewModel.sendFriendRequest("u4")
        viewModel.sendFriendRequest("u2")

        val results = viewModel.uiState.value.friendSearchResults
        assertEquals(listOf("u2", "u3", "u4"), results.map { it.userId })
        assertEquals(FriendRequestStatus.ALREADY_FRIENDS, results[0].requestStatus)
        assertEquals(FriendRequestStatus.REQUEST_RECEIVED, results[1].requestStatus)
        assertEquals(FriendRequestStatus.REQUEST_SENT, results[2].requestStatus)
        assertEquals(listOf("u4"), friends.sent)
    }

    @Test
    fun `incoming requests are listed and answered through the backend`() {
        friends.requests = listOf(
            FriendRequest("r1", "u9", "user-a", "Zoe", "Ada", FriendRequestState.PENDING, 1L, 1L),
            FriendRequest("r2", "user-a", "u8", "Ada", "Yan", FriendRequestState.PENDING, 1L, 1L),
        )
        val viewModel = viewModel()

        assertEquals(listOf("r1"), viewModel.uiState.value.incomingFriendRequests.map { it.requestId })

        viewModel.respondToFriendRequest("r1", accept = true)

        assertEquals(listOf("r1" to true), friends.responses)
    }
}
