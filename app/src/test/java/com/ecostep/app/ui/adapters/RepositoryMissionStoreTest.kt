package com.ecostep.app.ui.adapters

import com.ecostep.app.algorithm.DefaultWeeklyCoach
import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionOccurrence
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RecurrenceType
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.testing.FakeMissionRepository
import com.ecostep.app.testing.FakeMissionResultRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.mock.MockMissionScreenDataSource
import com.ecostep.app.ui.viewmodels.WeeklyInsightViewModel
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryMissionStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Monday 2026-10-05, 09:00 UTC.
    private val now = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private val missions = FakeMissionRepository()
    private val results = FakeMissionResultRepository()
    private val scope = TestScope(UnconfinedTestDispatcher())

    private fun store(seed: List<Mission> = emptyList()) = RepositoryMissionStore(
        missionRepository = missions,
        missionResultRepository = results,
        scope = scope,
        clock = { now },
        zoneId = { ZoneId.of("UTC") },
        seedMissions = { seed },
    ).also { it.bind("user-a") }

    private fun recurring(id: String, status: MissionStatus) = Mission(
        missionId = id,
        title = "Route $id",
        status = status,
        recurrence = MissionRecurrence(RecurrenceType.DAILY, timezone = "UTC"),
        targetTransportMode = TransportMode.CYCLING,
    )

    @Test
    fun `debug seed is written once and drives the UI state`() {
        val seed = MockMissionScreenDataSource().toSeedMissions("UTC")
        val first = store(seed)

        assertEquals("mock-suggested-mission", first.state.value.suggestedMission?.mission?.missionId)
        assertEquals(2, first.state.value.upcomingMissions.size)

        store(seed)
        assertEquals(3, missions.missions.value.size)
    }

    @Test
    fun `accept, start and complete survive an app restart`() = runTest {
        missions.missions.value = mapOf("s" to recurring("s", MissionStatus.SUGGESTED))
        val store = store()

        store.acceptSuggestedMission()
        assertNull(store.state.value.suggestedMission)
        assertEquals(listOf("s"), store.state.value.upcomingMissions.map { it.mission.missionId })

        store.startMission("s")
        assertEquals("s", store.activeMissionId())
        assertEquals(true, results.results.value["s_2026-10-05"]?.accepted)

        store.completeOccurrence("s", journeyId = "j1")

        // A new store over the same storage is what a process restart looks like.
        val restarted = store()
        val occurrence = results.results.value.getValue("s_2026-10-05")
        assertTrue(occurrence.completed)
        assertEquals("j1", occurrence.linkedJourneyId)
        assertNull(restarted.state.value.activeMission)
        assertEquals(MissionStatus.ACCEPTED, missions.missions.value.getValue("s").status)
        // Completed today: still listed with its next date, but cannot be started again today.
        val listed = restarted.state.value.upcomingMissions.single()
        assertTrue(listed.completedToday)
        assertEquals("2026-10-06", listed.nextOccurrenceDate)
        assertTrue(listed.todayStatusLabel!!.startsWith("Completed today"))
        restarted.startMission("s")
        assertNull(restarted.activeMissionId())
    }

    @Test
    fun `completing the same occurrence twice records it once`() = runTest {
        missions.missions.value = mapOf("a" to recurring("a", MissionStatus.ACCEPTED))
        val store = store()
        store.startMission("a")

        store.completeOccurrence("a", "j1")
        store.completeOccurrence("a", "j2")

        assertEquals(1, results.completeCalls)
        assertEquals(1, results.results.value.size)
        assertEquals("j1", results.results.value.getValue("a_2026-10-05").linkedJourneyId)
    }

    @Test
    fun `skip today keeps the mission listed for its next occurrence`() {
        missions.missions.value = mapOf("a" to recurring("a", MissionStatus.ACCEPTED))
        val store = store()

        store.skipMissionToday("a")

        val listed = store.state.value.upcomingMissions.single()
        assertTrue(listed.skippedToday)
        assertTrue(listed.todayStatusLabel!!.startsWith("Skipped today"))
        val stored = missions.missions.value.getValue("a")
        assertEquals(MissionStatus.ACCEPTED, stored.status)
        assertEquals("2026-10-06", stored.nextOccurrenceDate)
        val occurrence = results.results.value.getValue("a_2026-10-05")
        assertTrue(occurrence.skipped)
    }

    @Test
    fun `only one mission can be active and dismiss is persisted`() {
        missions.missions.value = mapOf(
            "a" to recurring("a", MissionStatus.ACCEPTED),
            "b" to recurring("b", MissionStatus.ACCEPTED),
            "s" to recurring("s", MissionStatus.SUGGESTED),
        )
        val store = store()

        store.startMission("a")
        store.startMission("b")
        store.dismissSuggestedMission()

        assertEquals("a", store.state.value.activeMission?.mission?.missionId)
        assertEquals(MissionStatus.ACCEPTED, missions.missions.value.getValue("b").status)
        assertEquals(MissionStatus.DISMISSED, missions.missions.value.getValue("s").status)
    }

    @Test
    fun `edits from the mission editor are persisted`() {
        missions.missions.value = mapOf("a" to recurring("a", MissionStatus.ACCEPTED))
        val store = store()
        val item = store.state.value.upcomingMissions.single()

        store.updateMission(
            item.copy(
                mission = item.mission.copy(routeTitle = "Home → Gym", transportLabel = "Walking"),
                destination = "Gym",
                scheduledHour = 18,
                scheduledMinute = 15,
            ),
        )

        val stored = missions.missions.value.getValue("a")
        assertEquals("Home → Gym", stored.title)
        assertEquals(TransportMode.WALKING, stored.targetTransportMode)
        assertEquals(18 * 60 + 15, stored.scheduledMinuteOfDay)
    }

    @Test
    fun `signing out clears mission state`() {
        missions.missions.value = mapOf("a" to recurring("a", MissionStatus.ACCEPTED))
        val store = store()

        store.bind(null)

        assertTrue(store.state.value.upcomingMissions.isEmpty())
    }

    @Test
    fun `weekly insight reads real mission results`() {
        // WeeklyInsightViewModel uses the real clock, so the data must be from this week.
        val realNow = System.currentTimeMillis() - 1_000L
        val today = java.time.LocalDate.now(ZoneOffset.UTC).toString()
        val weekStart = realNow - 60_000L
        results.results.value = mapOf(
            "a_2026-10-05" to MissionOccurrence(
                resultId = "a_2026-10-05",
                missionId = "a",
                occurrenceDate = today,
                accepted = true,
                completed = true,
                actualTransportMode = TransportMode.CYCLING,
                actualCarbonSavingGrams = 520.0,
                completedAtMillis = realNow,
            ),
            "b_2026-10-05" to MissionOccurrence(
                resultId = "b_2026-10-05",
                missionId = "b",
                occurrenceDate = today,
                accepted = true,
                lastActionAtMillis = realNow,
            ),
        )

        val dataSource = RepositoryWeeklyInsightDataSource(results) { ZoneId.of("UTC") }
        val loaded = kotlinx.coroutines.runBlocking { dataSource.getMissionResults(weekStart) }
        val viewModel = WeeklyInsightViewModel(DefaultWeeklyCoach(), dataSource)

        assertEquals(2, loaded.size)
        val report = viewModel.uiState.value.report
        assertNotNull(report)
        assertEquals(2, report!!.acceptedMissions)
        assertEquals(1, report.completedMissions)
        assertEquals(520.0, report.totalCarbonSavingGrams, 0.0)
    }
}
