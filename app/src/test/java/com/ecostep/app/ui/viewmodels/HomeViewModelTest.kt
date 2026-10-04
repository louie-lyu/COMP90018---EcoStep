package com.ecostep.app.ui.viewmodels

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.mock.HomeRouteDataSource
import com.ecostep.app.ui.mock.HomeRouteException
import com.ecostep.app.ui.mock.HomeRouteOption
import com.ecostep.app.ui.mock.HomeRouteQuery
import com.ecostep.app.ui.mock.HomeRouteSearchResult
import com.ecostep.app.ui.mock.MockHomeRouteDataSource
import com.ecostep.app.ui.mock.MockMissionRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val melbourne = GeoPoint(-37.8136, 144.9631)
    private val carlton = GeoPoint(-37.7983, 144.9610)

    private class FakeExternalData(
        private val failWeather: Boolean = false,
    ) : ExternalDataRepository {
        val weatherRequests = mutableListOf<GeoPoint>()

        override suspend fun getWeather(location: GeoPoint): WeatherData {
            weatherRequests += location
            if (failWeather) error("offline")
            return WeatherData(temperatureCelsius = 18.0, conditions = "Clear")
        }

        override suspend fun getRouteOptions(start: GeoPoint, end: GeoPoint): List<RouteInfo> =
            emptyList()

        override suspend fun getPublicTransportOptions(
            start: GeoPoint,
            end: GeoPoint,
        ): List<PublicTransportInfo> = emptyList()
    }

    private fun viewModel(externalData: ExternalDataRepository) = HomeViewModel(
        externalDataRepository = externalData,
        homeRouteDataSource = MockHomeRouteDataSource(),
        missionRepository = MockMissionRepository(),
    )

    @Test
    fun `weather uses the fallback location until a live fix arrives`() {
        val externalData = FakeExternalData()
        viewModel(externalData)

        assertEquals(listOf(melbourne), externalData.weatherRequests)
    }

    @Test
    fun `live location refreshes weather for the device position`() {
        val externalData = FakeExternalData()
        val viewModel = viewModel(externalData)

        viewModel.updateCurrentLocation(carlton)

        assertEquals(carlton, externalData.weatherRequests.last())
        assertEquals(carlton, viewModel.uiState.value.currentLocation)
        assertEquals("Clear", viewModel.uiState.value.weather?.conditions)
    }

    @Test
    fun `small moves do not trigger another weather request`() {
        val externalData = FakeExternalData()
        val viewModel = viewModel(externalData)

        viewModel.updateCurrentLocation(carlton)
        viewModel.updateCurrentLocation(GeoPoint(carlton.latitude + 0.0005, carlton.longitude))
        viewModel.updateCurrentLocation(GeoPoint(carlton.latitude + 0.001, carlton.longitude - 0.001))

        assertEquals(2, externalData.weatherRequests.size)
        assertEquals(
            GeoPoint(carlton.latitude + 0.001, carlton.longitude - 0.001),
            viewModel.uiState.value.currentLocation,
        )
    }

    @Test
    fun `moving far refreshes weather again`() {
        val externalData = FakeExternalData()
        val viewModel = viewModel(externalData)

        viewModel.updateCurrentLocation(carlton)
        viewModel.updateCurrentLocation(GeoPoint(carlton.latitude - 0.05, carlton.longitude))

        assertEquals(3, externalData.weatherRequests.size)
    }

    @Test
    fun `weather failure shows an error instead of crashing`() {
        val viewModel = viewModel(FakeExternalData(failWeather = true))

        viewModel.updateCurrentLocation(carlton)

        val state = viewModel.uiState.value
        assertNull(state.weather)
        assertFalse(state.isWeatherLoading)
        assertEquals("Weather is currently unavailable.", state.weatherErrorMessage)
    }

    private class FakeRouteSource(
        private val respond: suspend (HomeRouteQuery) -> HomeRouteSearchResult,
    ) : HomeRouteDataSource {
        val queries = mutableListOf<HomeRouteQuery>()

        override suspend fun getRouteOptions(query: HomeRouteQuery): HomeRouteSearchResult {
            queries += query
            return respond(query)
        }
    }

    private val walk = HomeRouteOption(RouteInfo(TransportMode.WALKING, 1_000.0, 700), 0.19, 0)

    private fun viewModel(routes: HomeRouteDataSource) = HomeViewModel(
        externalDataRepository = FakeExternalData(),
        homeRouteDataSource = routes,
        missionRepository = MockMissionRepository(),
    )

    @Test
    fun `search sends the typed places and the live position`() {
        val routes = FakeRouteSource { HomeRouteSearchResult(listOf(walk)) }
        val viewModel = viewModel(routes)
        viewModel.updateCurrentLocation(carlton)
        viewModel.updateDestination("University of Melbourne")

        viewModel.findRoutes()

        val query = routes.queries.single()
        assertEquals("University of Melbourne", query.destinationText)
        assertEquals(carlton, query.currentLocation)
        assertTrue(query.isCurrentLocationLive)
    }

    @Test
    fun `place lookup problems are shown as they are`() {
        val viewModel = viewModel(FakeRouteSource { throw HomeRouteException("Could not find that destination.") })
        viewModel.updateDestination("Nowhere")

        viewModel.findRoutes()

        assertEquals("Could not find that destination.", viewModel.uiState.value.routeErrorMessage)
        assertFalse(viewModel.uiState.value.isRouteLoading)
    }

    @Test
    fun `routes still show when the timetable is unavailable`() {
        val viewModel = viewModel(
            FakeRouteSource { HomeRouteSearchResult(listOf(walk), publicTransportUnavailable = true) },
        )
        viewModel.updateDestination("Uni")

        viewModel.findRoutes()

        val state = viewModel.uiState.value
        assertEquals(listOf(walk), state.routeOptions)
        assertTrue(state.isRoutePlannerVisible)
        assertEquals("Public transport times are unavailable right now.", state.publicTransportErrorMessage)
    }

    @Test
    fun `an older search cannot overwrite a newer one`() {
        val firstSearch = CompletableDeferred<HomeRouteSearchResult>()
        val cycle = walk.copy(route = walk.route.copy(mode = TransportMode.CYCLING))
        val routes = FakeRouteSource { query ->
            if (query.destinationText == "Old") firstSearch.await() else HomeRouteSearchResult(listOf(cycle))
        }
        val viewModel = viewModel(routes)
        viewModel.updateDestination("Old")
        viewModel.findRoutes()

        viewModel.updateDestination("New")
        viewModel.findRoutes()
        firstSearch.complete(HomeRouteSearchResult(listOf(walk)))

        assertEquals(listOf(cycle), viewModel.uiState.value.routeOptions)
    }

    @Test
    fun `clearing a free journey resets the confirmed route`() {
        val viewModel = viewModel(FakeExternalData())
        viewModel.updateDestination("University of Melbourne")
        viewModel.findRoutes()
        viewModel.selectTransportMode(TransportMode.WALKING)
        viewModel.confirmDirections()
        assertTrue(viewModel.uiState.value.isDirectionsConfirmed)

        viewModel.clearFreeJourney()

        val state = viewModel.uiState.value
        assertFalse(state.isDirectionsConfirmed)
        assertNull(state.selectedRouteOption)
        assertEquals("", state.destination)
    }
}
