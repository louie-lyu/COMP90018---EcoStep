package com.ecostep.app.network.publictransport

import com.ecostep.app.data.model.PublicTransportInfo
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Maps provider-specific Transitous itineraries into EcoStep's shared model.
 */
internal fun TransitousResponse.toPublicTransportInfoList():
        List<PublicTransportInfo> {
    return itineraries.mapNotNull { itinerary ->
        itinerary.toPublicTransportInfoOrNull()
    }
}

private fun TransitousItinerary.toPublicTransportInfoOrNull():
        PublicTransportInfo? {
    if (duration <= 0L) {
        return null
    }

    val mappedLegs = legs.mapNotNull { leg ->
        val lineLabel = leg.toLineLabelOrNull()
            ?: return@mapNotNull null

        leg to lineLabel
    }

    if (mappedLegs.isEmpty()) {
        return null
    }

    val departureTimeMillis = try {
        Instant.parse(
            mappedLegs.first().first.startTime,
        ).toEpochMilli()
    } catch (_: DateTimeParseException) {
        return null
    }

    val combinedLine = mappedLegs
        .map { it.second }
        .distinct()
        .joinToString(separator = " → ")

    return PublicTransportInfo(
        line = combinedLine,
        departureTimeMillis = departureTimeMillis,
        estimatedDurationSeconds = duration,
    )
}

private fun TransitousLeg.toLineLabelOrNull(): String? {
    val modeLabel = when (mode.uppercase(Locale.ROOT)) {
        "TRAM",
        "LIGHT_RAIL",
            -> "Tram"

        "BUS",
        "COACH",
            -> "Bus"

        "SUBWAY",
        "SUBURBAN",
        "RAIL",
        "REGIONAL_RAIL",
        "METRO",
            -> "Train"

        else -> return null
    }

    val routeName = listOf(
        displayName,
        routeShortName,
        routeLongName,
    ).firstOrNull { candidate ->
        !candidate.isNullOrBlank()
    }?.trim() ?: return null

    return if (
        routeName.startsWith(
            prefix = "$modeLabel ",
            ignoreCase = true,
        )
    ) {
        routeName
    } else {
        "$modeLabel $routeName"
    }
}
