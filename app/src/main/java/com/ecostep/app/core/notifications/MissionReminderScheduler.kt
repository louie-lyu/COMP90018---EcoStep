package com.ecostep.app.core.notifications

import com.ecostep.app.algorithm.DefaultMissionTriggerPlanner
import com.ecostep.app.algorithm.MissionTrigger
import com.ecostep.app.algorithm.RecurringJourneyPattern
import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RecurrenceType
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.UserPreferences
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Platform side of mission reminders; replaceable in tests. */
interface MissionReminderScheduler {
    fun schedule(mission: Mission, trigger: MissionTrigger, title: String, message: String)
    fun cancel(missionId: String)
    fun cancelAll()

    /** Missions that currently have a reminder scheduled, including from earlier app runs. */
    fun scheduledMissionIds(): Set<String>
}

/**
 * Keeps one reminder per accepted mission in line with the user's preferences. Safe to call
 * on every mission or preference change: reminders that are no longer wanted are cancelled
 * and the rest are rescheduled in place.
 */
class MissionReminderSync(
    private val scheduler: MissionReminderScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun sync(missions: List<Mission>, preferences: UserPreferences) {
        if (!preferences.missionNotificationsEnabled) {
            scheduler.cancelAll()
            return
        }
        val now = clock()
        val leadMinutes = preferences.defaultReminderMinutes.coerceIn(0, UserPreferences.MAX_REMINDER_MINUTES)
        val wanted = missions
            .filter { it.status == MissionStatus.ACCEPTED }
            .mapNotNull { mission -> planReminder(mission, leadMinutes, now)?.let { mission to it } }
        val wantedIds = wanted.mapTo(mutableSetOf()) { it.first.missionId }

        scheduler.scheduledMissionIds()
            .filterNot { it in wantedIds }
            .forEach(scheduler::cancel)
        wanted.forEach { (mission, trigger) ->
            scheduler.schedule(mission, trigger, REMINDER_TITLE, reminderMessage(mission))
        }
    }

    fun cancelAll() = scheduler.cancelAll()

    private fun reminderMessage(mission: Mission): String {
        val mode = when (mission.targetTransportMode) {
            TransportMode.WALKING -> "Walk"
            TransportMode.CYCLING -> "Cycle"
            TransportMode.PUBLIC_TRANSPORT -> "Take public transport"
            TransportMode.CAR, TransportMode.UNKNOWN, null -> "Head off"
        }
        return "$mode: ${mission.title}"
    }

    private companion object {
        const val REMINDER_TITLE = "Time for your EcoStep mission"
    }
}

/**
 * Next reminder for an accepted mission, rebuilt from its persisted schedule as the
 * equivalent recurring pattern. Never returns a reminder that is already due, so a
 * reminder that just fired is followed by the next occurrence, not repeated.
 */
internal fun planReminder(mission: Mission, leadMinutes: Int, nowMillis: Long): MissionTrigger? {
    val zone = runCatching { ZoneId.of(mission.recurrence.timezone) }.getOrDefault(ZoneId.systemDefault())
    val nextDate = mission.nextOccurrenceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val activeDays: Set<DayOfWeek> = when (mission.recurrence.type) {
        RecurrenceType.NONE -> setOfNotNull(nextDate?.dayOfWeek)
        RecurrenceType.DAILY -> DayOfWeek.values().toSet()
        RecurrenceType.WEEKLY, RecurrenceType.CUSTOM ->
            mission.recurrence.daysOfWeek.map(DayOfWeek::of).toSet().ifEmpty { DayOfWeek.values().toSet() }
    }
    val pattern = RecurringJourneyPattern(
        occurrenceCount = 0,
        typicalDepartureMinuteOfDay = mission.scheduledMinuteOfDay,
        averageDurationMinutes = 0,
        activeDays = activeDays,
        usualTransportMode = mission.targetTransportMode ?: TransportMode.UNKNOWN,
    )
    val planner = DefaultMissionTriggerPlanner(notificationLeadMinutes = leadMinutes.toLong(), zoneId = zone)
    // Skipped or completed occurrences move nextOccurrenceDate forward; plan from there.
    val from = maxOf(nowMillis, nextDate?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: nowMillis)

    var trigger = planner.planNextTrigger(pattern, from) ?: return null
    if (trigger.notificationTimeMillis <= nowMillis) {
        trigger = planner.planNextTrigger(pattern, trigger.expectedDepartureTimeMillis + 60_000L) ?: return null
    }
    if (mission.recurrence.type == RecurrenceType.NONE) {
        val departureDate = Instant.ofEpochMilli(trigger.expectedDepartureTimeMillis).atZone(zone).toLocalDate()
        if (departureDate != nextDate) return null
    }
    return trigger
}
