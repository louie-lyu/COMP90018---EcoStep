package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.MissionTrigger
import com.ecostep.app.algorithm.RecurringJourneyPattern
import com.ecostep.app.data.model.TransportMode
import java.time.DayOfWeek
import java.time.OffsetDateTime

internal data class MissionTriggerCase(
    val name: String,
    val now: String = "2026-09-21T07:00:00+10:00",
    val departure: String? = "2026-09-21T08:00:00+10:00",
    val notification: String? = "2026-09-21T07:55:00+10:00",
    val minuteOfDay: Int = 480,
    val activeDays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
    val leadMinutes: Long = 5L,
    val zone: String = "Australia/Melbourne",
    val rejectsLead: Boolean = false,
) {
    val currentTimeMillis: Long get() = millis(now)
    val expected: MissionTrigger? get() = departure?.let {
        MissionTrigger(millis(it), millis(requireNotNull(notification)))
    }
    val pattern: RecurringJourneyPattern get() =
        RecurringJourneyPattern(3, minuteOfDay, 30L, activeDays, TransportMode.CAR)

    private fun millis(value: String) = OffsetDateTime.parse(value).toInstant().toEpochMilli()
}

/** Explicit offset timestamps are the oracle; no call to the planner builds expected times. */
internal object MissionTriggerEvaluationCases {
    fun cases(): List<MissionTriggerCase> = listOf(
        MissionTriggerCase("five_minutes_before_departure"),
        MissionTriggerCase("exact_notification_boundary", now = "2026-09-21T07:55:00+10:00"),
        MissionTriggerCase("inside_notification_window", now = "2026-09-21T07:58:00+10:00",
            notification = "2026-09-21T07:58:00+10:00"),
        MissionTriggerCase("exact_departure", now = "2026-09-21T08:00:00+10:00",
            notification = "2026-09-21T08:00:00+10:00"),
        MissionTriggerCase("one_millisecond_after_departure", now = "2026-09-21T08:00:00.001+10:00",
            departure = "2026-09-28T08:00:00+10:00", notification = "2026-09-28T07:55:00+10:00"),
        MissionTriggerCase("next_active_weekday", now = "2026-09-22T12:00:00+10:00",
            departure = "2026-09-23T08:00:00+10:00", notification = "2026-09-23T07:55:00+10:00",
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        MissionTriggerCase("nearest_of_multiple_weekdays", now = "2026-09-21T08:01:00+10:00",
            departure = "2026-09-22T08:00:00+10:00", notification = "2026-09-22T07:55:00+10:00",
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY)),
        MissionTriggerCase("sunday_to_monday", now = "2026-09-20T21:00:00+10:00"),
        MissionTriggerCase("cross_month", now = "2026-09-28T08:01:00Z", departure = "2026-10-05T08:00:00Z",
            notification = "2026-10-05T07:55:00Z", zone = "UTC"),
        MissionTriggerCase("cross_year", now = "2026-12-31T08:01:00Z", departure = "2027-01-07T08:00:00Z",
            notification = "2027-01-07T07:55:00Z", activeDays = setOf(DayOfWeek.THURSDAY), zone = "UTC"),
        MissionTriggerCase("midnight_departure", now = "2026-09-20T23:50:00+10:00",
            departure = "2026-09-21T00:00:00+10:00", notification = "2026-09-20T23:55:00+10:00", minuteOfDay = 0),
        MissionTriggerCase("midnight_immediate_notification", now = "2026-09-20T23:58:00+10:00",
            departure = "2026-09-21T00:00:00+10:00", notification = "2026-09-20T23:58:00+10:00", minuteOfDay = 0),
        MissionTriggerCase("zero_lead", leadMinutes = 0L, notification = "2026-09-21T08:00:00+10:00"),
        MissionTriggerCase("maximum_lead", now = "2026-09-20T07:00:00+10:00", leadMinutes = 1440L,
            notification = "2026-09-20T08:00:00+10:00"),
        MissionTriggerCase("negative_lead_rejected", leadMinutes = -1L, rejectsLead = true),
        MissionTriggerCase("excessive_lead_rejected", leadMinutes = 1441L, rejectsLead = true),
        MissionTriggerCase("no_active_days", activeDays = emptySet(), departure = null, notification = null),
        MissionTriggerCase("negative_departure_minute", minuteOfDay = -1, departure = null, notification = null),
        MissionTriggerCase("excessive_departure_minute", minuteOfDay = 1440, departure = null, notification = null),
        MissionTriggerCase("all_weekdays", now = "2026-09-21T08:01:00+10:00",
            departure = "2026-09-22T08:00:00+10:00", notification = "2026-09-22T07:55:00+10:00",
            activeDays = DayOfWeek.entries.toSet()),
        MissionTriggerCase("utc_zone", now = "2026-09-21T07:00:00Z", departure = "2026-09-21T08:00:00Z",
            notification = "2026-09-21T07:55:00Z", zone = "UTC"),
        MissionTriggerCase("half_hour_offset", now = "2026-09-21T07:00:00+05:30",
            departure = "2026-09-21T08:00:00+05:30", notification = "2026-09-21T07:55:00+05:30", zone = "Asia/Kolkata"),
        // ZoneId resolves a missing local time forward by the gap, and an overlap to the earlier offset.
        MissionTriggerCase("spring_gap_shifted_forward", now = "2026-10-04T01:00:00+10:00",
            departure = "2026-10-04T03:30:00+11:00", notification = "2026-10-04T03:25:00+11:00",
            minuteOfDay = 150, activeDays = setOf(DayOfWeek.SUNDAY)),
        MissionTriggerCase("spring_local_departure", now = "2026-10-04T01:00:00+10:00",
            departure = "2026-10-04T08:00:00+11:00", notification = "2026-10-04T07:55:00+11:00",
            activeDays = setOf(DayOfWeek.SUNDAY)),
        MissionTriggerCase("spring_resolved_departure_missed", now = "2026-10-04T03:45:00+11:00",
            departure = "2026-10-11T02:30:00+11:00", notification = "2026-10-11T02:25:00+11:00",
            minuteOfDay = 150, activeDays = setOf(DayOfWeek.SUNDAY)),
        MissionTriggerCase("autumn_overlap_earlier_offset", now = "2026-04-05T01:00:00+11:00",
            departure = "2026-04-05T02:30:00+11:00", notification = "2026-04-05T02:25:00+11:00",
            minuteOfDay = 150, activeDays = setOf(DayOfWeek.SUNDAY)),
        MissionTriggerCase("autumn_local_departure", now = "2026-04-05T01:00:00+11:00",
            departure = "2026-04-05T08:00:00+10:00", notification = "2026-04-05T07:55:00+10:00",
            activeDays = setOf(DayOfWeek.SUNDAY)),
        MissionTriggerCase("autumn_earlier_departure_missed", now = "2026-04-05T02:45:00+11:00",
            departure = "2026-04-12T02:30:00+10:00", notification = "2026-04-12T02:25:00+10:00",
            minuteOfDay = 150, activeDays = setOf(DayOfWeek.SUNDAY)),
        MissionTriggerCase("notification_crosses_spring_gap", now = "2026-10-04T01:50:00+10:00",
            departure = "2026-10-04T03:00:00+11:00", notification = "2026-10-04T01:55:00+10:00",
            minuteOfDay = 180, activeDays = setOf(DayOfWeek.SUNDAY)),
    )
}
