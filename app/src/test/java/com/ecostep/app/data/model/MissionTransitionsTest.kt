package com.ecostep.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MissionTransitionsTest {

    // 2026-10-05 is a Monday.
    private val monday = LocalDate.of(2026, 10, 5)
    private val weekdays = MissionRecurrence.weekly(
        setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        "Australia/Melbourne",
    )

    private fun mission(
        status: MissionStatus = MissionStatus.SUGGESTED,
        recurrence: MissionRecurrence = weekdays,
    ) = Mission(missionId = "m1", title = "Home → Uni", status = status, recurrence = recurrence)

    @Test
    fun `suggested mission can be accepted or dismissed`() {
        val accepted = MissionTransitions.accept(mission(), monday)
        val dismissed = MissionTransitions.dismiss(mission())

        assertEquals(MissionStatus.ACCEPTED, accepted.status)
        assertEquals("2026-10-05", accepted.nextOccurrenceDate)
        assertEquals(MissionStatus.DISMISSED, dismissed.status)
    }

    @Test
    fun `accepting on a non-scheduled day picks the next scheduled day`() {
        val tuesday = monday.plusDays(1)

        assertEquals("2026-10-07", MissionTransitions.accept(mission(), tuesday).nextOccurrenceDate)
    }

    @Test
    fun `start then end of a recurring mission returns it to upcoming`() {
        val active = MissionTransitions.start(mission(MissionStatus.ACCEPTED), monday)
        val ended = MissionTransitions.end(active, monday, completed = true)

        assertEquals(MissionStatus.ACTIVE, active.status)
        assertEquals("2026-10-05", active.activeOccurrenceDate)
        assertEquals(MissionStatus.ACCEPTED, ended.status)
        assertNull(ended.activeOccurrenceDate)
        assertEquals("2026-10-07", ended.nextOccurrenceDate)
    }

    @Test
    fun `one-off mission ends as completed or archived`() {
        val oneOff = mission(MissionStatus.ACCEPTED, MissionRecurrence())
        val active = MissionTransitions.start(oneOff, monday)

        assertEquals(MissionStatus.COMPLETED, MissionTransitions.end(active, monday, completed = true).status)
        assertEquals(MissionStatus.ARCHIVED, MissionTransitions.end(active, monday, completed = false).status)
    }

    @Test
    fun `skip today keeps the recurring definition and moves to the next occurrence`() {
        val accepted = MissionTransitions.accept(mission(), monday)

        val skipped = MissionTransitions.skipToday(accepted, monday)

        assertEquals(MissionStatus.ACCEPTED, skipped.status)
        assertEquals(weekdays, skipped.recurrence)
        assertEquals("2026-10-07", skipped.nextOccurrenceDate)
    }

    @Test
    fun `invalid transitions are rejected`() {
        assertThrows(IllegalStateException::class.java) {
            MissionTransitions.start(mission(MissionStatus.SUGGESTED), monday)
        }
        assertThrows(IllegalStateException::class.java) {
            MissionTransitions.accept(mission(MissionStatus.DISMISSED), monday)
        }
        assertThrows(IllegalStateException::class.java) {
            MissionTransitions.end(mission(MissionStatus.ACCEPTED), monday, completed = true)
        }
        assertThrows(IllegalStateException::class.java) {
            MissionTransitions.skipToday(mission(MissionStatus.ACTIVE), monday)
        }
        assertThrows(IllegalStateException::class.java) {
            MissionTransitions.edit(mission(MissionStatus.ARCHIVED), mission(), monday)
        }
    }

    @Test
    fun `edit keeps status, active occurrence and creation time`() {
        val active = MissionTransitions.start(mission(MissionStatus.ACCEPTED), monday)
            .copy(createdAtMillis = 42L)
        val edited = active.copy(
            title = "Home → Gym",
            status = MissionStatus.COMPLETED,
            createdAtMillis = null,
            recurrence = MissionRecurrence(RecurrenceType.DAILY, timezone = "UTC"),
        )

        val result = MissionTransitions.edit(active, edited, monday)

        assertEquals("Home → Gym", result.title)
        assertEquals(MissionStatus.ACTIVE, result.status)
        assertEquals("2026-10-05", result.activeOccurrenceDate)
        assertEquals(42L, result.createdAtMillis)
    }

    @Test
    fun `recurrence finds the next scheduled day across the week boundary`() {
        val friday = monday.plusDays(4)

        assertEquals(monday.plusDays(7), weekdays.nextOccurrenceAfter(friday))
        assertEquals(friday.plusDays(1), MissionRecurrence(RecurrenceType.DAILY).nextOccurrenceAfter(friday))
        assertNull(MissionRecurrence().nextOccurrenceAfter(friday))
    }

    @Test
    fun `occurrence ID is deterministic per mission and date`() {
        assertEquals("m1_2026-10-05", MissionOccurrence.resultId("m1", "2026-10-05"))
    }

    @Test
    fun `occurrence converts to the calculator's MissionResult`() {
        val result = MissionOccurrence(
            resultId = "m1_2026-10-05",
            missionId = "m1",
            occurrenceDate = "2026-10-05",
            accepted = true,
            completed = true,
            actualTransportMode = TransportMode.WALKING,
            actualCarbonSavingGrams = 384.0,
            completedAtMillis = 99L,
        ).toMissionResult()

        assertEquals(MissionResult("m1", true, true, TransportMode.WALKING, 384.0, 99L), result)
    }
}
