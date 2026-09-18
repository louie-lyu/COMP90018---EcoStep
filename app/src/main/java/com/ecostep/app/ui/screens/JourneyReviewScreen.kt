package com.ecostep.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.ui.viewmodels.JourneyReviewUiState
import com.ecostep.app.ui.viewmodels.JourneyReviewViewModel
import java.util.Locale
import androidx.compose.ui.text.style.TextAlign

@Composable
fun JourneyReviewScreen(
    viewModel: JourneyReviewViewModel,
    onBackToMap: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> {
            JourneyLoadingContent()
        }

        uiState.errorMessage != null -> {
            JourneyErrorContent(
                message = uiState.errorMessage
                    ?: "Unable to load journey.",
            )
        }

        uiState.journey == null -> {
            JourneyErrorContent(
                message = "Journey is unavailable.",
            )
        }

        else -> {
            JourneyReviewLoadedContent(
                uiState = uiState,
                onModeSelected = viewModel::selectTransportMode,
                onConfirmJourney = viewModel::saveJourney,
            )

            if (uiState.isSaved) {
                JourneyConfirmedDialog(
                    ecoPoints = uiState.ecoPoints,
                    carbonSavedKg = uiState.carbonSavedKg,
                    onDismiss = {
                        viewModel.dismissConfirmation()
                        onBackToMap()
                    },
                )
            }
        }
    }
}

@Composable
private fun JourneyLoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Loading journey...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun JourneyErrorContent(
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background,
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun JourneyReviewLoadedContent(
    uiState: JourneyReviewUiState,
    onModeSelected: (TransportMode) -> Unit,
    onConfirmJourney: () -> Unit,
) {
    val journey = uiState.journey ?: return

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                JourneyMapPreview(
                    startLocation = journey.startLocation,
                    endLocation = journey.endLocation,
                )
            }

            item {
                JourneyReviewContent(
                    uiState = uiState,
                    onModeSelected = onModeSelected,
                )
            }

            item {
                ConfirmJourneyButton(
                    ecoPoints = uiState.ecoPoints,
                    carbonSavedKg = uiState.carbonSavedKg,
                    isSaving = uiState.isSaving,
                    onClick = onConfirmJourney,
                )
            }
        }
    }
}

@Composable
private fun ConfirmJourneyButton(
    ecoPoints: Int,
    carbonSavedKg: Double,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = !isSaving,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                bottom = 20.dp,
            )
            .height(64.dp),
        shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor =
                MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.55f,
                ),
            disabledContentColor =
                MaterialTheme.colorScheme.onPrimary.copy(
                    alpha = 0.75f,
                ),
        ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isSaving) {
                    "Saving Journey..."
                } else {
                    "Confirm Journey"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = String.format(
                    Locale.US,
                    "Save %.2f kg CO₂ · Earn %d EcoPoints",
                    carbonSavedKg,
                    ecoPoints,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(
                    alpha = 0.85f,
                ),
            )
        }
    }
}

@Composable
private fun JourneyMapPreview(
    startLocation: GeoPoint,
    endLocation: GeoPoint,
) {
    val mapColour = MaterialTheme.colorScheme.surfaceVariant
    val routeColour = MaterialTheme.colorScheme.primary
    val destinationColour =
        MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(mapColour),
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val roadColour = Color.White.copy(alpha = 0.9f)

            drawRect(
                color = roadColour,
                topLeft = Offset(
                    x = size.width * 0.22f,
                    y = 0f,
                ),
                size = Size(
                    width = size.width * 0.035f,
                    height = size.height,
                ),
            )

            drawRect(
                color = roadColour,
                topLeft = Offset(
                    x = size.width * 0.68f,
                    y = 0f,
                ),
                size = Size(
                    width = size.width * 0.035f,
                    height = size.height,
                ),
            )

            drawRect(
                color = roadColour,
                topLeft = Offset(
                    x = 0f,
                    y = size.height * 0.36f,
                ),
                size = Size(
                    width = size.width,
                    height = size.height * 0.035f,
                ),
            )

            drawRect(
                color = roadColour,
                topLeft = Offset(
                    x = 0f,
                    y = size.height * 0.77f,
                ),
                size = Size(
                    width = size.width,
                    height = size.height * 0.035f,
                ),
            )

            /*
             * Temporary visual projection for the mock map.
             * Replace this Canvas with the real map/route API later.
             */
            fun GeoPoint.toMapOffset(): Offset {
                val minimumLongitude = 144.85
                val maximumLongitude = 145.15
                val minimumLatitude = -37.95
                val maximumLatitude = -37.70

                val horizontalPosition = (
                        (longitude - minimumLongitude) /
                                (maximumLongitude - minimumLongitude)
                        ).coerceIn(0.08, 0.92)

                val verticalPosition = (
                        1.0 -
                                (latitude - minimumLatitude) /
                                (maximumLatitude - minimumLatitude)
                        ).coerceIn(0.10, 0.90)

                return Offset(
                    x = size.width * horizontalPosition.toFloat(),
                    y = size.height * verticalPosition.toFloat(),
                )
            }

            val start = startLocation.toMapOffset()
            val end = endLocation.toMapOffset()

            drawLine(
                color = routeColour,
                start = start,
                end = end,
                strokeWidth = 7f,
                cap = StrokeCap.Round,
            )

            drawCircle(
                color = routeColour,
                radius = 10f,
                center = start,
            )

            drawCircle(
                color = Color.White,
                radius = 13f,
                center = end,
            )

            drawCircle(
                color = destinationColour,
                radius = 13f,
                center = end,
                style = Stroke(width = 7f),
            )
        }

        Surface(
            modifier = Modifier.padding(
                start = 18.dp,
                top = 18.dp,
            ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
        ) {
            Text(
                text = "Journey completed",
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 10.dp,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun JourneyReviewContent(
    uiState: JourneyReviewUiState,
    onModeSelected: (TransportMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                ),
            )
            .padding(
                horizontal = 20.dp,
                vertical = 18.dp,
            ),
    ) {
        Text(
            text = "Journey complete",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Review your journey before saving it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(18.dp))

        JourneySummaryCard(
            startLocationText = uiState.startLocationText,
            endLocationText = uiState.endLocationText,
            durationText = uiState.durationText,
            distanceText = uiState.distanceText,
            dateText = uiState.dateText,
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "How did you travel?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        val transportModeMessage =
            if (uiState.selectedMode == uiState.detectedMode) {
                "We detected ${
                    transportModeName(uiState.detectedMode)
                }."
            } else {
                "Changed from ${
                    transportModeName(uiState.detectedMode)
                } to ${
                    transportModeName(uiState.selectedMode)
                }."
            }

        Text(
            text = transportModeMessage,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(14.dp))

        TransportModeSelector(
            selectedMode = uiState.selectedMode,
            onModeSelected = onModeSelected,
        )

        Spacer(modifier = Modifier.height(18.dp))

        EnvironmentalImpactCard(
            ecoPoints = uiState.ecoPoints,
            carbonSavedKg = uiState.carbonSavedKg,
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
private fun JourneySummaryCard(
    startLocationText: String,
    endLocationText: String,
    durationText: String,
    distanceText: String,
    dateText: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = startLocationText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Starting point",
                style = MaterialTheme.typography.labelMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "↓",
                modifier = Modifier.padding(vertical = 7.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
            )

            Text(
                text = endLocationText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Destination",
                style = MaterialTheme.typography.labelMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                JourneyDetail(
                    value = durationText,
                    label = "Duration",
                )

                JourneyDetail(
                    value = distanceText,
                    label = "Distance",
                )

                JourneyDetail(
                    value = dateText,
                    label = "Date",
                )
            }
        }
    }
}

@Composable
private fun JourneyDetail(
    value: String,
    label: String,
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransportModeSelector(
    selectedMode: TransportMode,
    onModeSelected: (TransportMode) -> Unit,
) {
    val modes = listOf(
        TransportMode.WALKING,
        TransportMode.CYCLING,
        TransportMode.PUBLIC_TRANSPORT,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransportModeButton(
            mode = modes[0],
            selected = selectedMode == modes[0],
            onClick = {
                onModeSelected(modes[0])
            },
            modifier = Modifier.weight(1f),
        )

        TransportModeButton(
            mode = modes[1],
            selected = selectedMode == modes[1],
            onClick = {
                onModeSelected(modes[1])
            },
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    TransportModeButton(
        mode = modes[2],
        selected = selectedMode == modes[2],
        onClick = {
            onModeSelected(modes[2])
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TransportModeButton(
    mode: TransportMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColour = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }

    val borderColour = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    val textColour = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .background(
                color = backgroundColour,
                shape = RoundedCornerShape(15.dp),
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = borderColour,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = transportModeName(mode),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
            ),
            color = textColour,
        )
    }
}

@Composable
private fun EnvironmentalImpactCard(
    ecoPoints: Int,
    carbonSavedKg: Double,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 18.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "+$ecoPoints",
                    style =
                        MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )

                Text(
                    text = "EcoPoints",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box(
                modifier = Modifier
                    .size(
                        width = 1.dp,
                        height = 48.dp,
                    )
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                    ),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 18.dp),
            ) {
                Text(
                    text = String.format(
                        Locale.US,
                        "%.2f kg",
                        carbonSavedKg,
                    ),
                    style =
                        MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text = "CO₂ saved",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun JourneyConfirmedDialog(
    ecoPoints: Int,
    carbonSavedKg: Double,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Journey saved!",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            color = MaterialTheme
                                .colorScheme
                                .primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "You earned",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme
                        .colorScheme
                        .onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "+$ecoPoints EcoPoints",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = String.format(
                        Locale.US,
                        "You saved %.2f kg of CO₂",
                        carbonSavedKg,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(
                    text = "Back to Map",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
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