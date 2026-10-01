package com.ecostep.app.algorithm

import com.ecostep.app.data.model.TransportMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultMissionTriggerPlannerTest {

    private val planner =
        DefaultMissionTriggerPlanner(
            zoneId = MELBOURNE_ZONE,
        )

    @Test
    fun `notification is planned five minutes before departure`() {
        val trigger =
            requireNotNull(
                planner.planNextTrigger(
                    pattern = mondayMorningPattern(),
                    currentTimeMillis =
                        timeMillis(
                            dayOfMonth = 21,
                            hour = 7,
                            minute = 0,
                        ),
                ),
            )

        assertEquals(
            timeMillis(
                dayOfMonth = 21,
                hour = 8,
                minute = 0,
            ),
            trigger.expectedDepartureTimeMillis,
        )

        assertEquals(
            timeMillis(
                dayOfMonth = 21,
                hour = 7,
                minute = 55,
            ),
            trigger.notificationTimeMillis,
        )
    }

    @Test
    fun `notification is immediate when lead time has passed`() {
        val currentTime =
            timeMillis(
                dayOfMonth = 21,
                hour = 7,
                minute = 58,
            )

        val trigger =
            requireNotNull(
                planner.planNextTrigger(
                    pattern = mondayMorningPattern(),
                    currentTimeMillis = currentTime,
                ),
            )

        assertEquals(
            currentTime,
            trigger.notificationTimeMillis,
        )

        assertEquals(
            timeMillis(
                dayOfMonth = 21,
                hour = 8,
                minute = 0,
            ),
            trigger.expectedDepartureTimeMillis,
        )
    }

    @Test
    fun `missed departure is planned for next active day`() {
        val trigger =
            requireNotNull(
                planner.planNextTrigger(
                    pattern = mondayMorningPattern(),
                    currentTimeMillis =
                        timeMillis(
                            dayOfMonth = 21,
                            hour = 8,
                            minute = 10,
                        ),
                ),
            )

        assertEquals(
            timeMillis(
                dayOfMonth = 28,
                hour = 8,
                minute = 0,
            ),
            trigger.expectedDepartureTimeMillis,
        )

        assertEquals(
            timeMillis(
                dayOfMonth = 28,
                hour = 7,
                minute = 55,
            ),
            trigger.notificationTimeMillis,
        )
    }

    @Test
    fun `next available weekday is selected`() {
        val pattern =
            mondayMorningPattern().copy(
                activeDays =
                    setOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.WEDNESDAY,
                    ),
            )

        val trigger =
            requireNotNull(
                planner.planNextTrigger(
                    pattern = pattern,
                    currentTimeMillis =
                        timeMillis(
                            dayOfMonth = 22,
                            hour = 12,
                            minute = 0,
                        ),
                ),
            )

        assertEquals(
            timeMillis(
                dayOfMonth = 23,
                hour = 8,
                minute = 0,
            ),
            trigger.expectedDepartureTimeMillis,
        )
    }

    @Test
    fun `empty active days returns no trigger`() {
        val pattern =
            mondayMorningPattern().copy(
                activeDays = emptySet(),
            )

        val trigger =
            planner.planNextTrigger(
                pattern = pattern,
                currentTimeMillis =
                    timeMillis(
                        dayOfMonth = 21,
                        hour = 7,
                        minute = 0,
                    ),
            )

        assertNull(trigger)
    }

    @Test
    fun `invalid departure minute returns no trigger`() {
        val pattern =
            mondayMorningPattern().copy(
                typicalDepartureMinuteOfDay = 1_440,
            )

        val trigger =
            planner.planNextTrigger(
                pattern = pattern,
                currentTimeMillis =
                    timeMillis(
                        dayOfMonth = 21,
                        hour = 7,
                        minute = 0,
                    ),
            )

        assertNull(trigger)
    }

    @Test
    fun `invalid notification lead time is rejected`() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            DefaultMissionTriggerPlanner(
                notificationLeadMinutes = -1L,
                zoneId = MELBOURNE_ZONE,
            )
        }
    }

    private fun mondayMorningPattern() =
        RecurringJourneyPattern(
            occurrenceCount = 3,
            typicalDepartureMinuteOfDay =
                8 * 60,
            averageDurationMinutes = 30L,
            activeDays =
                setOf(DayOfWeek.MONDAY),
            usualTransportMode =
                TransportMode.CAR,
        )

    private fun timeMillis(
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
    ): Long {
        return ZonedDateTime
            .of(
                2026,
                9,
                dayOfMonth,
                hour,
                minute,
                0,
                0,
                MELBOURNE_ZONE,
            )
            .toInstant()
            .toEpochMilli()
    }

    private companion object {
        val MELBOURNE_ZONE: ZoneId =
            ZoneId.of("Australia/Melbourne")
    }
}