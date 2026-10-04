package com.ecostep.app.ui.adapters

import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.UserStats
import com.ecostep.app.testing.FakeAuthRepository
import com.ecostep.app.testing.FakeEcoPointsRepository
import com.ecostep.app.testing.FakeFriendsRepository
import com.ecostep.app.testing.FakeJourneyRepository
import com.ecostep.app.testing.FakeLeaderboardRepository
import com.ecostep.app.testing.FakeProfileRepository
import com.ecostep.app.testing.testJourney
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryProfileDataSourceTest {

    private val auth = FakeAuthRepository(currentUserId = "user-a")
    private val journeys = FakeJourneyRepository(auth)
    private val stats = FakeEcoPointsRepository()

    private fun source() = RepositoryProfileDataSource(
        authRepository = auth,
        profileRepository = FakeProfileRepository(auth),
        ecoPointsRepository = stats,
        friendsRepository = FakeFriendsRepository(),
        leaderboardRepository = FakeLeaderboardRepository(),
        journeyRepository = journeys,
    )

    private fun confirmed(id: String, mode: TransportMode, carbon: Double? = null) =
        testJourney(journeyId = id, distanceMeters = 2_000.0, transportMode = mode).copy(
            confirmationStatus = JourneyConfirmationStatus.CONFIRMED,
            carbonSavedGrams = carbon,
        )

    @Test
    fun `confirmed journeys show even before the server has counted them`() = runTest {
        journeys.seed("user-a", confirmed("j1", TransportMode.WALKING))
        journeys.seed("user-a", confirmed("j2", TransportMode.PUBLIC_TRANSPORT, carbon = 200.0))
        journeys.seed("user-a", testJourney(journeyId = "pending"))

        val impact = source().getProfileData().impactSummary

        assertEquals(2, impact.totalJourneys)
        // Unverified walk estimated at 384 g plus the verified 200 g.
        assertEquals(0.584, impact.carbonSavedKg, 1e-9)
        assertEquals(0, impact.ecoPointsBalance)
        assertTrue(impact.isAwaitingServer)
    }

    @Test
    fun `server totals are used once they include every journey`() = runTest {
        journeys.seed("user-a", confirmed("j1", TransportMode.WALKING, carbon = 384.0))
        stats.stats = UserStats(pointsBalance = 58, totalJourneys = 1, carbonSavedGrams = 384.0)

        val impact = source().getProfileData().impactSummary

        assertEquals(1, impact.totalJourneys)
        assertEquals(58, impact.ecoPointsBalance)
        assertFalse(impact.isAwaitingServer)
    }
}
