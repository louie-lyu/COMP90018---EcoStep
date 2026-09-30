package com.ecostep.app.algorithm

import java.time.Instant
import java.time.ZoneId

class DefaultMissionTriggerPlanner(
    private val notificationLeadMinutes: Long = 5L,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : MissionTriggerPlanner {

    init {
        require(
            notificationLeadMinutes in 0L..MINUTES_PER_DAY,
        ) {
            "Notification lead time must be between 0 and 1440 minutes."
        }
    }

    override fun planNextTrigger(
        pattern: RecurringJourneyPattern,
        currentTimeMillis: Long,
    ): MissionTrigger? {
        if (pattern.activeDays.isEmpty()) {
            return null
        }

        if (
            pattern.typicalDepartureMinuteOfDay !in
            0 until MINUTES_PER_DAY.toInt()
        ) {
            return null
        }

        val now =
            Instant
                .ofEpochMilli(currentTimeMillis)
                .atZone(zoneId)

        for (dayOffset in 0L..DAYS_IN_WEEK) {
            val candidateDate =
                now
                    .toLocalDate()
                    .plusDays(dayOffset)

            if (
                candidateDate.dayOfWeek !in
                pattern.activeDays
            ) {
                continue
            }

            // Resolve the local clock time directly; elapsed minutes from midnight drift on DST days.
            val departureTime =
                candidateDate
                    .atTime(
                        pattern.typicalDepartureMinuteOfDay / 60,
                        pattern.typicalDepartureMinuteOfDay % 60,
                    )
                    .atZone(zoneId)

            if (departureTime.isBefore(now)) {
                continue
            }

            val plannedNotificationTime =
                departureTime.minusMinutes(
                    notificationLeadMinutes,
                )

            val notificationTime =
                if (plannedNotificationTime.isBefore(now)) {
                    now
                } else {
                    plannedNotificationTime
                }

            return MissionTrigger(
                expectedDepartureTimeMillis =
                    departureTime
                        .toInstant()
                        .toEpochMilli(),
                notificationTimeMillis =
                    notificationTime
                        .toInstant()
                        .toEpochMilli(),
            )
        }

        return null
    }

    private companion object {
        const val MINUTES_PER_DAY = 24L * 60L
        const val DAYS_IN_WEEK = 7L
    }
}
