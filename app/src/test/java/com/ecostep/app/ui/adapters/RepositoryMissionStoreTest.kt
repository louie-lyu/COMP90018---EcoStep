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
import com.ecostep.app.ui.mock.MissionDay
import com.ecostep.app.ui.mock.MissionTransportOption
import com.ecostep.app.ui.viewmodels.WeeklyInsightViewModel
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
    fun `manual creation survives restart and supports edit start and skip`() = runTest {
        val store = store()
        val draft = recurring("manual", MissionStatus.SUGGESTED).toPageItem().copy(
            startLocation = "Home",
            destination = "Campus",
            scheduledHour = 10,
            scheduledMinute = 30,
            repeatDays = setOf(MissionDay.MONDAY, MissionDay.THURSDAY),
            distanceMeters = 2500.0,
            transportOptions = listOf(MissionTransportOption("Cycling", 42, 0.5)),
        )

        store.createMission(draft)

        val stored = missions.missions.value.getValue("manual")
        assertEquals(MissionStatus.ACCEPTED, stored.status)
        assertEquals("Home", stored.startLabel)
        assertEquals("Campus", stored.destinationLabel)
        assertEquals(630, stored.scheduledMinuteOfDay)
        assertEquals(setOf(1, 4), stored.recurrence.daysOfWeek)
        assertEquals("UTC", stored.recurrence.timezone)
        assertEquals("2026-10-05", stored.nextOccurrenceDate)
        assertEquals(2500.0, stored.targetDistanceMeters!!, 0.0)
        assertEquals(500.0, stored.estimates.single().estimatedCarbonSavedGrams, 0.0)

        val restarted = store()
        val listed = restarted.state.value.upcomingMissions.single()
        restarted.updateMission(listed.copy(destination = "Library"))
        assertEquals("Library", missions.missions.value.getValue("manual").destinationLabel)
        restarted.startMission("manual")
        assertEquals("manual", restarted.activeMissionId())
        restarted.endActiveMission()
        restarted.skipMissionToday("manual")
        assertTrue(restarted.state.value.upcomingMissions.single().skippedToday)
    }

    @Test
    fun `failed creation reports an error without adding an upcoming mission`() = runTest {
        val failingRepository = object : com.ecostep.app.data.repository.MissionRepository by missions {
            override suspend fun createMission(mission: Mission): com.ecostep.app.data.repository.WriteOutcome {
                throw IllegalStateException("Unable to save mission")
            }
        }
        val store = RepositoryMissionStore(
            missionRepository = failingRepository,
            missionResultRepository = results,
            scope = scope,
            clock = { now },
            zoneId = { ZoneId.of("UTC") },
        ).also { it.bind("user-a") }

        val failure = runCatching { store.createMission(recurring("manual", MissionStatus.SUGGESTED).toPageItem()) }
        assertEquals("Unable to save mission", failure.exceptionOrNull()?.message)
        assertTrue(store.state.value.upcomingMissions.isEmpty())
        assertTrue(missions.missions.value.isEmpty())
    }

    @Test
    fun `non scheduled days and closed occurrences cannot start or skip`() {
        val weekly = recurring("weekly", MissionStatus.ACCEPTED).copy(
            recurrence = MissionRecurrence(RecurrenceType.WEEKLY, daysOfWeek = setOf(2), timezone = "UTC"),
        )
        missions.missions.value = mapOf("weekly" to weekly)
        val store = store()
        assertEquals(false, store.state.value.upcomingMissions.single().dueToday)
        store.startMission("weekly")
        store.skipMissionToday("weekly")
        assertEquals(0, missions.writes)
        assertTrue(results.results.value.isEmpty())

        missions.missions.value = mapOf("weekly" to weekly.copy(recurrence = MissionRecurrence(RecurrenceType.DAILY)))
        assertTrue(store.state.value.upcomingMissions.single().dueToday)
        store.skipMissionToday("weekly")
        val writesAfterSkip = missions.writes
        store.startMission("weekly")
        store.skipMissionToday("weekly")
        assertEquals(writesAfterSkip, missions.writes)
        assertNull(store.activeMissionId())
    }

    @Test
    fun `midnight refresh clears yesterday status and stops on sign out`() = runTest {
        val beforeMidnight = ZonedDateTime.of(2026, 10, 5, 23, 59, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        missions.missions.value = mapOf(
            "a" to recurring("a", MissionStatus.ACCEPTED),
            "b" to recurring("b", MissionStatus.ACCEPTED),
        )
        results.results.value = mapOf("a_2026-10-05" to MissionOccurrence(
            resultId = "a_2026-10-05", missionId = "a", occurrenceDate = "2026-10-05", skipped = true,
        ), "b_2026-10-05" to MissionOccurrence(
            resultId = "b_2026-10-05", missionId = "b", occurrenceDate = "2026-10-05", completed = true,
        ))
        val store = RepositoryMissionStore(missions, results, backgroundScope,
            clock = { beforeMidnight + testScheduler.currentTime }, zoneId = { ZoneId.of("UTC") })
        store.bind("user-a")
        runCurrent()
        assertTrue(store.state.value.upcomingMissions.first { it.mission.missionId == "a" }.skippedToday)
        assertTrue(store.state.value.upcomingMissions.first { it.mission.missionId == "b" }.completedToday)
        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(store.state.value.upcomingMissions.all { !it.skippedToday && !it.completedToday })
        store.startMission("a")
        runCurrent()
        assertEquals("a", store.activeMissionId())
        store.bind(null)
        advanceTimeBy(24 * 60 * 60_000L)
        runCurrent()
        assertTrue(store.state.value.upcomingMissions.isEmpty())
    }

    @Test
    fun `rebinding the same user refreshes status after the date changes`() {
        var current = now
        missions.missions.value = mapOf("a" to recurring("a", MissionStatus.ACCEPTED))
        val store = RepositoryMissionStore(missions, results, scope,
            clock = { current }, zoneId = { ZoneId.of("UTC") })
        store.bind("user-a")
        store.skipMissionToday("a")
        assertTrue(store.state.value.upcomingMissions.single().skippedToday)
        current += 24 * 60 * 60_000L
        store.bind("user-a")
        assertEquals(false, store.state.value.upcomingMissions.single().skippedToday)
    }

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
