package com.ecostep.app.ui.mock

import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode

/**
 * UI data used to compare a planned route and its estimated impact.
 *
 * TODO(Algorithm): Replace the mock EcoPoints and carbon-saving values
 * with results from the production calculators using route distance
 * and transport mode.
 */
data class HomeRouteOption(
    val route: RouteInfo,
    val estimatedCarbonSavedKg: Double,
    val estimatedEcoPoints: Int,
)

/**
 * Supplies route options required by HomeScreen.
 *
 * TODO(Routing): Update this interface to accept the selected origin
 * and destination, then return route distance, duration and geometry
 * from the production routing module.
 */
interface HomeRouteDataSource {
    suspend fun getRouteOptions(): List<HomeRouteOption>
}

/**
 * Supplies fixed route options for HomeScreen prototype development.
 *
 * TODO(Routing/Algorithm): Replace this mock implementation with
 * production routing results and calculate each option's EcoPoints
 * and carbon savings through the production calculators.
 */
class MockHomeRouteDataSource : HomeRouteDataSource {

    override suspend fun getRouteOptions(): List<HomeRouteOption> {
        return listOf(
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
        )
    }
}