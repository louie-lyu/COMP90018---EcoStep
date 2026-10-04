package com.ecostep.app.ui.viewmodels

import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.WriteOutcome
import com.ecostep.app.sensors.tracking.RecordedJourneySaver
import com.ecostep.app.testing.FakeAuthRepository
import com.ecostep.app.testing.FakeJourneyRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.testing.testJourney
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tracking -> Review -> History all share one repository instance, as in AppContainer. */
class JourneyLoopTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(currentUserId = "user-a")
    private val repository = FakeJourneyRepository(auth)

    @Test
    fun `tracked journey opens in review and the confirmed mode reaches history`() = runTest {
        // Tracking: classifier said PUBLIC_TRANSPORT.
        val saved = RecordedJourneySaver(repository).save(
            testJourney(journeyId = "tracked-1", transportMode = TransportMode.PUBLIC_TRANSPORT),
        )
        assertEquals(WriteOutcome.SYNCED, saved.outcome)

        // Review reads the same record by the ID Tracking navigated with.
        val review = JourneyReviewViewModel(repository, saved.journeyId)
        assertEquals(TransportMode.PUBLIC_TRANSPORT, review.uiState.value.detectedMode)
        assertEquals(TransportMode.PUBLIC_TRANSPORT, review.uiState.value.selectedMode)
        assertFalse(review.uiState.value.isConfirmed)

        review.selectTransportMode(TransportMode.CYCLING)
        review.saveJourney()

        val stored = repository.getJourney("tracked-1")!!
        assertEquals(TransportMode.PUBLIC_TRANSPORT, stored.detectedTransportMode)
        assertEquals(TransportMode.CYCLING, stored.confirmedTransportMode)
        assertEquals(TransportMode.CYCLING, stored.transportMode)
        assertEquals(JourneyConfirmationStatus.CONFIRMED, stored.confirmationStatus)
        assertTrue(review.uiState.value.isSaved)
        assertTrue(review.uiState.value.isSynced)

        // History, created with the signed-in UID, shows the updated record.
        val history = JourneyHistoryViewModel(repository, auth.currentUserId)
        val listed = history.uiState.value.journeys.single()
        assertEquals("tracked-1", listed.journeyId)
        assertEquals(TransportMode.CYCLING, listed.transportMode)
    }

    @Test
    fun `offline journey is readable immediately and marked pending until sync`() = runTest {
        repository.online = false
        val saved = RecordedJourneySaver(repository).save(testJourney(journeyId = "offline-1"))
        assertEquals(WriteOutcome.QUEUED, saved.outcome)

        val review = JourneyReviewViewModel(repository, "offline-1")
        val history = JourneyHistoryViewModel(repository, "user-a")
        assertNull(review.uiState.value.errorMessage)
        assertTrue(review.uiState.value.isPendingSync)
        assertEquals(setOf("offline-1"), history.uiState.value.pendingSyncJourneyIds)

        review.selectTransportMode(TransportMode.WALKING)
        review.saveJourney()
        assertTrue(review.uiState.value.isSaved)
        assertTrue(review.uiState.value.isPendingSync)
        assertFalse(review.uiState.value.isSynced)

        repository.goOnline()

        assertFalse(review.uiState.value.isPendingSync)
        assertTrue(review.uiState.value.isSynced)
        assertTrue(history.uiState.value.pendingSyncJourneyIds.isEmpty())
        assertEquals(TransportMode.WALKING, history.uiState.value.journeys.single().transportMode)
    }

    @Test
    fun `active mission is linked when the journey is saved`() = runTest {
        RecordedJourneySaver(repository) { "mission-7" }.save(testJourney(journeyId = "linked"))

        assertEquals("mission-7", repository.getJourney("linked")!!.linkedMissionId)
    }
}
