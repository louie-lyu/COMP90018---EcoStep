package com.ecostep.app.ui.adapters

import com.ecostep.app.algorithm.CarbonCalculator
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.ui.mock.CURRENT_LOCATION_LABEL
import com.ecostep.app.ui.mock.HomeRouteDataSource
import com.ecostep.app.ui.mock.HomeRouteException
import com.ecostep.app.ui.mock.HomeRouteOption
import com.ecostep.app.ui.mock.HomeRouteQuery
import com.ecostep.app.ui.mock.HomeRouteSearchResult
import com.ecostep.app.ui.viewmodels.selectRouteForMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Production Home route search: resolves the typed place names, asks the route and
 * public-transport services for the same start/end in parallel, and estimates each option's
 * saving with the shared carbon calculator.
 */
class RepositoryHomeRouteDataSource(
    private val externalDataRepository: ExternalDataRepository,
    private val locate: suspend (placeName: String, near: GeoPoint) -> GeoPoint?,
    private val carbonCalculator: CarbonCalculator,
    private val clock: () -> Long = System::currentTimeMillis,
) : HomeRouteDataSource {

    override suspend fun getRouteOptions(query: HomeRouteQuery): HomeRouteSearchResult {
        val start = resolveStart(query)
        val end = locate(query.destinationText.trim(), start)
            ?: throw HomeRouteException("Could not find that destination.")

        return coroutineScope {
            // A timetable failure must not cancel or fail the route search.
            val departures = async { upcomingDepartures(start, end) }
            val routes = externalDataRepository.getRouteOptions(start, end)
                .filter { it.mode != TransportMode.PUBLIC_TRANSPORT && it.mode != TransportMode.UNKNOWN }
                .distinctBy { it.mode }
            val transit = departures.await()

            HomeRouteSearchResult(
                routes = routes.map(::toOption) + listOfNotNull(transitOption(routes, transit)),
                publicTransportOptions = transit.orEmpty(),
                publicTransportUnavailable = transit == null,
            )
        }
    }

    private suspend fun resolveStart(query: HomeRouteQuery): GeoPoint {
        val text = query.startText.trim()
        if (text.isEmpty() || text.equals(CURRENT_LOCATION_LABEL, ignoreCase = true)) {
            if (!query.isCurrentLocationLive) {
                throw HomeRouteException(
                    "Your current location isn't available yet. Enter a starting point instead.",
                )
            }
            return query.currentLocation
        }
        return locate(text, query.currentLocation)
            ?: throw HomeRouteException("Could not find that starting point.")
    }

    /** Departures that have not left yet, earliest first; null when the service failed. */
    private suspend fun upcomingDepartures(start: GeoPoint, end: GeoPoint): List<PublicTransportInfo>? =
        try {
            val now = clock()
            externalDataRepository.getPublicTransportOptions(start, end)
                .filter { it.departureTimeMillis >= now }
                .sortedBy { it.departureTimeMillis }
                .take(MAX_DEPARTURES)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    /**
     * The routing service has no public-transport profile, so the transit option uses the
     * road distance for its estimate and the next departure's travel time.
     */
    private fun transitOption(
        routes: List<RouteInfo>,
        departures: List<PublicTransportInfo>?,
    ): HomeRouteOption? {
        val next = departures?.firstOrNull() ?: return null
        val reference = routes.firstOrNull { it.mode == TransportMode.CAR }
            ?: routes.firstOrNull { it.mode == TransportMode.WALKING }
            ?: routes.firstOrNull()
            ?: return null
        return toOption(
            RouteInfo(
                mode = TransportMode.PUBLIC_TRANSPORT,
                distanceMeters = reference.distanceMeters,
                durationSeconds = next.estimatedDurationSeconds,
                path = selectRouteForMode(routes, TransportMode.PUBLIC_TRANSPORT)?.path.orEmpty(),
            ),
        )
    }

    private fun toOption(route: RouteInfo) = HomeRouteOption(
        route = route,
        estimatedCarbonSavedKg =
            carbonSavedVersusCarGrams(carbonCalculator, route.distanceMeters, route.mode) / 1000.0,
        // DefaultEcoPointsCalculator only rewards completed missions; a free route earns none.
        estimatedEcoPoints = 0,
    )

    private companion object {
        const val MAX_DEPARTURES = 3
    }
}

/**
 * Saving of travelling [distanceMeters] by [mode] instead of by car, taken from the
 * calculator's lower-carbon alternatives for an equivalent car trip.
 */
internal fun carbonSavedVersusCarGrams(
    calculator: CarbonCalculator,
    distanceMeters: Double,
    mode: TransportMode,
): Double {
    if (mode == TransportMode.CAR || mode == TransportMode.UNKNOWN) return 0.0
    val carTrip = JourneySummary(
        journeyId = "route-estimate",
        userId = "",
        startLocation = GeoPoint(0.0, 0.0),
        endLocation = GeoPoint(0.0, 0.0),
        startTimeMillis = 0L,
        endTimeMillis = 0L,
        distanceMeters = distanceMeters,
        transportMode = TransportMode.CAR,
    )
    return calculator.calculate(carTrip).lowerCarbonAlternatives
        .firstOrNull { it.mode == mode }
        ?.savingsGrams
        ?: 0.0
}
