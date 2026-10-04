package com.ecostep.app.ui.viewmodels

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlannedRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val here = GeoPoint(-37.7983, 144.9610)
    private val uni = GeoPoint(-37.8183, 144.9650)
    private val path = listOf(here, GeoPoint(-37.808, 144.963), uni)

    private fun route(mode: TransportMode, withPath: Boolean = true) =
        RouteInfo(mode, distanceMeters = 2_300.0, durationSeconds = 1_700, path = if (withPath) path else emptyList())

    private var lookups = 0

    private fun viewModel(
        routes: List<RouteInfo> = listOf(route(TransportMode.WALKING), route(TransportMode.CYCLING)),
        found: GeoPoint? = uni,
    ) = PlannedRouteViewModel(
        routeLookup = { _, _ -> lookups++; routes },
        locate = { _, _ -> found },
    )

    @Test
    fun `plans from the current position to the located destination`() {
        val viewModel = viewModel()

        viewModel.plan("m1", "University of Melbourne", here, TransportMode.CYCLING)

        val state = viewModel.uiState.value
        assertEquals(here, state.start)
        assertEquals(uni, state.destination)
        assertEquals(path, state.path)
        assertEquals("Cycling route · 2.3 km · 29 min", state.summary)
    }

    @Test
    fun `unknown destination name explains how to fix it`() {
        val viewModel = viewModel(found = null)

        viewModel.plan("m1", "Home", here, TransportMode.WALKING)

        val state = viewModel.uiState.value
        assertTrue(state.path.isEmpty())
        assertNull(state.destination)
        assertTrue(state.summary!!.contains("\"Home\""))
    }

    @Test
    fun `the same mission is planned only once`() {
        val viewModel = viewModel()

        viewModel.plan("m1", "Uni", here, TransportMode.WALKING)
        viewModel.plan("m1", "Uni", GeoPoint(-37.0, 144.0), TransportMode.WALKING)

        assertEquals(1, lookups)
        viewModel.clear()
        viewModel.plan("m1", "Uni", here, TransportMode.WALKING)
        assertEquals(2, lookups)
    }

    @Test
    fun `a stale first position is replaced once the user is clearly elsewhere`() {
        // The emulator case: the plan starts from an old CBD fix, then the device appears at
        // the university, 1.7 km away.
        val staleCbd = GeoPoint(-37.8130, 144.9628)
        var now = 0L
        val starts = mutableListOf<GeoPoint>()
        val viewModel = PlannedRouteViewModel(
            routeLookup = { start, end ->
                starts += start
                listOf(RouteInfo(TransportMode.WALKING, 2_300.0, 1_700, path = listOf(start, end)))
            },
            locate = { _, _ -> uni },
            clock = { now },
        )
        viewModel.plan("m1", "Uni", staleCbd, TransportMode.WALKING)

        now = 61_000L
        viewModel.onLocation(here)

        assertEquals(listOf(staleCbd, here), starts)
        assertEquals(here, viewModel.uiState.value.start)
        assertEquals(uni, viewModel.uiState.value.destination)
    }

    @Test
    fun `being on the planned route or replanning too often does not replan`() {
        var now = 61_000L
        val viewModel = PlannedRouteViewModel(
            routeLookup = { _, _ -> lookups++; listOf(route(TransportMode.WALKING)) },
            locate = { _, _ -> uni },
            clock = { now },
        )
        viewModel.plan("m1", "Uni", here, TransportMode.WALKING)

        // Midway along the path, between its first two points.
        viewModel.onLocation(GeoPoint(-37.8030, 144.9620))
        assertEquals(1, lookups)

        // Far away, but within a minute of the last plan.
        now += 30_000L
        viewModel.onLocation(GeoPoint(-37.85, 145.0))
        assertEquals(1, lookups)
    }

    @Test
    fun `distance to a route counts the segments, not only its corners`() {
        val straight = listOf(GeoPoint(-37.80, 144.96), GeoPoint(-37.82, 144.96))

        assertTrue(distanceToPathMeters(GeoPoint(-37.81, 144.96), straight) < 1.0)
        assertTrue(distanceToPathMeters(GeoPoint(-37.81, 144.962), straight) in 170.0..180.0)
    }

    @Test
    fun `public transport falls back to the walking route`() {
        val chosen = selectRouteForMode(
            listOf(route(TransportMode.CAR), route(TransportMode.WALKING)),
            TransportMode.PUBLIC_TRANSPORT,
        )

        assertEquals(TransportMode.WALKING, chosen?.mode)
    }

    @Test
    fun `routes without a shape are never drawn`() {
        assertNull(selectRouteForMode(listOf(route(TransportMode.WALKING, withPath = false)), TransportMode.WALKING))
        assertEquals(
            TransportMode.WALKING,
            selectRouteForMode(
                listOf(route(TransportMode.CYCLING, withPath = false), route(TransportMode.WALKING)),
                TransportMode.CYCLING,
            )?.mode,
        )
    }
}
