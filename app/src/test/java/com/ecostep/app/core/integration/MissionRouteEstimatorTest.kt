package com.ecostep.app.core.integration

import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.ExternalDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionRouteEstimatorTest {

    private val home = GeoPoint(-37.7983, 144.9610)
    private val cbd = GeoPoint(-37.8183, 144.9671)

    private class FakeRoutes(private val routes: List<RouteInfo>) : ExternalDataRepository {
        val requests = mutableListOf<Pair<GeoPoint, GeoPoint>>()
        override suspend fun getWeather(location: GeoPoint) = WeatherData(20.0, "Clear")
        override suspend fun getRouteOptions(start: GeoPoint, end: GeoPoint): List<RouteInfo> {
            requests += start to end
            return routes
        }

        override suspend fun getPublicTransportOptions(start: GeoPoint, end: GeoPoint) =
            emptyList<PublicTransportInfo>()
    }

    private val routes = FakeRoutes(
        listOf(
            RouteInfo(TransportMode.WALKING, 2_000.0, 1_500),
            RouteInfo(TransportMode.CYCLING, 2_300.0, 600),
            RouteInfo(TransportMode.CAR, 2_500.0, 400),
        ),
    )

    private fun estimator(external: ExternalDataRepository = routes) = MissionRouteEstimator(
        externalDataRepository = external,
        locate = { name, _ -> mapOf("Home street" to home, "CBD" to cbd)[name] },
        carbonCalculator = DefaultCarbonCalculator(),
        ecoPointsCalculator = DefaultEcoPointsCalculator(),
        searchCenter = { home },
    )

    @Test
    fun `each mode gets its own route distance, saving and points`() = runTest {
        val estimate = estimator().estimate(" Home street ", "CBD")

        assertEquals(listOf(home to cbd), routes.requests)
        val byMode = estimate.estimates.associateBy { it.mode }
        // Walking 2.0 km: 384 g saved -> 38 + full 20 bonus.
        assertEquals(384.0, byMode.getValue(TransportMode.WALKING).estimatedCarbonSavedGrams, 1e-9)
        assertEquals(58, byMode.getValue(TransportMode.WALKING).estimatedEcoPoints)
        // Public transport uses the 2.5 km road distance: 257.5 g -> 26 + 5.
        assertEquals(257.5, byMode.getValue(TransportMode.PUBLIC_TRANSPORT).estimatedCarbonSavedGrams, 1e-9)
        assertEquals(31, byMode.getValue(TransportMode.PUBLIC_TRANSPORT).estimatedEcoPoints)
        assertEquals(0, byMode.getValue(TransportMode.CAR).estimatedEcoPoints)
        assertEquals(2_300.0, estimate.distanceByMode.getValue(TransportMode.CYCLING), 1e-9)
        assertTrue(estimate.matches("Home street", " CBD "))
    }

    @Test
    fun `a short route earns only part of the mode bonus`() = runTest {
        val short = FakeRoutes(listOf(RouteInfo(TransportMode.WALKING, 26.0, 20), RouteInfo(TransportMode.CAR, 26.0, 5)))

        val walking = estimator(short).estimate("Home street", "CBD").estimates
            .single { it.mode == TransportMode.WALKING }

        assertEquals(1, walking.estimatedEcoPoints)
    }

    @Test
    fun `unknown places explain what to fix`() = runTest {
        val start = runCatching { estimator().estimate("Home", "CBD") }.exceptionOrNull()
        val end = runCatching { estimator().estimate("Home street", "Work") }.exceptionOrNull()

        assertTrue(start is MissionRouteException && start.message!!.contains("starting point"))
        assertTrue(end is MissionRouteException && end.message!!.contains("destination"))
    }
}
