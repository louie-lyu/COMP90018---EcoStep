package com.ecostep.app.ui.viewmodels

import com.ecostep.app.algorithm.DefaultWeeklyCoach
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.mock.WeeklyInsightDataSource
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WeeklyInsightRetryTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `load failure can be retried without overlapping requests`() {
        var calls = 0
        val results = CompletableDeferred<List<MissionResult>>()
        val source = object : WeeklyInsightDataSource {
            override suspend fun getMissionResults(fromMillis: Long): List<MissionResult> {
                calls++
                if (calls == 1) throw IllegalStateException("Offline")
                return results.await()
            }
        }
        val viewModel = WeeklyInsightViewModel(DefaultWeeklyCoach(), source)
        assertEquals("Offline", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        viewModel.retry()
        viewModel.retry()
        assertEquals(2, calls)
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        results.complete(emptyList())
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.report)
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
