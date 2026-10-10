package com.ecostep.app.ui.screens

import com.ecostep.app.ui.format.carbonGramsText

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
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecostep.app.algorithm.WeeklyCoachReport
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.ui.viewmodels.WeeklyInsightViewModel
import java.util.Locale
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun WeeklyInsightScreen(
    viewModel: WeeklyInsightViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val report = uiState.report

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(56.dp),
                ) {
                    Text(
                        text = "←",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics {
                            contentDescription = "Back to Missions"
                        },
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
                ) {
                    Text(
                        text = "Weekly Insight",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        text = "Your low-carbon missions this week",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = 20.dp,
                    vertical = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uiState.isLoading) {
                    item { Text("Loading weekly insight…") }
                }
                uiState.errorMessage?.let { message ->
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = viewModel::retry, enabled = !uiState.isLoading) {
                                Text("Retry")
                            }
                        }
                    }
                }
                if (report == null) {
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
                value = carbonGramsText(report.totalCarbonSavingGrams),
                label = "Completed mission CO₂",
            )

            StatCard(
                modifier = Modifier.weight(1f),
                value = report.mostUsedCompletedMode
                    ?.let { transportModeName(it) }
                    ?: "—",
                label = "Most used mode",
            )
        }
        Text(
            text = "This week's completed mission occurrences only; ordinary journeys and route estimates are excluded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
