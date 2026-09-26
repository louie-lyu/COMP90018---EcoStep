package com.ecostep.app.algorithm

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

class DefaultRecurringJourneyDetector(
    private val locationToleranceMeters: Double = 200.0,
    private val departureToleranceMinutes: Int = 60,
    private val minimumOccurrences: Int = 3,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : RecurringJourneyDetector {

    init {
        require(
            locationToleranceMeters.isFinite() &&
                    locationToleranceMeters > 0.0,
        ) {
            "Location tolerance must be finite and positive."
        }

        require(
            departureToleranceMinutes in 0..720,
        ) {
            "Departure tolerance must be between 0 and 720 minutes."
        }

        require(minimumOccurrences >= 2) {
            "Minimum occurrences must be at least two."
        }
    }

    override fun detect(
        referenceJourney: JourneySummary,
        journeyHistory: List<JourneySummary>,
    ): RecurringJourneyPattern? {
        val matchingJourneys =
            (journeyHistory + referenceJourney)
                .distinctBy { it.journeyId }
                .filter {
                    matchesReference(
                        reference = referenceJourney,
                        candidate = it,
                    )
                }

        if (matchingJourneys.size < minimumOccurrences) {
            return null
        }

        val averageDurationMinutes =
            matchingJourneys
                .map {
                    (it.endTimeMillis - it.startTimeMillis)
                        .coerceAtLeast(0L) / MILLIS_PER_MINUTE.toDouble()
                }
                .average()
                .roundToLong()

        val usualTransportMode =
            matchingJourneys
                .groupingBy { it.transportMode }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: referenceJourney.transportMode

        return RecurringJourneyPattern(
            occurrenceCount = matchingJourneys.size,
            typicalDepartureMinuteOfDay =
                calculateTypicalDepartureMinute(
                    reference = referenceJourney,
                    matchingJourneys = matchingJourneys,
                ),
            averageDurationMinutes = averageDurationMinutes,
            activeDays =
                matchingJourneys
                    .map { departureDateTime(it).dayOfWeek }
                    .toSet(),
            usualTransportMode = usualTransportMode,
        )
    }

    private fun matchesReference(
        reference: JourneySummary,
        candidate: JourneySummary,
    ): Boolean {
        if (
            reference.endTimeMillis < reference.startTimeMillis ||
            candidate.endTimeMillis < candidate.startTimeMillis
        ) {
            return false
        }

        val startMatches =
            distanceMeters(
                first = reference.startLocation,
                second = candidate.startLocation,
            ) <= locationToleranceMeters

        val endMatches =
            distanceMeters(
                first = reference.endLocation,
                second = candidate.endLocation,
            ) <= locationToleranceMeters

        val departureMatches =
            minuteDifference(
                first = departureMinuteOfDay(reference),
                second = departureMinuteOfDay(candidate),
            ) <= departureToleranceMinutes

        return startMatches &&
                endMatches &&
                departureMatches
    }

    private fun calculateTypicalDepartureMinute(
        reference: JourneySummary,
        matchingJourneys: List<JourneySummary>,
    ): Int {
        val referenceMinute =
            departureMinuteOfDay(reference)

        val averageOffset =
            matchingJourneys
                .map {
                    signedMinuteDifference(
                        reference = referenceMinute,
                        value = departureMinuteOfDay(it),
                    )
                }
                .average()
                .roundToInt()

        return Math.floorMod(
            referenceMinute + averageOffset,
            MINUTES_PER_DAY,
        )
    }

    private fun departureMinuteOfDay(
        journey: JourneySummary,
    ): Int {
        val dateTime = departureDateTime(journey)

        return dateTime.hour * 60 +
                dateTime.minute
    }

    private fun departureDateTime(
        journey: JourneySummary,
    ) = Instant
        .ofEpochMilli(journey.startTimeMillis)
        .atZone(zoneId)

    private fun minuteDifference(
        first: Int,
        second: Int,
    ): Int {
        val directDifference = abs(first - second)

        return minOf(
            directDifference,
            MINUTES_PER_DAY - directDifference,
        )
    }

    private fun signedMinuteDifference(
        reference: Int,
        value: Int,
    ): Int {
        var difference = value - reference

        if (difference > MINUTES_PER_DAY / 2) {
            difference -= MINUTES_PER_DAY
        } else if (difference < -MINUTES_PER_DAY / 2) {
            difference += MINUTES_PER_DAY
        }

        return difference
    }

    private fun distanceMeters(
        first: GeoPoint,
        second: GeoPoint,
    ): Double {
        val firstLatitude =
            Math.toRadians(first.latitude)
        val secondLatitude =
            Math.toRadians(second.latitude)

        val latitudeDifference =
            Math.toRadians(
                second.latitude - first.latitude,
            )
        val longitudeDifference =
            Math.toRadians(
                second.longitude - first.longitude,
            )

        val haversine =
            (
                    sin(latitudeDifference / 2.0) *
                            sin(latitudeDifference / 2.0) +
                            cos(firstLatitude) *
                            cos(secondLatitude) *
                            sin(longitudeDifference / 2.0) *
                            sin(longitudeDifference / 2.0)
                    ).coerceIn(0.0, 1.0)

        val angularDistance =
            2.0 * atan2(
                sqrt(haversine),
                sqrt(1.0 - haversine),
            )

        return EARTH_RADIUS_METERS *
                angularDistance
    }

    private companion object {
        const val EARTH_RADIUS_METERS =
            6_371_000.0

        const val MINUTES_PER_DAY =
            24 * 60

        const val MILLIS_PER_MINUTE =
            60_000L
    }
}