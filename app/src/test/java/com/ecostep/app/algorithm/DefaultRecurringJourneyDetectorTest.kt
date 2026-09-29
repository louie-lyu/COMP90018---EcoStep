package com.ecostep.app.algorithm

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultRecurringJourneyDetectorTest {

    private val detector =
        DefaultRecurringJourneyDetector(
            zoneId = MELBOURNE_ZONE,
        )

    @Test
    fun `three similar journeys create recurring pattern`() {
        val reference =
            createJourney(
                id = "journey_1",
                dayOfMonth = 21,
                hour = 8,
                minute = 0,
                mode = TransportMode.CAR,
            )

        val history =
            listOf(
                createJourney(
                    id = "journey_2",
                    dayOfMonth = 22,
                    hour = 8,
                    minute = 10,
                    start = NEARBY_HOME,
                    end = NEARBY_OFFICE,
                    mode =
                        TransportMode.PUBLIC_TRANSPORT,
                ),
                createJourney(
                    id = "journey_3",
                    dayOfMonth = 23,
                    hour = 7,
                    minute = 50,
                    start = NEARBY_HOME,
                    end = NEARBY_OFFICE,
                    mode =
                        TransportMode.PUBLIC_TRANSPORT,
                ),
            )

        val pattern =
            requireNotNull(
                detector.detect(
                    referenceJourney = reference,
                    journeyHistory = history,
                ),
            )

        assertEquals(3, pattern.occurrenceCount)
        assertEquals(
            8 * 60,
            pattern.typicalDepartureMinuteOfDay,
        )
        assertEquals(
            30L,
            pattern.averageDurationMinutes,
        )
        assertEquals(
            TransportMode.PUBLIC_TRANSPORT,
            pattern.usualTransportMode,
        )
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
            ),
            pattern.activeDays,
        )
    }

    @Test
    fun `fewer than three journeys returns no pattern`() {
        val reference =
            createJourney(
                id = "journey_1",
                dayOfMonth = 21,
                hour = 8,
                minute = 0,
            )

        val history =
            listOf(
                createJourney(
                    id = "journey_2",
                    dayOfMonth = 22,
                    hour = 8,
                    minute = 10,
                ),
            )

        val pattern =
            detector.detect(
                referenceJourney = reference,
                journeyHistory = history,
            )

        assertNull(pattern)
    }

    @Test
    fun `different destination is not treated as same journey`() {
        val reference =
            createJourney(
                id = "journey_1",
                dayOfMonth = 21,
                hour = 8,
                minute = 0,
            )

        val history =
            listOf(
                createJourney(
                    id = "journey_2",
                    dayOfMonth = 22,
                    hour = 8,
                    minute = 10,
                ),
                createJourney(
                    id = "journey_3",
                    dayOfMonth = 23,
                    hour = 8,
                    minute = 5,
                    end = FAR_DESTINATION,
                ),
            )

        val pattern =
            detector.detect(
                referenceJourney = reference,
                journeyHistory = history,
            )

        assertNull(pattern)
    }

    @Test
    fun `different departure time is not treated as same pattern`() {
        val reference =
            createJourney(
                id = "journey_1",
                dayOfMonth = 21,
                hour = 8,
                minute = 0,
            )

        val history =
            listOf(
                createJourney(
                    id = "journey_2",
                    dayOfMonth = 22,
                    hour = 8,
                    minute = 10,
                ),
                createJourney(
                    id = "journey_3",
                    dayOfMonth = 23,
                    hour = 10,
                    minute = 0,
                ),
            )

        val pattern =
            detector.detect(
                referenceJourney = reference,
                journeyHistory = history,
            )

        assertNull(pattern)
    }

    @Test
    fun `reverse journey is treated as different direction`() {
        val reference =
            createJourney(
                id = "journey_1",
                dayOfMonth = 21,
                hour = 8,
                minute = 0,
            )

        val history =
            listOf(
                createJourney(
                    id = "journey_2",
                    dayOfMonth = 22,
                    hour = 8,
                    minute = 10,
                ),
                createJourney(
                    id = "journey_3",
                    dayOfMonth = 23,
                    hour = 8,
                    minute = 5,
                    start = OFFICE,
                    end = HOME,
                ),
            )

        val pattern =
            detector.detect(
                referenceJourney = reference,
                journeyHistory = history,
            )

        assertNull(pattern)
    }

    @Test
    fun `departure times around midnight are averaged correctly`() {
        val reference =
            createJourney(
                id = "journey_1",
                dayOfMonth = 21,
                hour = 23,
                minute = 50,
            )

        val history =
            listOf(
                createJourney(
                    id = "journey_2",
                    dayOfMonth = 22,
                    hour = 0,
                    minute = 10,
                ),
                createJourney(
                    id = "journey_3",
                    dayOfMonth = 23,
                    hour = 0,
                    minute = 0,
                ),
            )

        val pattern =
            requireNotNull(
                detector.detect(
                    referenceJourney = reference,
                    journeyHistory = history,
                ),
            )

        assertEquals(
            0,
            pattern.typicalDepartureMinuteOfDay,
        )
    }

    private fun createJourney(
        id: String,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
        start: GeoPoint = HOME,
        end: GeoPoint = OFFICE,
        mode: TransportMode = TransportMode.CAR,
    ): JourneySummary {
        val startTime =
            ZonedDateTime.of(
                2026,
                9,
                dayOfMonth,
                hour,
                minute,
                0,
                0,
                MELBOURNE_ZONE,
            )

        return JourneySummary(
            journeyId = id,
            userId = "test_user",
            startLocation = start,
            endLocation = end,
            startTimeMillis =
                startTime.toInstant().toEpochMilli(),
            endTimeMillis =
                startTime
                    .plusMinutes(30)
                    .toInstant()
                    .toEpochMilli(),
            distanceMeters = 5_000.0,
            transportMode = mode,
        )
    }

    private companion object {
        val MELBOURNE_ZONE: ZoneId =
            ZoneId.of("Australia/Melbourne")

        val HOME =
            GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            )

        val NEARBY_HOME =
            GeoPoint(
                latitude = -37.8132,
                longitude = 144.9635,
            )

        val OFFICE =
            GeoPoint(
                latitude = -37.8200,
                longitude = 144.9700,
            )

        val NEARBY_OFFICE =
            GeoPoint(
                latitude = -37.8196,
                longitude = 144.9704,
            )

        val FAR_DESTINATION =
            GeoPoint(
                latitude = -37.8500,
                longitude = 145.0000,
            )
    }
}