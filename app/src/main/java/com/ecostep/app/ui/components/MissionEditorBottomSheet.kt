package com.ecostep.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecostep.app.ui.mock.MissionDay
import com.ecostep.app.ui.mock.MissionPageItem
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import com.ecostep.app.ui.adapters.withRouteEstimate
import com.ecostep.app.ui.viewmodels.RouteEstimateState
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionEditorBottomSheet(
    item: MissionPageItem,
    onDismiss: () -> Unit,
    onSave: (MissionPageItem) -> Unit,
    /** Recalculated impact for the start and destination being edited. */
    routeEstimate: RouteEstimateState = RouteEstimateState.Idle,
    /** Called once typing pauses on a changed start or destination. */
    onRouteChanged: (start: String, destination: String) -> Unit = { _, _ -> },
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    var startLocation by remember(item.mission.missionId) {
        mutableStateOf(item.startLocation)
    }

    var destination by remember(item.mission.missionId) {
        mutableStateOf(item.destination)
    }

    var selectedTransportMode by remember(item.mission.missionId) {
        mutableStateOf(item.mission.transportLabel)
    }

    var selectedHour by remember(item.mission.missionId) {
        mutableIntStateOf(item.scheduledHour)
    }

    var selectedMinute by remember(item.mission.missionId) {
        mutableIntStateOf(item.scheduledMinute)
    }

    var selectedRepeatDays by remember(item.mission.missionId) {
        mutableStateOf(item.repeatDays)
    }

    var showTimePicker by remember {
        mutableStateOf(false)
    }

    var showRepeatDialog by remember {
        mutableStateOf(false)
    }

    // Recalculate once the user has finished editing the route, not on every keystroke.
    LaunchedEffect(startLocation, destination) {
        val routeChanged = startLocation.trim() != item.startLocation.trim() ||
            destination.trim() != item.destination.trim()
        if (routeChanged || item.transportOptions.isEmpty()) {
            delay(ROUTE_ESTIMATE_DEBOUNCE_MILLIS)
            onRouteChanged(startLocation, destination)
        }
    }

    val estimate = (routeEstimate as? RouteEstimateState.Ready)?.estimate
        ?.takeIf { it.matches(startLocation, destination) }

    val transportModes = estimate
        ?.let { item.withRouteEstimate(it).transportOptions }
        ?: item.transportOptions

    val selectedTransportOption =
        transportModes.firstOrNull {
            it.label == selectedTransportMode
        }

    val canSave =
        startLocation.isNotBlank() &&
                destination.isNotBlank() &&
                selectedRepeatDays.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 28.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Edit EcoMission",
                style = MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = "Update the route, transport mode and schedule.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Route",
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = startLocation,
                onValueChange = {
                    startLocation = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Starting point")
                },
                singleLine = true,
            )

            OutlinedTextField(
                value = destination,
                onValueChange = {
                    destination = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Destination")
                },
                singleLine = true,
            )

            RouteEstimateStatus(
                routeEstimate = routeEstimate,
                startLocation = startLocation,
                destination = destination,
            )

            Text(
                text = "Transport mode",
                style = MaterialTheme.typography.titleMedium,
            )

            transportModes
                .chunked(2)
                .forEach { rowModes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowModes.forEach { mode ->
                            val isSelected =
                                mode.label == selectedTransportMode

                            if (isSelected) {
                                Button(
                                    onClick = {
                                        selectedTransportMode = mode.label
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(mode.label)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedTransportMode = mode.label
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(mode.label)
                                }
                            }
                        }

                        if (rowModes.size == 1) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.surface,
                            ) {}
                        }
                    }
                }

            selectedTransportOption?.let { option ->
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
                                text = "+${option.estimatedEcoPoints} EcoPoints",
                                style = MaterialTheme.typography.titleMedium,
                                color =
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                            )

                            Text(
                                text = "Potential reward",
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    MaterialTheme.colorScheme.onPrimaryContainer,
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
                                text = String.format(
                                    Locale.getDefault(),
                                    "%.2f kg CO₂ saved",
                                    option.estimatedCarbonSavedKg,
                                ),
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
            }

            Text(
                text = "Schedule",
                style = MaterialTheme.typography.titleMedium,
            )

            ScheduleSettingRow(
                label = "Time",
                value = formatMissionTime(
                    hour = selectedHour,
                    minute = selectedMinute,
                ),
                onClick = {
                    showTimePicker = true
                },
            )

            ScheduleSettingRow(
                label = "Repeat",
                value = formatRepeatDays(selectedRepeatDays),
                onClick = {
                    showRepeatDialog = true
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onSave(
                            item.copy(
                                mission = item.mission.copy(
                                    routeTitle =
                                        "${startLocation.trim()} → " +
                                                destination.trim(),
                                    transportLabel = selectedTransportMode,
                                    estimatedEcoPoints =
                                        selectedTransportOption?.estimatedEcoPoints
                                            ?: item.mission.estimatedEcoPoints,
                                    estimatedCarbonSavedKg =
                                        selectedTransportOption?.estimatedCarbonSavedKg
                                            ?: item.mission.estimatedCarbonSavedKg,
                                ),
                                startLocation = startLocation.trim(),
                                destination = destination.trim(),
                                scheduledHour = selectedHour,
                                scheduledMinute = selectedMinute,
                                repeatDays = selectedRepeatDays,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = canSave,
                ) {
                    Text("Save")
                }
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = false,
        )

        AlertDialog(
            onDismissRequest = {
                showTimePicker = false
            },
            title = {
                Text("Select time")
            },
            text = {
                TimePicker(
                    state = timePickerState,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedHour = timePickerState.hour
                        selectedMinute = timePickerState.minute
                        showTimePicker = false
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showRepeatDialog) {
        AlertDialog(
            onDismissRequest = {
                showRepeatDialog = false
            },
            title = {
                Text("Repeat")
            },
            text = {
                Column {
                    MissionDay.entries.forEach { day ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedRepeatDays =
                                        toggleMissionDay(
                                            currentDays =
                                                selectedRepeatDays,
                                            day = day,
                                        )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = day.fullLabel,
                                modifier = Modifier.weight(1f),
                                style =
                                    MaterialTheme.typography.bodyLarge,
                            )

                            Checkbox(
                                checked =
                                    day in selectedRepeatDays,
                                onCheckedChange = {
                                    selectedRepeatDays =
                                        toggleMissionDay(
                                            currentDays =
                                                selectedRepeatDays,
                                            day = day,
                                        )
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRepeatDialog = false
                    },
                    enabled = selectedRepeatDays.isNotEmpty(),
                ) {
                    Text("Done")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRepeatDialog = false
                    },
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

private const val ROUTE_ESTIMATE_DEBOUNCE_MILLIS = 800L

@Composable
private fun RouteEstimateStatus(
    routeEstimate: RouteEstimateState,
    startLocation: String,
    destination: String,
) {
    fun isCurrent(start: String, end: String) =
        start == startLocation.trim() && end == destination.trim()

    val (text, isError) = when (routeEstimate) {
        is RouteEstimateState.Loading ->
            if (isCurrent(routeEstimate.startText, routeEstimate.destinationText)) {
                "Calculating CO₂ and EcoPoints for this route…" to false
            } else {
                return
            }

        is RouteEstimateState.Ready -> {
            val estimate = routeEstimate.estimate
            if (!estimate.matches(startLocation, destination)) return
            val km = estimate.distanceByMode.values.minOrNull()?.div(1000.0) ?: return
            String.format(Locale.getDefault(), "Estimates updated for this route (about %.1f km).", km) to false
        }

        is RouteEstimateState.Failed ->
            if (isCurrent(routeEstimate.startText, routeEstimate.destinationText)) {
                routeEstimate.message to true
            } else {
                return
            }

        RouteEstimateState.Idle -> return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun ScheduleSettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                )

                Text(
                    text = "$value  ›",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider()
        }
    }
}

private fun toggleMissionDay(
    currentDays: Set<MissionDay>,
    day: MissionDay,
): Set<MissionDay> =
    if (day in currentDays) {
        currentDays - day
    } else {
        currentDays + day
    }

private fun formatMissionTime(
    hour: Int,
    minute: Int,
): String {
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }

    val period = if (hour < 12) "am" else "pm"

    return "%d:%02d %s".format(
        displayHour,
        minute,
        period,
    )
}

private fun formatRepeatDays(
    repeatDays: Set<MissionDay>,
): String =
    when {
        repeatDays.isEmpty() ->
            "Never"

        repeatDays.size == MissionDay.entries.size ->
            "Every day"

        repeatDays == setOf(
            MissionDay.MONDAY,
            MissionDay.TUESDAY,
            MissionDay.WEDNESDAY,
            MissionDay.THURSDAY,
            MissionDay.FRIDAY,
        ) ->
            "Weekdays"

        repeatDays.size == 1 ->
            "Every ${repeatDays.first().fullLabel}"

        else ->
            repeatDays
                .sortedBy { it.ordinal }
                .joinToString(", ") { it.shortLabel }
    }