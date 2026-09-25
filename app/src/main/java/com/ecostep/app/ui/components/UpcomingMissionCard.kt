package com.ecostep.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecostep.app.ui.viewmodels.UpcomingMissionUi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment

@Composable
fun UpcomingMissionCard(
    mission: UpcomingMissionUi,
    currentTimeMillis: Long,
    onStart: () -> Unit,
    onViewMission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val millisUntilStart =
        mission.startTimeMillis - currentTimeMillis

    val timeLabel =
        if (millisUntilStart > 0) {
            val minutesUntilStart =
                ((millisUntilStart + 59_999L) / 60_000L)
                    .coerceAtLeast(1L)

            "Starts in $minutesUntilStart min"
        } else {
            "Ready to start"
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Upcoming EcoMission",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = mission.routeTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = mission.transportLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "◷ $timeLabel",
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = 9.dp,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 10.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "+${mission.estimatedEcoPoints} EcoPoints",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Potential reward",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 10.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text =
                                "${mission.estimatedCarbonSavedKg} kg CO₂ saved",
                            style = MaterialTheme.typography.titleMedium,
                            color =
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = "Estimated saving",
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onViewMission,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("View Mission")
                }

                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Start")
                }
            }
        }
    }
}