package com.ecostep.app.core.notifications

import com.ecostep.app.algorithm.MissionTrigger
import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RecurrenceType
import com.ecostep.app.data.model.UserPreferences
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionReminderSyncTest {

    private class FakeScheduler : MissionReminderScheduler {
        val scheduled = mutableMapOf<String, MissionTrigger>()
        var scheduleCalls = 0

        override fun schedule(mission: Mission, trigger: MissionTrigger, title: String, message: String) {
            scheduleCalls++
            scheduled[mission.missionId] = trigger
        }

        override fun cancel(missionId: String) {
            scheduled -= missionId
        }

        override fun cancelAll() = scheduled.clear()

        override fun scheduledMissionIds(): Set<String> = scheduled.keys.toSet()
    }

    // Monday 2026-10-05 07:00 UTC.
    private val now = LocalDateTime.of(2026, 10, 5, 7, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun millis(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, 10, day, hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun mission(
        id: String = "m1",
        status: MissionStatus = MissionStatus.ACCEPTED,
        days: Set<Int> = setOf(1, 3),
        minuteOfDay: Int = 8 * 60 + 30,
        nextDate: String? = "2026-10-05",
        type: RecurrenceType = RecurrenceType.WEEKLY,
    ) = Mission(
        missionId = id,
        title = "Home → Uni",
        status = status,
        recurrence = MissionRecurrence(type = type, daysOfWeek = days, timezone = "UTC"),
        scheduledMinuteOfDay = minuteOfDay,
        nextOccurrenceDate = nextDate,
    )

    private val scheduler = FakeScheduler()
    private val sync = MissionReminderSync(scheduler, clock = { now })

    @Test
    fun `accepted missions get a reminder using the chosen lead time`() {
        sync.sync(listOf(mission()), UserPreferences(defaultReminderMinutes = 15))

        assertEquals(millis(5, 8, 15), scheduler.scheduled.getValue("m1").notificationTimeMillis)
        assertEquals(millis(5, 8, 30), scheduler.scheduled.getValue("m1").expectedDepartureTimeMillis)
    }

    @Test
    fun `changing the lead time reschedules the same mission`() {
        sync.sync(listOf(mission()), UserPreferences(defaultReminderMinutes = 15))
        sync.sync(listOf(mission()), UserPreferences(defaultReminderMinutes = 60))

        assertEquals(setOf("m1"), scheduler.scheduledMissionIds())
        assertEquals(millis(5, 7, 30), scheduler.scheduled.getValue("m1").notificationTimeMillis)
    }

    @Test
    fun `turning reminders off cancels them all`() {
        sync.sync(listOf(mission("m1"), mission("m2")), UserPreferences())

        sync.sync(listOf(mission("m1"), mission("m2")), UserPreferences(missionNotificationsEnabled = false))

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `dismissed, suggested or archived missions lose their reminder`() {
        sync.sync(listOf(mission("m1"), mission("m2")), UserPreferences())

        sync.sync(
            listOf(mission("m1", status = MissionStatus.DISMISSED), mission("m2", status = MissionStatus.ARCHIVED)),
            UserPreferences(),
        )

        assertTrue(scheduler.scheduled.isEmpty())
    }

    @Test
    fun `a reminder that is already due moves to the next occurrence`() {
        // 08:30 departure with a 60 minute lead was due at 07:30; the clock says 07:45.
        val trigger = planReminder(mission(), leadMinutes = 60, nowMillis = millis(5, 7, 45))

        assertEquals(millis(7, 8, 30), trigger!!.expectedDepartureTimeMillis)
        assertEquals(millis(7, 7, 30), trigger.notificationTimeMillis)
    }

    @Test
    fun `a skipped day is respected through the next occurrence date`() {
        val trigger = planReminder(mission(nextDate = "2026-10-07"), leadMinutes = 15, nowMillis = now)

        assertEquals(millis(7, 8, 30), trigger!!.expectedDepartureTimeMillis)
    }

    @Test
    fun `a one-off mission is only reminded on its date`() {
        val oneOff = mission(type = RecurrenceType.NONE, days = emptySet(), nextDate = "2026-10-05")

        assertEquals(millis(5, 8, 30), planReminder(oneOff, 15, now)!!.expectedDepartureTimeMillis)
        assertNull(planReminder(oneOff, 15, millis(5, 9, 0)))
    }
}
