package com.ecostep.app.ui.viewmodels

import com.ecostep.app.testing.FakeAuthRepository
import com.ecostep.app.testing.FakeJourneyRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.testing.testJourney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JourneyHistoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(currentUserId = "user-a")
    private val repository = FakeJourneyRepository(auth)

    @Test
    fun `signed-out user gets an explicit sign-in state, not mock data`() {
        auth.currentUserId = null

        val state = JourneyHistoryViewModel(repository, currentUserId = null).uiState.value

        assertTrue(state.requiresSignIn)
        assertFalse(state.isLoading)
        assertTrue(state.journeys.isEmpty())
    }

    @Test
    fun `only the signed-in user's journeys are listed, newest first`() {
        repository.seed("user-a", testJourney(journeyId = "a-old", startTimeMillis = 1_000_000L))
        repository.seed("user-a", testJourney(journeyId = "a-new", startTimeMillis = 9_000_000L))
        repository.seed("user-b", testJourney(journeyId = "b-1"))

        val state = JourneyHistoryViewModel(repository, "user-a").uiState.value

        assertEquals(listOf("a-new", "a-old"), state.journeys.map { it.journeyId })
    }

    @Test
    fun `requesting another user's history fails instead of leaking it`() {
        repository.seed("user-b", testJourney(journeyId = "b-1"))

        val state = JourneyHistoryViewModel(repository, "user-b").uiState.value

        assertTrue(state.journeys.isEmpty())
        assertEquals("Cannot read another user's journeys.", state.errorMessage)
    }
}
