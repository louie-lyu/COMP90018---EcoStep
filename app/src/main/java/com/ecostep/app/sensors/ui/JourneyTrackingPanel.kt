package com.ecostep.app.sensors.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Floating journey-tracking panel shown at the bottom of the map: mission name, live distance
 * and duration, and Start / End (save and review) / Abort (discard) controls.
 */
@Composable
fun JourneyTrackingPanel(
    missionTitle: String?,
    routeSummary: String?,
    isRecording: Boolean,
    isSaving: Boolean,
    distanceMeters: Double,
    elapsedSeconds: Long,
    message: String?,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmAbort by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRecording) "Recording journey" else "Journey not started",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isRecording) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        text = missionTitle ?: "Free journey",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    routeSummary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                TrackingMetric(label = "Distance", value = formatTrackingDistance(distanceMeters))
                TrackingMetric(label = "Duration", value = formatTrackingDuration(elapsedSeconds))
            }

            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    enabled = !isRecording && !isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("Start") }
                Button(
                    onClick = onEnd,
                    enabled = isRecording && !isSaving,
                    modifier = Modifier.weight(1f),
                ) { Text("End") }
                OutlinedButton(
                    onClick = { confirmAbort = true },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Abort") }
            }
        }
    }

    if (confirmAbort) {
        AlertDialog(
            onDismissRequest = { confirmAbort = false },
            title = { Text("Abort this journey?") },
            text = {
                Text(
                    if (missionTitle != null) {
                        "The recording is discarded and the mission ends without a reward."
                    } else {
                        "The recording is discarded."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAbort = false
                    onAbort()
                }) { Text("Abort") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAbort = false }) { Text("Keep going") }
            },
        )
    }
}

@Composable
private fun TrackingMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun formatTrackingDistance(distanceMeters: Double): String =
    if (distanceMeters >= 1000.0) {
        String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0)
    } else {
        "${distanceMeters.toInt()} m"
    }

internal fun formatTrackingDuration(elapsedSeconds: Long): String {
    val seconds = elapsedSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
    }
}
