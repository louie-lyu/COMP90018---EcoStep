package com.ecostep.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.ui.mock.HomeRouteOption
import com.ecostep.app.ui.viewmodels.HomeViewModel
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import com.ecostep.app.ui.components.UpcomingMissionCard
import com.ecostep.app.ui.viewmodels.JourneyTrackingState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartJourney: (RouteInfo) -> Unit = {},
    onStartMission: (String) -> Unit = {},
    onViewMission: (String) -> Unit = {},
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
        MapBackground(
            selectedMode = selectedOption?.route?.mode,
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
                                text =
                                    "Up to +${option.estimatedEcoPoints}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Text(
                                text = "EcoPoints",
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
                                    "Est. ${option.estimatedCarbonSavedKg} kg",
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
                                            "Your journey is being tracked automatically."
                                        } else {
                                            "Trip tracking will start automatically " +
                                                    "when you begin moving."
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
    }
}

@Composable
private fun MapBackground(
    selectedMode: TransportMode?,
    // TODO(Sensors): Replace this mock bearing with the user's
    // real device heading when sensor integration is available.
    userBearing: Float = 35f,
) {
    val backgroundColor =
        MaterialTheme.colorScheme.primaryContainer.copy(
            alpha = 0.55f,
        )

    val roadColor =
        MaterialTheme.colorScheme.surface.copy(
            alpha = 0.95f,
        )

    val routeColor =
        MaterialTheme.colorScheme.primary

    val surfaceColor =
        MaterialTheme.colorScheme.surface

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val roadWidth = 14.dp.toPx()

        drawLine(
            color = roadColor,
            start = Offset(size.width * 0.18f, 0f),
            end = Offset(
                size.width * 0.18f,
                size.height,
            ),
            strokeWidth = roadWidth,
        )

        drawLine(
            color = roadColor,
            start = Offset(size.width * 0.52f, 0f),
            end = Offset(
                size.width * 0.52f,
                size.height,
            ),
            strokeWidth = roadWidth,
        )

        drawLine(
            color = roadColor,
            start = Offset(size.width * 0.84f, 0f),
            end = Offset(
                size.width * 0.84f,
                size.height,
            ),
            strokeWidth = roadWidth,
        )

        drawLine(
            color = roadColor,
            start = Offset(0f, size.height * 0.26f),
            end = Offset(
                size.width,
                size.height * 0.26f,
            ),
            strokeWidth = roadWidth,
        )

        drawLine(
            color = roadColor,
            start = Offset(0f, size.height * 0.52f),
            end = Offset(
                size.width,
                size.height * 0.52f,
            ),
            strokeWidth = roadWidth,
        )

        drawLine(
            color = roadColor,
            start = Offset(0f, size.height * 0.76f),
            end = Offset(
                size.width,
                size.height * 0.76f,
            ),
            strokeWidth = roadWidth,
        )

        if (selectedMode != null) {
            val routeVariation = when (selectedMode) {
                TransportMode.WALKING -> 0.42f
                TransportMode.CYCLING -> 0.50f
                TransportMode.PUBLIC_TRANSPORT -> 0.58f
                TransportMode.CAR -> 0.66f
                TransportMode.UNKNOWN -> 0.54f
            }

            val startPoint = Offset(
                x = size.width * 0.28f,
                y = size.height * 0.67f,
            )

            val middlePoint = Offset(
                x = size.width * routeVariation,
                y = size.height * 0.47f,
            )

            val endPoint = Offset(
                x = size.width * 0.74f,
                y = size.height * 0.29f,
            )

            val dashedEffect =
                PathEffect.dashPathEffect(
                    intervals = floatArrayOf(14f, 10f),
                )

            drawLine(
                color = routeColor,
                start = startPoint,
                end = middlePoint,
                strokeWidth = 7.dp.toPx(),
                pathEffect = dashedEffect,
            )

            drawLine(
                color = routeColor,
                start = middlePoint,
                end = endPoint,
                strokeWidth = 7.dp.toPx(),
                pathEffect = dashedEffect,
            )

            drawCircle(
                color = routeColor,
                radius = 11.dp.toPx(),
                center = startPoint,
            )

            drawCircle(
                color = surfaceColor,
                radius = 11.dp.toPx(),
                center = endPoint,
            )

            drawCircle(
                color = routeColor,
                radius = 11.dp.toPx(),
                center = endPoint,
                style = Stroke(
                    width = 4.dp.toPx(),
                ),
            )
        }

        // TODO(Location): Replace this fixed canvas position with the user's
        // live map position when location tracking is available.
        val userLocation = Offset(
            x = size.width * 0.28f,
            y = size.height * 0.67f,
        )

        val cursorPath = Path().apply {
            moveTo(
                userLocation.x,
                userLocation.y - 18.dp.toPx(),
            )
            lineTo(
                userLocation.x - 11.dp.toPx(),
                userLocation.y + 14.dp.toPx(),
            )
            lineTo(
                userLocation.x,
                userLocation.y + 9.dp.toPx(),
            )
            lineTo(
                userLocation.x + 11.dp.toPx(),
                userLocation.y + 14.dp.toPx(),
            )
            close()
        }

        rotate(
            degrees = userBearing,
            pivot = userLocation,
        ) {
            drawPath(
                path = cursorPath,
                color = Color.White,
                style = Stroke(width = 6.dp.toPx()),
            )

            drawPath(
                path = cursorPath,
                color = routeColor,
            )
        }
    }
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
    option: HomeRouteOption?,
    mode: TransportMode =
        option?.route?.mode ?: TransportMode.UNKNOWN,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                            "${option.estimatedCarbonSavedKg} kg",
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
                        text = "Estimated EcoPoints",
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
    return "${distanceMeters / 1000.0} km"
}