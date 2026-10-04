package com.ecostep.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.ui.viewmodels.JourneyHistoryViewModel
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun JourneyHistoryScreen(
    viewModel: JourneyHistoryViewModel,
    onBack: () -> Unit,
    onJourneySelected: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            JourneyHistoryTopBar(
                onBack = onBack,
            )

            HorizontalDivider()

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.journeys.isEmpty() &&
                        uiState.errorMessage == null -> {
                    JourneyHistoryEmptyState()
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = 18.dp,
                            vertical = 20.dp,
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                text =
                                    "${uiState.journeys.size} recorded journeys",
                                style =
                                    MaterialTheme.typography.bodyMedium,
                                color =
                                    MaterialTheme.colorScheme
                                        .onSurfaceVariant,
                            )
                        }

                        items(
                            items = uiState.journeys,
                            key = { journey ->
                                journey.journeyId
                            },
                        ) { journey ->
                            JourneyHistoryCard(
                                journey = journey,
                                isPendingSync =
                                    journey.journeyId in
                                        uiState.pendingSyncJourneyIds,
                                onClick = {
                                    onJourneySelected(
                                        journey.journeyId,
                                    )
                                },
                            )
                        }

                        item {
                            Spacer(
                                modifier = Modifier.height(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("Unable to load journeys")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = onBack,
                ) {
                    Text("Back")
                }
            },
        )
    }
}

@Composable
private fun JourneyHistoryTopBar(
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "←",
                style =
                    MaterialTheme.typography.headlineLarge,
                color =
                    MaterialTheme.colorScheme.onBackground,
            )
        }

        Column {
            Text(
                text = "Journey History",
                style =
                    MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "Your completed journeys",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JourneyHistoryCard(
    journey: JourneySummary,
    isPendingSync: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
        ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color =
                            MaterialTheme.colorScheme
                                .primaryContainer,
                    ) {
                        Box(
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            Text(
                                text =
                                    journey.transportMode
                                        .shortLabel(),
                                style =
                                    MaterialTheme.typography
                                        .labelMedium,
                                color =
                                    MaterialTheme.colorScheme
                                        .onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Column {
                        Text(
                            text =
                                journey.transportMode
                                    .displayName(),
                            style =
                                MaterialTheme.typography
                                    .titleMedium,
                        )

                        Text(
                            text =
                                formatJourneyDate(
                                    journey.endTimeMillis,
                                ),
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "View",
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                JourneyMetric(
                    label = "Distance",
                    value =
                        formatJourneyDistance(
                            journey.distanceMeters,
                        ),
                    modifier = Modifier.weight(1f),
                )

                JourneyMetric(
                    label = "Duration",
                    value =
                        formatJourneyDuration(
                            startTimeMillis =
                                journey.startTimeMillis,
                            endTimeMillis =
                                journey.endTimeMillis,
                        ),
                    modifier = Modifier.weight(1f),
                )
            }

            if (isPendingSync) {
                Text(
                    text = "Saved on this device · waiting to sync",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun JourneyMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color =
            MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier =
                Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 10.dp,
                ),
        ) {
            Text(
                text = value,
                style =
                    MaterialTheme.typography.titleMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = label,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun JourneyHistoryEmptyState() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No journeys yet",
                style =
                    MaterialTheme.typography.headlineMedium,
            )

            Text(
                text =
                    "Completed journeys will appear here after they have been reviewed and saved.",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun TransportMode.displayName(): String {
    return when (this) {
        TransportMode.WALKING -> "Walking"
        TransportMode.CYCLING -> "Cycling"
        TransportMode.PUBLIC_TRANSPORT ->
            "Public Transport"

        TransportMode.CAR -> "Car"
        TransportMode.UNKNOWN -> "Unknown"
    }
}

private fun TransportMode.shortLabel(): String {
    return when (this) {
        TransportMode.WALKING -> "W"
        TransportMode.CYCLING -> "B"
        TransportMode.PUBLIC_TRANSPORT -> "PT"
        TransportMode.CAR -> "C"
        TransportMode.UNKNOWN -> "?"
    }
}

private fun formatJourneyDate(
    timeMillis: Long,
): String {
    return DateFormat
        .getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
        )
        .format(Date(timeMillis))
}

private fun formatJourneyDistance(
    distanceMeters: Double,
): String {
    return if (distanceMeters >= 1000.0) {
        val roundedTenths =
            kotlin.math
                .round(distanceMeters / 100.0)
                .toInt()

        val wholePart =
            roundedTenths / 10

        val decimalPart =
            kotlin.math.abs(roundedTenths % 10)

        "$wholePart.$decimalPart km"
    } else {
        "${distanceMeters.roundToInt()} m"
    }
}

private fun formatJourneyDuration(
    startTimeMillis: Long,
    endTimeMillis: Long,
): String {
    val totalMinutes =
        ((endTimeMillis - startTimeMillis) / 60_000L)
            .coerceAtLeast(0L)

    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return when {
        hours > 0 && minutes > 0 ->
            "${hours}h ${minutes}m"

        hours > 0 ->
            "${hours}h"

        else ->
            "${minutes} min"
    }
}