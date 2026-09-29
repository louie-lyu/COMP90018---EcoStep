package com.ecostep.app.ui.mock

import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode

/**
 * UI data needed to preview a planned route.
 *
 * Carbon savings and EcoPoints are temporary estimates while
 * the production planning calculations are unavailable.
 */
data class HomeRouteOption(
    val route: RouteInfo,
    val estimatedCarbonSavedKg: Double,
    val estimatedEcoPoints: Int,
)

/**
 * Temporary route source used by the Home screen.
 *
 * Replace this with the production route and impact data
 * when those implementations are available.
 */
interface HomeRouteDataSource {
    suspend fun getRouteOptions(): List<HomeRouteOption>
}

/**
 * Mock route previews for Home screen development.
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