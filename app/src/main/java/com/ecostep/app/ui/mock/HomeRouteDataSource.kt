package com.ecostep.app.ui.mock

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode

/** Start-field text that means "use the device's live position". */
const val CURRENT_LOCATION_LABEL = "Current location"

/**
 * UI data used to compare a planned route and its estimated impact.
 *
 * Production values come from the shared carbon calculator. EcoPoints are only awarded for
 * completed missions, so a free route's [estimatedEcoPoints] is 0.
 */
data class HomeRouteOption(
    val route: RouteInfo,
    val estimatedCarbonSavedKg: Double,
    val estimatedEcoPoints: Int,
)

/** What the user typed in the route planner, plus where the device is. */
data class HomeRouteQuery(
    val startText: String,
    val destinationText: String,
    /** Live position, or the fallback map centre when [isCurrentLocationLive] is false. */
    val currentLocation: GeoPoint,
    val isCurrentLocationLive: Boolean,
)

data class HomeRouteSearchResult(
    val routes: List<HomeRouteOption>,
    /** Upcoming departures, earliest first. */
    val publicTransportOptions: List<PublicTransportInfo> = emptyList(),
    /** True when the timetable service failed; the other routes are still usable. */
    val publicTransportUnavailable: Boolean = false,
)

/** A search problem the user can fix, e.g. a place name that could not be found. */
class HomeRouteException(message: String) : Exception(message)

/** Supplies route options required by HomeScreen. */
interface HomeRouteDataSource {
    /** @throws HomeRouteException with a user-facing message when a place cannot be resolved. */
    suspend fun getRouteOptions(query: HomeRouteQuery): HomeRouteSearchResult
}

/**
 * Supplies fixed route options for previews and UI tests only. Production navigation uses
 * RepositoryHomeRouteDataSource.
 */
class MockHomeRouteDataSource : HomeRouteDataSource {

    override suspend fun getRouteOptions(query: HomeRouteQuery): HomeRouteSearchResult {
        return HomeRouteSearchResult(
            routes = listOf(
                HomeRouteOption(
                    route = RouteInfo(
                        mode = TransportMode.WALKING,
                        distanceMeters = 3200.0,
                        durationSeconds = 2400,
                    ),
                    estimatedCarbonSavedKg = 0.64,
                    estimatedEcoPoints = 160,
                ),
                HomeRouteOption(
                    route = RouteInfo(
                        mode = TransportMode.CYCLING,
                        distanceMeters = 3200.0,
                        durationSeconds = 900,
                    ),
                    estimatedCarbonSavedKg = 0.58,
                    estimatedEcoPoints = 140,
                ),
                HomeRouteOption(
                    route = RouteInfo(
                        mode = TransportMode.PUBLIC_TRANSPORT,
                        distanceMeters = 4200.0,
                        durationSeconds = 1080,
                    ),
                    estimatedCarbonSavedKg = 0.31,
                    estimatedEcoPoints = 120,
                ),
                HomeRouteOption(
                    route = RouteInfo(
                        mode = TransportMode.CAR,
                        distanceMeters = 3900.0,
                        durationSeconds = 720,
                    ),
                    estimatedCarbonSavedKg = 0.0,
                    estimatedEcoPoints = 0,
                ),
            ),
        )
    }
}
