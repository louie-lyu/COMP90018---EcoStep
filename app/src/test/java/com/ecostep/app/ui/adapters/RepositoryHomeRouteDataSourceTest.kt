package com.ecostep.app.ui.adapters

import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.ui.mock.CURRENT_LOCATION_LABEL
import com.ecostep.app.ui.mock.HomeRouteException
import com.ecostep.app.ui.mock.HomeRouteQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryHomeRouteDataSourceTest {

    private val here = GeoPoint(-37.7983, 144.9610)
    private val flinders = GeoPoint(-37.8183, 144.9671)
    private val uni = GeoPoint(-37.7964, 144.9612)
    private val now = 1_000_000L
    private val path = listOf(here, GeoPoint(-37.808, 144.963), uni)

    private class FakeExternalData(
        val routes: List<RouteInfo>,
        val departures: List<PublicTransportInfo> = emptyList(),
        val failTransit: Boolean = false,
    ) : ExternalDataRepository {
        val routeRequests = mutableListOf<Pair<GeoPoint, GeoPoint>>()
        val transitRequests = mutableListOf<Pair<GeoPoint, GeoPoint>>()

        override suspend fun getWeather(location: GeoPoint) = WeatherData(20.0, "Clear")

        override suspend fun getRouteOptions(start: GeoPoint, end: GeoPoint): List<RouteInfo> {
            routeRequests += start to end
            return routes
        }

        override suspend fun getPublicTransportOptions(
            start: GeoPoint,
            end: GeoPoint,
        ): List<PublicTransportInfo> {
            transitRequests += start to end
            if (failTransit) error("timetable offline")
            return departures
        }
    }

    private val defaultRoutes = listOf(
        RouteInfo(TransportMode.WALKING, 2_000.0, 1_500, path),
        RouteInfo(TransportMode.CYCLING, 2_100.0, 600, path),
        RouteInfo(TransportMode.CAR, 2_500.0, 400, path),
    )

    private val places = mapOf("Flinders Street" to flinders, "University of Melbourne" to uni)

    private fun source(externalData: FakeExternalData) = RepositoryHomeRouteDataSource(
        externalDataRepository = externalData,
        locate = { name, _ -> places[name] },
        carbonCalculator = DefaultCarbonCalculator(),
        clock = { now },
    )

    private fun query(
        start: String = CURRENT_LOCATION_LABEL,
        destination: String = "University of Melbourne",
        live: Boolean = true,
    ) = HomeRouteQuery(start, destination, here, live)

    @Test
    fun `current location is used as the start`() = runTest {
        val externalData = FakeExternalData(defaultRoutes)

        source(externalData).getRouteOptions(query())

        assertEquals(listOf(here to uni), externalData.routeRequests)
        assertEquals(listOf(here to uni), externalData.transitRequests)
    }

    @Test
    fun `typed start is geocoded`() = runTest {
        val externalData = FakeExternalData(defaultRoutes)

        source(externalData).getRouteOptions(query(start = "Flinders Street"))

        assertEquals(listOf(flinders to uni), externalData.routeRequests)
    }

    @Test
    fun `unknown places fail with a clear message instead of a fake coordinate`() = runTest {
        val source = source(FakeExternalData(defaultRoutes))

        val badStart = runCatching { source.getRouteOptions(query(start = "Nowhere")) }.exceptionOrNull()
        val badEnd = runCatching { source.getRouteOptions(query(destination = "Nowhere")) }.exceptionOrNull()

        assertEquals("Could not find that starting point.", (badStart as HomeRouteException).message)
        assertEquals("Could not find that destination.", (badEnd as HomeRouteException).message)
    }

    @Test
    fun `current location without a live fix is refused`() = runTest {
        val externalData = FakeExternalData(defaultRoutes)

        val error = runCatching { source(externalData).getRouteOptions(query(live = false)) }.exceptionOrNull()

        assertTrue(error is HomeRouteException)
        assertTrue(externalData.routeRequests.isEmpty())
    }

    @Test
    fun `estimates come from the carbon calculator relative to driving`() = runTest {
        val result = source(FakeExternalData(defaultRoutes)).getRouteOptions(query())

        val byMode = result.routes.associateBy { it.route.mode }
        // 2.0 km walked instead of driven at 192 g/km.
        assertEquals(0.384, byMode.getValue(TransportMode.WALKING).estimatedCarbonSavedKg, 1e-9)
        assertEquals(0.0, byMode.getValue(TransportMode.CAR).estimatedCarbonSavedKg, 1e-9)
        assertTrue(result.routes.all { it.estimatedEcoPoints == 0 })
        assertEquals(path, byMode.getValue(TransportMode.WALKING).route.path)
    }

    @Test
    fun `public transport option uses upcoming departures only`() = runTest {
        val departures = listOf(
            PublicTransportInfo("Tram 19", now + 600_000, 900),
            PublicTransportInfo("Tram 1", now - 60_000, 800),
            PublicTransportInfo("Tram 6", now + 120_000, 1_000),
            PublicTransportInfo("Tram 16", now + 900_000, 1_100),
            PublicTransportInfo("Tram 3", now + 1_200_000, 1_200),
        )

        val result = source(FakeExternalData(defaultRoutes, departures)).getRouteOptions(query())

        assertEquals(listOf("Tram 6", "Tram 19", "Tram 16"), result.publicTransportOptions.map { it.line })
        val transit = result.routes.single { it.route.mode == TransportMode.PUBLIC_TRANSPORT }
        assertEquals(1_000L, transit.route.durationSeconds)
        // Road distance 2.5 km at (192 - 89) g/km.
        assertEquals(0.2575, transit.estimatedCarbonSavedKg, 1e-9)
        assertFalse(result.publicTransportUnavailable)
    }

    @Test
    fun `timetable failure keeps the other routes`() = runTest {
        val result = source(FakeExternalData(defaultRoutes, failTransit = true)).getRouteOptions(query())

        assertEquals(3, result.routes.size)
        assertTrue(result.publicTransportUnavailable)
        assertNull(result.routes.firstOrNull { it.route.mode == TransportMode.PUBLIC_TRANSPORT })
    }
}
