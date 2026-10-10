package com.ecostep.app.ui.viewmodels

import com.ecostep.app.data.repository.WriteOutcome
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.mock.MissionPageItem
import com.ecostep.app.ui.mock.MissionRepository
import com.ecostep.app.ui.mock.MockMissionRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MissionCreationTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `failed creation retains the ID for retry and concurrent saves are ignored`() {
        val storage = MockMissionRepository()
        val draft = storage.state.value.upcomingMissions.first().let {
            it.copy(mission = it.mission.copy(missionId = "manual-draft"))
        }
        var result = CompletableDeferred<WriteOutcome>()
        val ids = mutableListOf<String>()
        val repository = object : MissionRepository by storage {
            override suspend fun createMission(mission: MissionPageItem): WriteOutcome {
                ids += mission.mission.missionId
                val outcome = result.await()
                storage.createMission(mission)
                return outcome
            }
        }
        val viewModel = MissionViewModel(repository)
        viewModel.createMission(draft)
        viewModel.createMission(draft)
        assertTrue(viewModel.creationState.value.isCreating)
        assertEquals(listOf("manual-draft"), ids)
        result.completeExceptionally(IllegalStateException("Save failed"))
        assertEquals("Save failed", viewModel.creationState.value.createError)
        assertFalse(viewModel.creationState.value.isCreating)
        assertNull(viewModel.creationState.value.createdMissionId)

        result = CompletableDeferred()
        viewModel.createMission(draft)
        result.complete(WriteOutcome.QUEUED)
        assertEquals(listOf("manual-draft", "manual-draft"), ids)
        assertEquals("manual-draft", viewModel.creationState.value.createdMissionId)
        assertNull(viewModel.creationState.value.createError)
        assertEquals(1, storage.state.value.upcomingMissions.count { it.mission.missionId == "manual-draft" })
    }
}
