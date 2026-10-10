package com.ecostep.app.ui.viewmodels

import com.ecostep.app.core.integration.MissionRouteEstimate
import com.ecostep.app.core.integration.MissionRouteException
import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionModeEstimate
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RecurrenceType
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.WriteOutcome
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.adapters.toPageItem
import com.ecostep.app.ui.adapters.toMission
import com.ecostep.app.ui.adapters.withEditsFrom
import com.ecostep.app.ui.mock.MissionPageItem
import com.ecostep.app.ui.mock.MissionRepository
import com.ecostep.app.ui.mock.MissionRepositoryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MissionEditEstimateTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val stored = Mission(
        missionId = "m1",
        title = "Home → Uni",
        startLabel = "Home street",
        destinationLabel = "Uni",
        targetTransportMode = TransportMode.WALKING,
        targetDistanceMeters = 800.0,
        status = MissionStatus.ACCEPTED,
        recurrence = MissionRecurrence(type = RecurrenceType.DAILY, timezone = "UTC"),
        estimates = listOf(MissionModeEstimate(TransportMode.WALKING, 31, 153.6)),
    )

    /** Applies edits the way RepositoryMissionStore does, so the stored Mission can be checked. */
    private class Store(initial: Mission) : MissionRepository {
        var mission = initial
        override val state: StateFlow<MissionRepositoryState> =
            MutableStateFlow(MissionRepositoryState(upcomingMissions = listOf(initial.toPageItem())))

        override fun updateMission(updatedMission: MissionPageItem) {
            mission = mission.withEditsFrom(updatedMission)
        }

        override suspend fun createMission(mission: MissionPageItem): WriteOutcome {
            this.mission = mission.toMission(MissionStatus.ACCEPTED, "UTC")
            return WriteOutcome.SYNCED
        }

        override fun acceptSuggestedMission() = Unit
        override fun dismissSuggestedMission() = Unit
        override fun startMission(missionId: String) = Unit
        override fun skipMissionToday(missionId: String) = Unit
        override fun endActiveMission() = Unit
    }

    private val cbdEstimate = MissionRouteEstimate(
        startText = "Home street",
        destinationText = "CBD",
        distanceByMode = mapOf(TransportMode.WALKING to 2_000.0, TransportMode.CAR to 2_500.0),
        estimates = listOf(
            MissionModeEstimate(TransportMode.WALKING, 58, 384.0),
            MissionModeEstimate(TransportMode.CAR, 0, 0.0),
        ),
    )

    private var estimateCalls = 0

    private fun viewModel(store: Store, fail: Boolean = false) = MissionViewModel(
        missionRepository = store,
        routeEstimator = { start, destination ->
            estimateCalls++
            if (fail) throw MissionRouteException("Could not find the destination. Try a street address.")
            cbdEstimate.copy(startText = start, destinationText = destination)
        },
    )

    private fun edited(destination: String) = stored.toPageItem().let {
        it.copy(destination = destination, mission = it.mission.copy(routeTitle = "Home street → $destination"))
    }

    @Test
    fun `creating a mission saves the ready route estimate without another request`() {
        val store = Store(stored)
        val viewModel = viewModel(store)
        viewModel.estimateRoute("Home street", "CBD")

        viewModel.createMission(edited("CBD"))

        assertEquals(1, estimateCalls)
        assertEquals("CBD", store.mission.destinationLabel)
        assertEquals(2000.0, store.mission.targetDistanceMeters!!, 0.0)
        assertEquals(58, store.mission.estimates.first().estimatedEcoPoints)
    }

    @Test
    fun `typing a new route shows recalculated values`() {
        val viewModel = viewModel(Store(stored))

        viewModel.estimateRoute("Home street", "CBD")

        val ready = viewModel.routeEstimate.value as RouteEstimateState.Ready
        assertEquals(58, ready.estimate.estimates.first().estimatedEcoPoints)
    }

    @Test
    fun `saving a new route stores its estimates and distance`() {
        val store = Store(stored)
        val viewModel = viewModel(store)
        viewModel.estimateRoute("Home street", "CBD")

        viewModel.updateMission(edited("CBD"))

        assertEquals(1, estimateCalls)
        assertEquals("CBD", store.mission.destinationLabel)
        assertEquals(2_000.0, store.mission.targetDistanceMeters!!, 1e-9)
        val walking = store.mission.estimates.single { it.mode == TransportMode.WALKING }
        assertEquals(58, walking.estimatedEcoPoints)
        assertEquals(384.0, walking.estimatedCarbonSavedGrams, 1e-9)
    }

    @Test
    fun `saving before the estimate finished still recalculates`() {
        val store = Store(stored)

        viewModel(store).updateMission(edited("CBD"))

        assertEquals(1, estimateCalls)
        assertEquals(58, store.mission.estimates.single { it.mode == TransportMode.WALKING }.estimatedEcoPoints)
    }

    @Test
    fun `an unchanged route keeps the stored estimates`() {
        val store = Store(stored)

        viewModel(store).updateMission(edited("Uni"))

        assertEquals(0, estimateCalls)
        assertEquals(31, store.mission.estimates.single().estimatedEcoPoints)
        assertEquals(800.0, store.mission.targetDistanceMeters!!, 1e-9)
    }

    @Test
    fun `a place that cannot be found is reported and old values are kept`() {
        val store = Store(stored)
        val viewModel = viewModel(store, fail = true)

        viewModel.estimateRoute("Home street", "Work")
        val failed = viewModel.routeEstimate.value as RouteEstimateState.Failed
        assertTrue(failed.message.contains("destination"))

        viewModel.updateMission(edited("Work"))

        assertEquals("Work", store.mission.destinationLabel)
        assertEquals(31, store.mission.estimates.single().estimatedEcoPoints)
    }
}
