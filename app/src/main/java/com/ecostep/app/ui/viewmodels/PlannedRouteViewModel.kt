package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlannedRouteUiState(
    val start: GeoPoint? = null,
    val destination: GeoPoint? = null,
    /** Route shape from start to destination; empty when no route could be planned. */
    val path: List<GeoPoint> = emptyList(),
    /** Short status for the tracking panel, e.g. "Walking route · 2.3 km". */
    val summary: String? = null,
    val isLoading: Boolean = false,
)

/**
 * Plans the route shown on the map for the active mission: from where the user is to the
 * mission's destination, for the mission's transport mode.
 */
class PlannedRouteViewModel(
    private val routeLookup: suspend (start: GeoPoint, end: GeoPoint) -> List<RouteInfo>,
    private val locate: suspend (placeName: String, near: GeoPoint) -> GeoPoint?,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlannedRouteUiState())
    val uiState: StateFlow<PlannedRouteUiState> = _uiState.asStateFlow()

    private var plannedKey: String? = null
    private var plannedDestinationName: String = ""
    private var plannedMode: TransportMode? = null
    private var lastPlanAtMillis = 0L
    private var planJob: Job? = null

    /** Plans once per [key] (e.g. the mission ID); repeated calls with the same key do nothing. */
    fun plan(key: String, destinationName: String, from: GeoPoint, mode: TransportMode?) {
        if (key == plannedKey) return
        plannedKey = key
        plannedDestinationName = destinationName
        plannedMode = mode
        launchPlan(from = from, knownDestination = null)
    }

    /**
     * Plans again from [current] when the user is clearly away from the planned route, e.g.
     * the first position was stale or they started somewhere else. Safe to call for every
     * location update: it is throttled and reuses the destination already found.
     */
    fun onLocation(current: GeoPoint) {
        if (plannedKey == null || planJob?.isActive == true) return
        val state = _uiState.value
        val start = state.start ?: return
        val offRoute = if (state.path.size >= 2) {
            distanceToPathMeters(current, state.path)
        } else {
            distanceMeters(current, start)
        }
        if (offRoute <= OFF_ROUTE_METERS) return
        if (clock() - lastPlanAtMillis < REPLAN_INTERVAL_MILLIS) return
        launchPlan(from = current, knownDestination = state.destination)
    }

    private fun launchPlan(from: GeoPoint, knownDestination: GeoPoint?) {
        val destinationName = plannedDestinationName
        val mode = plannedMode
        lastPlanAtMillis = clock()
        planJob?.cancel()
        _uiState.value = PlannedRouteUiState(start = from, isLoading = true)

        planJob = viewModelScope.launch {
            try {
                val destination = knownDestination ?: locate(destinationName, from)
                if (destination == null) {
                    _uiState.value = PlannedRouteUiState(
                        start = from,
                        summary = "Couldn't find \"$destinationName\" on the map. " +
                            "Edit the mission to use an address.",
                    )
                    return@launch
                }
                val route = selectRouteForMode(routeLookup(from, destination), mode)
                _uiState.value = PlannedRouteUiState(
                    start = from,
                    destination = destination,
                    path = route?.path.orEmpty(),
                    summary = route?.let(::routeSummary) ?: "Route unavailable right now.",
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                _uiState.value = PlannedRouteUiState(start = from, summary = "Route unavailable right now.")
            }
        }
    }

    fun clear() {
        plannedKey = null
        planJob?.cancel()
        _uiState.value = PlannedRouteUiState()
    }

    private companion object {
        const val OFF_ROUTE_METERS = 150.0
        const val REPLAN_INTERVAL_MILLIS = 60_000L
    }
}

/** Shortest distance from [point] to the polyline [path]. */
internal fun distanceToPathMeters(point: GeoPoint, path: List<GeoPoint>): Double =
    path.zipWithNext().minOf { (a, b) -> distanceToSegmentMeters(point, a, b) }

/** Local flat projection; accurate enough at route scale. */
private fun distanceToSegmentMeters(point: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
    val metersPerDegreeLat = 111_320.0
    val metersPerDegreeLon = 111_320.0 * cos(Math.toRadians(point.latitude))
    fun x(p: GeoPoint) = (p.longitude - point.longitude) * metersPerDegreeLon
    fun y(p: GeoPoint) = (p.latitude - point.latitude) * metersPerDegreeLat
    val ax = x(a)
    val ay = y(a)
    val dx = x(b) - ax
    val dy = y(b) - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0.0) 0.0 else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
    return hypot(ax + t * dx, ay + t * dy)
}

private fun distanceMeters(a: GeoPoint, b: GeoPoint): Double = distanceToSegmentMeters(a, b, b)

/**
 * Picks the route for the mission's mode. The routing service has no public-transport
 * profile, so public transport (or no mode) falls back to the walking route.
 */
internal fun selectRouteForMode(routes: List<RouteInfo>, mode: TransportMode?): RouteInfo? {
    val withShape = routes.filter { it.path.size >= 2 }
    val wanted = when (mode) {
        TransportMode.WALKING, TransportMode.CYCLING, TransportMode.CAR -> mode
        TransportMode.PUBLIC_TRANSPORT, TransportMode.UNKNOWN, null -> TransportMode.WALKING
    }
    return withShape.firstOrNull { it.mode == wanted }
        ?: withShape.firstOrNull { it.mode == TransportMode.WALKING }
        ?: withShape.firstOrNull()
}

internal fun routeSummary(route: RouteInfo): String {
    val mode = when (route.mode) {
        TransportMode.WALKING -> "Walking"
        TransportMode.CYCLING -> "Cycling"
        TransportMode.CAR -> "Driving"
        TransportMode.PUBLIC_TRANSPORT -> "Public transport"
        TransportMode.UNKNOWN -> "Planned"
    }
    val distance = if (route.distanceMeters >= 1000.0) {
        String.format(Locale.getDefault(), "%.1f km", route.distanceMeters / 1000.0)
    } else {
        "${route.distanceMeters.toInt()} m"
    }
    val minutes = (route.durationSeconds + 59) / 60
    return "$mode route · $distance · $minutes min"
}
