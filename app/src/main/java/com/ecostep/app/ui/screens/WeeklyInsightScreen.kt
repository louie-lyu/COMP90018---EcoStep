package com.ecostep.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecostep.app.algorithm.WeeklyCoachReport
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.ui.viewmodels.WeeklyInsightViewModel
import java.util.Locale

@Composable
fun WeeklyInsightScreen(
    viewModel: WeeklyInsightViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val report = uiState.report

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 24.dp,
            end = 20.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Weekly Insight",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text =
                        "How your low-carbon missions went this week.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (report == null) {
            item {
                Text(
                    text = if (uiState.isLoading) {
                        "Loading this week's insight..."
                    } else {
                        "Could not load this week's data. Check your connection and reopen this page."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@LazyColumn
        }

        item {
            StatsRow(report = report)
        }

        item {
            InsightCard(
                title = "This week",
                body = uiState.insight ?: report.summary,
                badge = if (uiState.usedAi) "AI personalised" else null,
            )
        }

        item {
            InsightCard(
                title = "Coaching tip",
                body = uiState.action ?: report.fallbackMessage,
            )
        }
    }
}

@Composable
private fun StatsRow(
    report: WeeklyCoachReport,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = "${report.completedMissions}/${report.acceptedMissions}",
                label = "Missions completed",
            )

            StatCard(
                modifier = Modifier.weight(1f),
                value = "${formatNumber(report.completionRate)}%",
                label = "Completion rate",
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                value = "${formatNumber(report.totalCarbonSavingGrams)} g",
                label = "CO₂ saved",
            )

            StatCard(
                modifier = Modifier.weight(1f),
                value = report.mostUsedCompletedMode
                    ?.let { transportModeName(it) }
                    ?: "—",
                label = "Most used mode",
            )
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightCard(
    title: String,
    body: String,
    badge: String? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )

                badge?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun transportModeName(mode: TransportMode): String {
    return when (mode) {
        TransportMode.WALKING -> "Walking"
        TransportMode.CYCLING -> "Cycling"
        TransportMode.PUBLIC_TRANSPORT -> "Public Transport"
        TransportMode.CAR -> "Car"
        TransportMode.UNKNOWN -> "Unknown"
    }
}

private fun formatNumber(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}
