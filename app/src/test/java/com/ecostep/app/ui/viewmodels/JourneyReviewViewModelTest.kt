package com.ecostep.app.ui.viewmodels

import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.testing.FakeAuthRepository
import com.ecostep.app.testing.FakeJourneyRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.testing.testJourney
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class JourneyReviewViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(currentUserId = "user-a")
    private val repository = FakeJourneyRepository(auth)

    @Test
    fun `missing journey shows not found`() {
        val viewModel = JourneyReviewViewModel(repository, "nope")

        assertEquals("Journey not found.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `estimate uses car baseline and awards no points without a mission`() {
        repository.seed("user-a", testJourney(distanceMeters = 2_000.0, transportMode = TransportMode.CAR))
        val viewModel = JourneyReviewViewModel(repository, "j1")

        assertEquals(0.0, viewModel.uiState.value.carbonSavedKg, 1e-9)

        viewModel.selectTransportMode(TransportMode.PUBLIC_TRANSPORT)

        // (192 - 89) g/km * 2 km
        assertEquals(0.206, viewModel.uiState.value.carbonSavedKg, 1e-9)
        assertEquals(0, viewModel.uiState.value.ecoPoints)
        assertFalse(viewModel.uiState.value.isImpactVerified)
    }

    @Test
    fun `car journey lists lower-carbon alternatives, largest saving first`() {
        repository.seed("user-a", testJourney(distanceMeters = 2_000.0, transportMode = TransportMode.CAR))
        val viewModel = JourneyReviewViewModel(repository, "j1")

        val state = viewModel.uiState.value
        assertEquals(384.0, state.emissionsGrams, 1e-9)
        assertEquals(
            listOf(TransportMode.WALKING, TransportMode.CYCLING, TransportMode.PUBLIC_TRANSPORT),
            state.lowerCarbonAlternatives.map { it.mode },
        )
        assertEquals(206.0, state.lowerCarbonAlternatives.last().savingsGrams, 1e-9)
    }

    @Test
    fun `alternatives follow the selected mode and vanish for zero-emission modes`() {
        repository.seed("user-a", testJourney(distanceMeters = 2_000.0, transportMode = TransportMode.CAR))
        val viewModel = JourneyReviewViewModel(repository, "j1")

        viewModel.selectTransportMode(TransportMode.PUBLIC_TRANSPORT)
        assertEquals(
            setOf(TransportMode.WALKING, TransportMode.CYCLING),
            viewModel.uiState.value.lowerCarbonAlternatives.map { it.mode }.toSet(),
        )

        viewModel.selectTransportMode(TransportMode.WALKING)
        assertTrue(viewModel.uiState.value.lowerCarbonAlternatives.isEmpty())
        assertEquals(0.0, viewModel.uiState.value.emissionsGrams, 1e-9)
    }

    @Test
    fun `mission-linked journey estimates points with the production calculator`() {
        repository.seed("user-a", testJourney(distanceMeters = 2_000.0, linkedMissionId = "m1"))
        val viewModel = JourneyReviewViewModel(repository, "j1")

        viewModel.selectTransportMode(TransportMode.WALKING)

        // 384 g saved -> 38 base + 20 walking bonus
        assertEquals(58, viewModel.uiState.value.ecoPoints)
    }

    @Test
    fun `backend values replace the estimate once the confirmed journey is processed`() {
        repository.seed("user-a", testJourney(transportMode = TransportMode.CYCLING))
        val viewModel = JourneyReviewViewModel(repository, "j1")
        viewModel.saveJourney()

        repository.applyBackendResult("user-a", "j1", carbonSavedGrams = 1_234.0, ecoPoints = 77)

        assertEquals(1.234, viewModel.uiState.value.carbonSavedKg, 1e-9)
        assertEquals(77, viewModel.uiState.value.ecoPoints)
        assertTrue(viewModel.uiState.value.isImpactVerified)
    }

    @Test
    fun `repeating the same confirmation is an idempotent update`() {
        repository.seed("user-a", testJourney())
        val viewModel = JourneyReviewViewModel(repository, "j1")

        viewModel.selectTransportMode(TransportMode.WALKING)
        viewModel.saveJourney()
        val first = repository.store("j1")
        viewModel.dismissConfirmation()
        viewModel.saveJourney()

        assertEquals(first, repository.store("j1"))
        assertEquals(2, repository.confirmCalls)
    }

    @Test
    fun `double tapping confirm sends one request while saving`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowRepository = object : com.ecostep.app.data.repository.JourneyRepository by repository {
            override suspend fun confirmTransportMode(
                journeyId: String,
                mode: TransportMode,
            ): com.ecostep.app.data.repository.WriteOutcome {
                gate.await()
                return repository.confirmTransportMode(journeyId, mode)
            }
        }
        repository.seed("user-a", testJourney())
        val viewModel = JourneyReviewViewModel(slowRepository, "j1")

        viewModel.saveJourney()
        viewModel.saveJourney()
        assertTrue(viewModel.uiState.value.isSaving)
        gate.complete(Unit)

        assertEquals(1, repository.confirmCalls)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `UNKNOWN cannot be confirmed`() {
        repository.seed("user-a", testJourney(transportMode = TransportMode.UNKNOWN))
        val viewModel = JourneyReviewViewModel(repository, "j1")

        viewModel.saveJourney()

        assertEquals(0, repository.confirmCalls)
        assertNotNull(viewModel.uiState.value.saveErrorMessage)
    }

    @Test
    fun `save failure keeps the journey on screen with a retryable error`() {
        repository.seed("user-a", testJourney())
        val viewModel = JourneyReviewViewModel(repository, "j1")
        repository.failNextWrite = IllegalStateException("PERMISSION_DENIED")

        viewModel.saveJourney()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("PERMISSION_DENIED", viewModel.uiState.value.saveErrorMessage)
        assertFalse(viewModel.uiState.value.isSaved)

        viewModel.saveJourney()
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `confirmation hook receives the confirmed journey`() {
        repository.seed("user-a", testJourney(linkedMissionId = "m1"))
        var confirmed: JourneySummary? = null
        val viewModel = JourneyReviewViewModel(repository, "j1", onJourneyConfirmed = { confirmed = it })

        viewModel.selectTransportMode(TransportMode.WALKING)
        viewModel.saveJourney()

        assertEquals(TransportMode.WALKING, confirmed?.confirmedTransportMode)
        assertEquals("m1", confirmed?.linkedMissionId)
    }

    private fun FakeJourneyRepository.store(id: String) =
        kotlinx.coroutines.runBlocking { getJourney(id) }
}
