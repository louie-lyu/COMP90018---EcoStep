package com.ecostep.app.ui.viewmodels

import com.ecostep.app.algorithm.DefaultAiWeeklyCoach
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.network.ai.AiGateway
import com.ecostep.app.network.ai.AiMissionSuggestion
import com.ecostep.app.network.ai.AiWeeklyAdvice
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.mock.WeeklyInsightDataSource
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeeklyInsightViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeAi(private val advice: AiWeeklyAdvice?) : AiGateway {
        override suspend fun generateMission(prompt: String): AiMissionSuggestion =
            throw UnsupportedOperationException()

        override suspend fun generateWeeklyAdvice(prompt: String): AiWeeklyAdvice =
            advice ?: throw IOException("AI unavailable")
    }

    private val completedRide = MissionResult(
        missionId = "m1",
        accepted = true,
        completed = true,
        actualTransportMode = TransportMode.CYCLING,
        actualCarbonSavingGrams = 520.0,
        timestampMillis = System.currentTimeMillis(),
    )

    private fun dataSource(results: List<MissionResult>? = listOf(completedRide)) =
        object : WeeklyInsightDataSource {
            override suspend fun getMissionResults(fromMillis: Long): List<MissionResult> =
                results ?: throw IllegalStateException("Firestore unavailable")
        }

    private fun viewModel(advice: AiWeeklyAdvice?, results: List<MissionResult>? = listOf(completedRide)) =
        WeeklyInsightViewModel(
            coaching = DefaultAiWeeklyCoach(FakeAi(advice))::generate,
            dataSource = dataSource(results),
        )

    @Test
    fun `AI advice replaces the local text`() {
        val state = viewModel(AiWeeklyAdvice("You rode a lot.", "Try walking on Friday.")).uiState.value

        assertEquals("You rode a lot.", state.insight)
        assertEquals("Try walking on Friday.", state.action)
        assertTrue(state.usedAi)
        assertEquals(1, state.report?.completedMissions)
    }

    @Test
    fun `AI failure shows the local fallback, not an error`() {
        val state = viewModel(advice = null).uiState.value

        assertFalse(state.usedAi)
        assertEquals(state.report?.summary, state.insight)
        assertEquals(state.report?.fallbackMessage, state.action)
        assertNull(state.errorMessage)
    }

    @Test
    fun `only a data failure is an error`() {
        val state = viewModel(advice = null, results = null).uiState.value

        assertEquals("Firestore unavailable", state.errorMessage)
        assertFalse(state.isLoading)
    }
}
