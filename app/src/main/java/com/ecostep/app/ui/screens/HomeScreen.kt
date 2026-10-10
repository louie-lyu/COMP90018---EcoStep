package com.ecostep.app.ui.screens

import com.ecostep.app.ui.format.carbonKilogramsAsGramsText

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.ui.mock.HomeRouteOption
import com.ecostep.app.ui.viewmodels.HomeViewModel
import com.ecostep.app.ui.components.UpcomingMissionCard
import com.ecostep.app.ui.viewmodels.JourneyTrackingState
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.overlay.Polyline

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartJourney: (RouteInfo) -> Unit = {},
    onStartMission: (String) -> Unit = {},
    onViewMission: (String) -> Unit = {},
    /** Live device position; the map follows it when present. */
    liveLocation: GeoPoint? = null,
    /** Planned route for the active journey, drawn with start and destination markers. */
    plannedRoute: List<GeoPoint> = emptyList(),
    routeStart: GeoPoint? = null,
    routeDestination: GeoPoint? = null,
    /** Path actually travelled in the current recording. */
    recordedPath: List<GeoPoint> = emptyList(),
    /** Journey-tracking panel floating above the bottom of the map, when a journey is active. */
    trackingPanel: (@Composable () -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val selectedOption = uiState.selectedRouteOption

    fun searchDestination() {
        keyboardController?.hide()
        focusManager.clearFocus()
        viewModel.findRoutes()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        OsmMapView(
            currentLocation = liveLocation ?: uiState.currentLocation,
            followLocation = liveLocation != null,
            plannedRoute = plannedRoute,
            routeStart = routeStart,
            routeDestination = routeDestination,
            recordedPath = recordedPath,
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.destination,
                    onValueChange = viewModel::updateDestination,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text("Where are you going?")
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            searchDestination()
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor =
                            MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor =
                            MaterialTheme.colorScheme.surface,
                    ),
                )


            }


            @Composable
            fun RouteImpactChips(
                option: HomeRouteOption,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 10.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = if (option.estimatedEcoPoints > 0) {
                                    "Up to +${option.estimatedEcoPoints}"
                                } else {
                                    "+0"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Text(
                                text = ecoPointsLabel(
                                    points = option.estimatedEcoPoints,
                                    isMission = uiState.journeyTrackingState ==
                                        JourneyTrackingState.IN_PROGRESS,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 3.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                horizontal = 14.dp,
                                vertical = 10.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text =
                                    "Est. ${carbonKilogramsAsGramsText(option.estimatedCarbonSavedKg)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Text(
                                text = "CO₂ saved",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            WeatherChip(
                isLoading = uiState.isWeatherLoading,
                temperature = uiState.weather?.temperatureCelsius,
                conditions = uiState.weather?.conditions,
                errorMessage = uiState.weatherErrorMessage,
                onTryAgain = viewModel::loadWeather,
            )

            AnimatedVisibility(
                visible = uiState.isDirectionsConfirmed,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                selectedOption?.let { option ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RouteImpactChips(
                            option = option,
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shadowElevation = 2.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 14.dp,
                                    vertical = 10.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text =
                                        if (
                                            uiState.journeyTrackingState ==
                                            JourneyTrackingState.IN_PROGRESS
                                        ) {
                                            "Journey in progress"
                                        } else {
                                            "Ready to go"
                                        },
                                    style = MaterialTheme.typography.titleMedium,
                                    color =
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text =
                                        if (
                                            uiState.journeyTrackingState ==
                                            JourneyTrackingState.IN_PROGRESS
                                        ) {
                                            "${transportModeName(option.route.mode)} · " +
                                                    "Tracking automatically"
                                        } else {
                                            "Press Start below to record this trip."
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                        MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isRouteLoading) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 10.dp,
                        ),
                        horizontalArrangement =
                            Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )

                        Text(
                            text = "Finding route options...",
                            style =
                                MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            uiState.routeErrorMessage?.let { errorMessage ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color =
                        MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = errorMessage,
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 10.dp,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible =
                uiState.isRoutePlannerVisible &&
                        !uiState.isDirectionsConfirmed,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                initialOffsetY = { fullHeight ->
                    fullHeight
                },
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight ->
                    fullHeight
                },
            ) + fadeOut(),
        ) {
            RouteSelectionCard(
                startLocation = uiState.startLocation,
                destination = uiState.destination,
                onStartLocationChange = viewModel::updateStartLocation,
                onDestinationChange = viewModel::updateDestination,
                onFindRoutes = viewModel::findRoutes,

                routeOptions = uiState.routeOptions,
                selectedOption = selectedOption,
                publicTransportOptions = uiState.publicTransportOptions,
                publicTransportErrorMessage = uiState.publicTransportErrorMessage,
                isDirectionsConfirmed =
                    uiState.isDirectionsConfirmed,
                onRouteSelected = { option ->
                    viewModel.selectTransportMode(
                        option.route.mode,
                    )
                },
                onDirectionsClick =
                    viewModel::confirmDirections,
                onStartClick = {
                    selectedOption?.route?.let(
                        onStartJourney,
                    )
                },
            )
        }
        AnimatedVisibility(
            visible =
                uiState.shouldShowMissionCard &&
                        !uiState.isRoutePlannerVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
            ) + fadeOut(),
        ) {
            uiState.upcomingMission?.let { mission ->
                UpcomingMissionCard(
                    mission = mission,
                    currentTimeMillis = uiState.currentTimeMillis,
                    onStart = {
                        onStartMission(mission.missionId)
                    },
                    onViewMission = {
                        onViewMission(mission.missionId)
                    },
                    onDismiss = viewModel::dismissUpcomingMission,
                )
            }
        }

        if (trackingPanel != null && !uiState.isRoutePlannerVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
            ) {
                trackingPanel()
            }
        }
    }
}

@Composable
private fun OsmMapView(
    currentLocation: GeoPoint,
    followLocation: Boolean = false,
    plannedRoute: List<GeoPoint> = emptyList(),
    routeStart: GeoPoint? = null,
    routeDestination: GeoPoint? = null,
    recordedPath: List<GeoPoint> = emptyList(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastCentered = remember { LastCentered() }
    val lastFittedRoute = remember { LastFittedRoute() }
    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val density = context.resources.displayMetrics.density
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Scale tiles to the screen density; otherwise streets and labels look tiny on
            // high-resolution phones.
            isTilesScaledToDpi = true
            controller.setZoom(DEFAULT_MAP_ZOOM)
            controller.setCenter(currentLocation.toOsmGeoPoint())
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            view.overlays.clear()
            if (plannedRoute.size >= 2) {
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(plannedRoute.map { it.toOsmGeoPoint() })
                        outlinePaint.color = routeColor
                        outlinePaint.strokeWidth = 6f * density
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                        title = "Planned route"
                    },
                )
            }
            if (recordedPath.size >= 2) {
                view.overlays.add(
                    Polyline(view).apply {
                        setPoints(recordedPath.map { it.toOsmGeoPoint() })
                        outlinePaint.color = RECORDED_PATH_COLOR
                        outlinePaint.strokeWidth = 5f * density
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.isAntiAlias = true
                        title = "Your journey"
                    },
                )
            }
            routeStart?.let {
                view.overlays.add(routeMarker(view, it, "Start", START_MARKER_COLOR))
            }
            routeDestination?.let {
                view.overlays.add(routeMarker(view, it, "Destination", DESTINATION_MARKER_COLOR))
            }
            view.overlays.add(
                Marker(view).apply {
                    position = currentLocation.toOsmGeoPoint()
                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        Marker.ANCHOR_BOTTOM,
                    )
                    title = "You are here"
                },
            )

            if (plannedRoute.size >= 2 && lastFittedRoute.value !== plannedRoute) {
                // Show the whole route once when it arrives; leave room for the cards above
                // and the tracking panel below.
                lastFittedRoute.value = plannedRoute
                lastCentered.value = currentLocation
                val box = BoundingBox.fromGeoPoints(
                    (plannedRoute + currentLocation).map { it.toOsmGeoPoint() },
                )
                view.post {
                    view.zoomToBoundingBox(box, true, (ROUTE_FIT_PADDING_DP * density).toInt())
                }
            } else if (followLocation && lastCentered.value != currentLocation) {
                // Recentre only when the position changes, so the user can still pan the map.
                lastCentered.value = currentLocation
                view.controller.animateTo(currentLocation.toOsmGeoPoint())
            }
            view.invalidate()
        },
    )

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }
}

/** Non-observable holder: remembering the last centred point must not trigger recomposition. */
private class LastCentered {
    var value: GeoPoint? = null
}

/** Non-observable holder for the route the map last zoomed to. */
private class LastFittedRoute {
    var value: List<GeoPoint>? = null
}

private fun routeMarker(view: MapView, point: GeoPoint, label: String, color: Int): Marker =
    Marker(view).apply {
        position = point.toOsmGeoPoint()
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = label
        ContextCompat.getDrawable(view.context, org.osmdroid.library.R.drawable.marker_default)
            ?.mutate()
            ?.let { drawable ->
                DrawableCompat.setTint(drawable, color)
                icon = drawable
            }
    }

private const val DEFAULT_MAP_ZOOM = 17.0
private const val ROUTE_FIT_PADDING_DP = 140f
private const val START_MARKER_COLOR = 0xFF1E88E5.toInt()
private const val DESTINATION_MARKER_COLOR = 0xFFD32F2F.toInt()
private const val RECORDED_PATH_COLOR = 0xFF1565C0.toInt()

private fun GeoPoint.toOsmGeoPoint(): OsmGeoPoint {
    return OsmGeoPoint(latitude, longitude)
}

@Composable
private fun WeatherChip(
    isLoading: Boolean,
    temperature: Double?,
    conditions: String?,
    errorMessage: String?,
    onTryAgain: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable {
            if (errorMessage != null) {
                onTryAgain()
            }
        },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 9.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )

                    Text(
                        text = "Loading weather...",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                temperature != null && conditions != null -> {
                    Text(
                        text = "☁  $temperature°C · $conditions",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    Text(
                        text = errorMessage
                            ?: "Weather unavailable",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}


@Composable
private fun RouteSelectionCard(
    startLocation: String,
    destination: String,
    onStartLocationChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onFindRoutes: () -> Unit,
    routeOptions: List<HomeRouteOption>,
    selectedOption: HomeRouteOption?,
    publicTransportOptions: List<PublicTransportInfo>,
    publicTransportErrorMessage: String?,
    isDirectionsConfirmed: Boolean,
    onRouteSelected: (HomeRouteOption) -> Unit,
    onDirectionsClick: () -> Unit,
    onStartClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
        ),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Plan your route",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = startLocation,
                onValueChange = onStartLocationChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Starting point")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                ),
            )

            OutlinedTextField(
                value = destination,
                onValueChange = onDestinationChange,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Destination")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onFindRoutes()
                    },
                ),
            )

            Text(
                text = "Choose how to travel",
                style = MaterialTheme.typography.titleMedium,
            )

            val displayedModes =
                listOf(
                    TransportMode.WALKING,
                    TransportMode.CYCLING,
                    TransportMode.PUBLIC_TRANSPORT,
                    TransportMode.CAR,
                )

            val displayedRouteOptions =
                displayedModes.map { mode ->
                    mode to routeOptions.firstOrNull { option ->
                        option.route.mode == mode
                    }
                }

            displayedRouteOptions.chunked(2).forEach { modeRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    modeRow.forEach { (mode, option) ->
                        RouteModeButton(
                            option = option,
                            mode = mode,
                            isSelected =
                                option != null &&
                                        option.route.mode ==
                                        selectedOption?.route?.mode,
                            onClick = {
                                option?.let(onRouteSelected)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (modeRow.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (selectedOption?.route?.mode == TransportMode.PUBLIC_TRANSPORT) {
                PublicTransportTimetable(departures = publicTransportOptions)
            } else if (publicTransportErrorMessage != null && routeOptions.isNotEmpty()) {
                Text(
                    text = publicTransportErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            selectedOption?.let { option ->
                SelectedRouteImpact(
                    option = option,
                )
            }

            Button(
                onClick = if (isDirectionsConfirmed) {
                    onStartClick
                } else {
                    onDirectionsClick
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedOption != null,
            ) {
                Text(
                    text = "Show route"
                )
            }
        }
    }
}

@Composable
private fun RouteModeButton(
    modifier: Modifier = Modifier,
    option: HomeRouteOption?,
    mode: TransportMode =
        option?.route?.mode ?: TransportMode.UNKNOWN,
    isSelected: Boolean,
    onClick: () -> Unit,

) {
    val isAvailable = option != null

    val containerColor =
        when {
            !isAvailable ->
                MaterialTheme.colorScheme.surfaceVariant
            isSelected ->
                MaterialTheme.colorScheme.primaryContainer
            else ->
                MaterialTheme.colorScheme.surface
        }

    val borderColor =
        when {
            !isAvailable ->
                MaterialTheme.colorScheme.outlineVariant
            isSelected ->
                MaterialTheme.colorScheme.primary
            else ->
                MaterialTheme.colorScheme.outlineVariant
        }

    val contentColor =
        if (isAvailable) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier =
            modifier.clickable(
                enabled = isAvailable,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = transportModeName(mode),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
            )

            Text(
                text =
                    option?.let {
                        durationText(it.route.durationSeconds)
                    } ?: "No route available",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun SelectedRouteImpact(
    option: HomeRouteOption,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color =
            MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Text(
                    text = transportModeName(
                        option.route.mode,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text =
                        "${durationText(option.route.durationSeconds)} · " +
                                distanceText(
                                    option.route.distanceMeters,
                                ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text =
                            carbonKilogramsAsGramsText(option.estimatedCarbonSavedKg),
                        style =
                            MaterialTheme.typography.titleMedium,
                        color =
                            MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = "Estimated CO₂ saved",
                        style =
                            MaterialTheme.typography.bodyMedium,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text =
                            "+${option.estimatedEcoPoints}",
                        style =
                            MaterialTheme.typography.titleMedium,
                        color =
                            MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = ecoPointsLabel(option.estimatedEcoPoints),
                        style =
                            MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun transportModeName(
    mode: TransportMode,
): String {
    return when (mode) {
        TransportMode.WALKING -> "Walk"
        TransportMode.CYCLING -> "Cycle"
        TransportMode.PUBLIC_TRANSPORT ->
            "Public Transport"

        TransportMode.CAR -> "Car"
        TransportMode.UNKNOWN -> "Unknown"
    }
}

private fun durationText(
    durationSeconds: Long,
): String {
    return "${durationSeconds / 60} min"
}

private fun distanceText(
    distanceMeters: Double,
): String {
    return String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0)
}

/** EcoPoints are only awarded for completed missions; free routes say so instead of "+0". */
private fun ecoPointsLabel(points: Int, isMission: Boolean = false): String =
    if (points > 0 || isMission) "Estimated EcoPoints" else "EcoPoints on missions only"

@Composable
private fun PublicTransportTimetable(
    departures: List<PublicTransportInfo>,
) {
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Next departures",
            style = MaterialTheme.typography.labelLarge,
        )

        if (departures.isEmpty()) {
            Text(
                text = "No upcoming departures found.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        departures.forEach { departure ->
            Text(
                text = "${departure.line} · departs " +
                    "${timeFormat.format(Date(departure.departureTimeMillis))} · " +
                    durationText(departure.estimatedDurationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}