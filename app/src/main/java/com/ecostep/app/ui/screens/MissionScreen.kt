package com.ecostep.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ecostep.app.ui.components.MissionListCard
import com.ecostep.app.ui.mock.MissionPageItem
import com.ecostep.app.ui.viewmodels.MissionViewModel
import com.ecostep.app.ui.components.MissionEditorBottomSheet
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf

@Composable
fun MissionScreen(
    onStartMission: (String) -> Unit = {},
    onEditMission: (String) -> Unit = {},
    missionViewModel: MissionViewModel,
    onOpenJourneyReview: (String) -> Unit,
    onOpenWeeklyInsight: () -> Unit = {},
) {
    LaunchedEffect(missionViewModel) {
        missionViewModel.journeyReviewEvents.collect { journeyId ->
            onOpenJourneyReview(journeyId)
        }
    }

    val uiState by missionViewModel.uiState.collectAsState()

    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(
            if (uiState.activeMission != null) 0 else 1,
        )
    }



    LaunchedEffect(
        uiState.activeMission?.mission?.missionId,
    ) {
        selectedTabIndex =
            if (uiState.activeMission != null) {
                0
            } else {
                1
            }
    }

    var missionBeingEdited by remember {
        mutableStateOf<MissionPageItem?>(null)
    }

    var showEndMissionDialog by rememberSaveable {
        mutableStateOf(false)
    }

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
                    text = "Missions",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                Text(
                    text =
                        "Build greener habits from your regular journeys.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            TextButton(
                onClick = onOpenWeeklyInsight,
            ) {
                Text("View Weekly Insight →")
            }
        }

        item {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = {
                        selectedTabIndex = 0
                    },
                    text = {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )

                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        selectedTabIndex = 1
                    },
                    text = {
                        Text(
                            text = "Upcoming",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                )
            }
        }

        if (selectedTabIndex == 0) {
            item {
                val activeMission = uiState.activeMission

                if (activeMission == null) {
                    ActiveMissionEmptyState()
                } else {
                    ActiveMissionCard(
                        item = activeMission,
                        onEndMission = {
                            showEndMissionDialog = true
                        },
                    )
                }
            }
        } else {
            uiState.suggestedMission?.let { suggested ->
                item {
                    Text(
                        text = "Suggested for you",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                item {
                    SuggestedMissionCard(
                        item = suggested,
                        onAccept = {
                            missionViewModel.acceptSuggestedMission()
                        },
                        onEdit = {
                            missionBeingEdited = suggested
                        },
                        onDismiss = {
                            missionViewModel.dismissSuggestedMission()
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Upcoming",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (uiState.upcomingMissions.isEmpty()) {
                item {
                    Text(
                        text = "No upcoming EcoMissions.",
                        style = MaterialTheme.typography.bodyLarge,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(
                    items = uiState.upcomingMissions,
                    key = { item ->
                        item.mission.missionId
                    },
                ) { item ->
                    MissionListCard(
                        mission = item.mission,
                        scheduleLabel = item.scheduleLabel,
                        todayStatusLabel = item.todayStatusLabel,
                        canStartToday = !item.completedToday,
                        onEdit = {
                            missionBeingEdited = item

                            onEditMission(
                                item.mission.missionId,
                            )
                        },
                        onStart = {
                            missionViewModel.startMission(
                                item.mission.missionId,
                            )

                            selectedTabIndex = 0

                            onStartMission(
                                item.mission.missionId,
                            )
                        },
                        onSkipToday = {
                            missionViewModel.skipMissionToday(
                                item.mission.missionId,
                            )
                        },
                    )
                }
            }
        }
    }

    missionBeingEdited?.let { item ->
        val routeEstimate by missionViewModel.routeEstimate.collectAsState()

        MissionEditorBottomSheet(
            item = item,
            onDismiss = {
                missionViewModel.clearRouteEstimate()
                missionBeingEdited = null
            },
            onSave = { updatedMission ->
                missionViewModel.updateMission(updatedMission)
                missionBeingEdited = null
            },
            routeEstimate = routeEstimate,
            onRouteChanged = missionViewModel::estimateRoute,
        )
    }
    if (showEndMissionDialog) {
        AlertDialog(
            onDismissRequest = {
                showEndMissionDialog = false
            },
            title = {
                Text("End this mission?")
            },
            text = {
                Text(
                    "Your verified journey progress will be saved. " +
                            "You may receive partial EcoPoints and carbon " +
                            "savings, but the mission completion bonus will " +
                            "not be awarded.",
                )
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showEndMissionDialog = false
                    },
                ) {
                    Text("Keep going")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        missionViewModel.endActiveMission()
                        showEndMissionDialog = false
                    },
                ) {
                    Text(
                        text = "End mission",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun ActiveMissionEmptyState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "No active EcoMission",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text =
                    "Start an upcoming mission when you are ready to travel.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActiveMissionCard(
    item: MissionPageItem,
    onEndMission: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Active EcoMission",
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer,
                )

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = "In progress",
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 6.dp,
                        ),
                        style =
                            MaterialTheme.typography.labelMedium,
                        color =
                            MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Text(
                text = item.mission.routeTitle,
                style = MaterialTheme.typography.titleMedium,
                color =
                    MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(
                text = item.mission.transportLabel,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Text(
                text = "Journey tracking is active.",
                style = MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 10.dp,
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text =
                                "+${item.mission.estimatedEcoPoints} EcoPoints",
                            style =
                                MaterialTheme.typography.titleMedium,
                            color =
                                MaterialTheme.colorScheme.primary,
                        )

                        Text(
                            text = "Complete to earn",
                            style =
                                MaterialTheme.typography.labelMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color =
                        MaterialTheme.colorScheme
                            .secondaryContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 10.dp,
                        ),
                        verticalArrangement =
                            Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text =
                                String.format(java.util.Locale.getDefault(), "%.2f kg CO₂ saved", item.mission.estimatedCarbonSavedKg),
                            style =
                                MaterialTheme.typography.titleMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSecondaryContainer,
                        )

                        Text(
                            text = "Estimated saving",
                            style =
                                MaterialTheme.typography.labelMedium,
                            color =
                                MaterialTheme.colorScheme
                                    .onSecondaryContainer,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onEndMission,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("End mission")
            }
        }
    }
}

@Composable
private fun SuggestedMissionCard(
    item: MissionPageItem,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = "New EcoMission suggestion",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = item.mission.routeTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = item.mission.transportLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = item.scheduleLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (item.explanation.isNotBlank()) {
                Text(
                    text = item.explanation,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                            text = "+${item.mission.estimatedEcoPoints} EcoPoints",
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
                            text =
                                String.format(java.util.Locale.getDefault(), "%.2f kg CO₂ saved", item.mission.estimatedCarbonSavedKg),
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

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Accept Mission")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit")
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}