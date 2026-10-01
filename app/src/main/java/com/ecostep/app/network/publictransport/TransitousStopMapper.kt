package com.ecostep.app.network.publictransport

import com.ecostep.app.algorithm.TransitStop
import java.util.Locale

private val TRANSIT_MODES = setOf(
    "BUS",
    "COACH",
    "TRAM",
    "LIGHT_RAIL",
    "SUBWAY",
    "SUBURBAN",
    "RAIL",
    "REGIONAL_RAIL",
    "METRO",
)

internal fun TransitousResponse.toTransitStopRoutes(): List<List<TransitStop>> =
    itineraries.flatMap { itinerary ->
        itinerary.legs.mapNotNull { leg ->
            if (leg.mode.uppercase(Locale.ROOT) !in TRANSIT_MODES) {
                return@mapNotNull null
            }

            val places = listOfNotNull(leg.from) +
                    (leg.intermediateStops ?: emptyList()) +
                    listOfNotNull(leg.to)

            val stops = places.mapNotNull { place ->
                if (
                    !place.lat.isFinite() ||
                    !place.lon.isFinite() ||
                    place.lat !in -90.0..90.0 ||
                    place.lon !in -180.0..180.0
                ) {
                    null
                } else {
                    TransitStop(place.lat, place.lon)
                }
            }

            stops.takeIf { it.size >= 2 }
        }
    }