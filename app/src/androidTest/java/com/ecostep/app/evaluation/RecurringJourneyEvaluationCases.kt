package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.RecurringJourneyPattern
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

internal data class RecurringJourneyCase(
    val name: String,
    val description: String,
    val reference: JourneySummary,
    val history: List<JourneySummary>,
    val expected: RecurringJourneyPattern?,
)

/** Synthetic, single-user fixtures. Expected results never call the detector. */
internal object RecurringJourneyEvaluationCases {
    val zone: ZoneId = ZoneId.of("Australia/Melbourne")
    private val monday = LocalDate.of(2026, 9, 21)
    private val start = GeoPoint(0.0, 0.0)
    private val end = GeoPoint(0.0, 0.02)

    fun journey(
        id: String,
        day: Int = 0,
        minute: Int = 480,
        durationMinutes: Int = 30,
        mode: TransportMode = TransportMode.CAR,
    ): JourneySummary {
        val departure = monday.plusDays(day.toLong())
            .atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
        return JourneySummary(
            journeyId = id,
            userId = "recurring_evaluation_user",
            startLocation = start,
            endLocation = end,
            startTimeMillis = departure,
            endTimeMillis = departure + durationMinutes * 60_000L,
            distanceMeters = 2_224.0,
            transportMode = mode,
        )
    }

    fun correctnessCases(): List<RecurringJourneyCase> = buildList {
        val reference = journey("reference")
        val first = journey("first", day = 1)
        val second = journey("second", day = 2)
        val history = listOf(first, second)
        val expected = RecurringJourneyPattern(
            occurrenceCount = 3,
            typicalDepartureMinuteOfDay = 480,
            averageDurationMinutes = 30,
            activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
            usualTransportMode = TransportMode.CAR,
        )

        add(RecurringJourneyCase("empty_history", "Reference alone is one occurrence", reference, emptyList(), null))
        add(RecurringJourneyCase("two_occurrences", "Reference plus one history entry is below three", reference, listOf(first), null))
        add(RecurringJourneyCase("three_occurrences", "Reference plus two history entries meets the minimum", reference, history, expected))
        add(RecurringJourneyCase(
            "five_occurrences", "Count all five unique matching journeys", reference,
            history + listOf(journey("third", 3), journey("fourth", 4)),
            expected.copy(occurrenceCount = 5, activeDays = expected.activeDays + setOf(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)),
        ))

        // Along a meridian from latitude zero, arc length is R * latitude angle.
        // This constructs distances independently of the detector's Haversine formula.
        // Test both signs and each endpoint; do not loosen the detector's 200 m threshold.
        for (endpoint in listOf("start", "end", "both")) {
            for (meters in listOf(-200.1, -200.0, -199.9, 199.9, 200.0, 200.1)) {
                val latitude = Math.toDegrees(meters / 6_371_000.0)
                val shifted = history.map { journey ->
                    journey.copy(
                        startLocation = if (endpoint != "end") start.copy(latitude = latitude) else start,
                        endLocation = if (endpoint != "start") end.copy(latitude = latitude) else end,
                    )
                }
                add(RecurringJourneyCase(
                    "location_${endpoint}_$meters", "$endpoint displacement $meters m; inclusive 200 m tolerance",
                    reference, shifted, if (meters in -200.0..200.0) expected else null,
                ))
            }
        }

        // Hand-calculated means of [08:00, 08:00 + offset, 08:00 + offset].
        for ((offset, meanMinute) in listOf(-61 to null, -60 to 440, -59 to 441, 59 to 519, 60 to 520, 61 to null)) {
            add(RecurringJourneyCase(
                "departure_offset_$offset", "Departure offset $offset min; inclusive 60 min tolerance",
                reference, listOf(journey("first", 1, 480 + offset), journey("second", 2, 480 + offset)),
                meanMinute?.let { expected.copy(typicalDepartureMinuteOfDay = it) },
            ))
        }

        add(RecurringJourneyCase(
            "different_destination", "One distant destination leaves only two matches", reference,
            listOf(first, second.copy(endLocation = GeoPoint(0.0, 0.04))), null,
        ))
        add(RecurringJourneyCase(
            "reverse_direction", "Return trips must not count as outbound journeys", reference,
            history.map { it.copy(startLocation = end, endLocation = start) }, null,
        ))
        add(RecurringJourneyCase(
            "cross_midnight", "23:50, 00:10 and 00:00 average to midnight",
            journey("reference", minute = 1430), listOf(journey("first", 1, 10), journey("second", 2, 0)),
            expected.copy(typicalDepartureMinuteOfDay = 0),
        ))
        add(RecurringJourneyCase(
            "duplicate_id_below_minimum", "Repeated copies do not create additional occurrences",
            reference, listOf(first, first, first), null,
        ))
        add(RecurringJourneyCase(
            "duplicate_id_with_pattern", "Repeated history IDs count once", reference,
            listOf(first, first, second, second), expected,
        ))
        add(RecurringJourneyCase(
            "reference_already_in_history", "Reference ID counts once even when present in history",
            reference, history + reference, expected,
        ))
        add(RecurringJourneyCase(
            "invalid_candidate", "End before start excludes the candidate", reference,
            listOf(first, second.copy(endTimeMillis = second.startTimeMillis - 1)), null,
        ))
        add(RecurringJourneyCase(
            "ignore_invalid_extra", "An invalid extra entry does not change a valid pattern", reference,
            history + journey("invalid", 3, durationMinutes = -1), expected,
        ))
        add(RecurringJourneyCase(
            "invalid_reference", "End before start on the reference produces no pattern",
            reference.copy(endTimeMillis = reference.startTimeMillis - 1), history, null,
        ))
        add(RecurringJourneyCase(
            "zero_duration", "Equal start and end timestamps are allowed by the current contract",
            journey("reference", durationMinutes = 0),
            listOf(journey("first", 1, durationMinutes = 0), journey("second", 2, durationMinutes = 0)),
            expected.copy(averageDurationMinutes = 0),
        ))

        val variedHistory = listOf(
            journey("first", 1, 490, 20, TransportMode.PUBLIC_TRANSPORT),
            journey("second", 2, 500, 40, TransportMode.PUBLIC_TRANSPORT),
        )
        val variedExpected = expected.copy(typicalDepartureMinuteOfDay = 490, usualTransportMode = TransportMode.PUBLIC_TRANSPORT)
        add(RecurringJourneyCase(
            "output_fields", "Mean departure 08:10, mean duration 30 min, public transport majority",
            reference, variedHistory, variedExpected,
        ))
        add(RecurringJourneyCase(
            "history_reversed", "Reordering distinct IDs with an unambiguous mode majority preserves all fields",
            reference, variedHistory.reversed(), variedExpected,
        ))
        add(RecurringJourneyCase(
            "active_days_across_week", "Departure weekdays follow the fixed Melbourne time zone",
            journey("reference", 6), listOf(journey("first", 7), journey("second", 9)),
            expected.copy(activeDays = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)),
        ))
    }

    fun benchmarkHistory(size: Int, allMatching: Boolean): List<JourneySummary> = List(size) { index ->
        val item = journey("history_$index", day = index % 28)
        if (allMatching || index % 10 == 0) item else item.copy(startLocation = end, endLocation = start)
    }
}
