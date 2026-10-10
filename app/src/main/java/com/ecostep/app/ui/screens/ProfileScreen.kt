package com.ecostep.app.ui.screens

import com.ecostep.app.ui.format.carbonGramsText
import com.ecostep.app.ui.format.carbonKilogramsAsGramsText

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ecostep.app.ui.mock.CarbonRankingEntry
import com.ecostep.app.ui.mock.FriendRequestStatus
import com.ecostep.app.ui.mock.IncomingFriendRequestUi
import com.ecostep.app.ui.mock.FriendSearchResult
import com.ecostep.app.ui.mock.ProfileData
import com.ecostep.app.ui.mock.RankingPeriod
import com.ecostep.app.ui.mock.RankingScope
import com.ecostep.app.ui.viewmodels.ProfileViewModel
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onViewJourneyHistory: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onSignOut: () -> Unit = {},
    /** Permissions may have changed; lets features that depend on them re-check. */
    onPermissionsChanged: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()

    var showSettings by rememberSaveable {
        mutableStateOf(false)
    }

    var showAddFriend by rememberSaveable {
        mutableStateOf(false)
    }

    var showEditName by rememberSaveable {
        mutableStateOf(false)
    }

    var showReminderOptions by rememberSaveable {
        mutableStateOf(false)
    }

    when {
        uiState.isLoading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        uiState.profileData != null -> {
            val profileData = uiState.profileData!!
            val communityRankingEnabled =
                profileData.preferences.communityRankingEnabled

            LaunchedEffect(
                communityRankingEnabled,
                uiState.selectedRankingScope,
            ) {
                if (
                    !communityRankingEnabled &&
                    uiState.selectedRankingScope ==
                    RankingScope.COMMUNITY
                ) {
                    viewModel.selectRankingScope(
                        RankingScope.FRIENDS,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 18.dp,
                    vertical = 20.dp,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(18.dp),
            ) {
                item {
                    ProfileHeader(
                        onOpenSettings = {
                            showSettings = true
                        },
                    )
                }

                item {
                    UserSummaryCard(
                        profileData = profileData,
                        onEditName = {
                            showEditName = true
                        },
                        onAddFriend = {
                            viewModel.clearFriendSearch()
                            showAddFriend = true
                        },
                    )
                }

                if (uiState.incomingFriendRequests.isNotEmpty()) {
                    item {
                        IncomingFriendRequestsCard(
                            requests = uiState.incomingFriendRequests,
                            respondingRequestId =
                                uiState.respondingFriendRequestId,
                            onRespond =
                                viewModel::respondToFriendRequest,
                        )
                    }
                }

                item {
                    ImpactSummaryCard(
                        profileData = profileData,
                    )
                }

                item {
                    OutlinedButton(
                        onClick = onViewJourneyHistory,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("View Journey History")
                    }
                }

                item {
                    RankingHeader(
                        selectedScope =
                            uiState.selectedRankingScope,
                        showCommunityRanking =
                            communityRankingEnabled,
                        onSelectScope =
                            viewModel::selectRankingScope,
                    )
                }

                item {
                    RankingPeriodSelector(
                        selectedPeriod =
                            uiState.selectedRankingPeriod,
                        onSelectPeriod =
                            viewModel::selectRankingPeriod,
                    )
                }

                if (
                    uiState.selectedRankingScope ==
                    RankingScope.COMMUNITY
                ) {
                    item {
                        Text(
                            text = "Community Top 10",
                            style =
                                MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                if (uiState.isRankingLoading) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                } else if (uiState.rankingEntries.isEmpty()) {
                    item {
                        RankingEmptyState(
                            scope =
                                uiState.selectedRankingScope,
                        )
                    }
                } else {
                    items(
                        items = uiState.rankingEntries,
                        key = { entry ->
                            "${entry.userId}-${entry.rank}"
                        },
                    ) { entry ->
                        RankingRow(entry = entry)
                    }
                }

                item {
                    Text(
                        text =
                            "Rankings are based on verified carbon emissions saved from completed journeys.",
                        style =
                            MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (showSettings) {
                ProfileSettingsPage(
                    profileData = profileData,
                    onDismiss = {
                        showSettings = false
                    },
                    onEditName = {
                        showSettings = false
                        showEditName = true
                    },
                    onRoutineLearningChanged =
                        viewModel::setRoutineLearningEnabled,
                    onMissionNotificationsChanged =
                        viewModel::setMissionNotificationsEnabled,
                    onReminderTimeClick = {
                        showSettings = false
                        showReminderOptions = true
                    },
                    onAutomaticDetectionChanged =
                        viewModel::setAutomaticJourneyDetectionEnabled,
                    onCommunityRankingChanged =
                        viewModel::setCommunityRankingEnabled,
                    onOpenLocationSettings = {
                        showSettings = false
                        onOpenLocationSettings()
                    },
                    onSignOut = {
                        showSettings = false
                        onSignOut()
                    },
                    onPermissionsChanged = onPermissionsChanged,
                )
            }

            if (showAddFriend) {
                AddFriendSheet(
                    query = uiState.friendSearchQuery,
                    results = uiState.friendSearchResults,
                    isSearching =
                        uiState.isSearchingFriends,
                    requestUserId =
                        uiState.friendRequestUserId,
                    onQueryChanged =
                        viewModel::updateFriendSearchQuery,
                    onSearch =
                        viewModel::searchFriends,
                    onSendRequest =
                        viewModel::sendFriendRequest,
                    onDismiss = {
                        showAddFriend = false
                        viewModel.clearFriendSearch()
                    },
                )
            }

            if (showEditName) {
                EditDisplayNameDialog(
                    currentName =
                        profileData.user.displayName,
                    isSaving =
                        uiState.isSavingProfile,
                    onSave = { updatedName ->
                        viewModel.updateDisplayName(
                            updatedName,
                        )
                        showEditName = false
                    },
                    onDismiss = {
                        showEditName = false
                    },
                )
            }

            if (showReminderOptions) {
                ReminderTimeDialog(
                    selectedMinutes =
                        profileData.preferences
                            .defaultReminderMinutes,
                    onSelect = { minutes ->
                        viewModel.setDefaultReminderMinutes(
                            minutes,
                        )
                        showReminderOptions = false
                        showSettings = true
                    },
                    onDismiss = {
                        showReminderOptions = false
                        showSettings = true
                    },
                )
            }
        }
    }

    uiState.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest =
                viewModel::dismissError,
            title = {
                Text("Something went wrong")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick =
                        viewModel::dismissError,
                ) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Profile",
                style =
                    MaterialTheme.typography.headlineLarge,
            )

            Text(
                text = "Your impact and community",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FilledTonalButton(
            onClick = onOpenSettings,
        ) {
            Text("Settings")
        }
    }
}

@Composable
private fun UserSummaryCard(
    profileData: ProfileData,
    onEditName: () -> Unit,
    onAddFriend: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme.colorScheme.primary,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                profileData.user.displayName
                                    .firstOrNull()
                                    ?.uppercase()
                                    ?: "?",
                            style =
                                MaterialTheme.typography.headlineMedium,
                            color =
                                MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text =
                            profileData.user.displayName,
                        style =
                            MaterialTheme.typography.titleMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer,
                    )

                    Text(
                        text = profileData.user.email,
                        style =
                            MaterialTheme.typography.bodyMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer,
                    )

                    Text(
                        text = "Account email",
                        style =
                            MaterialTheme.typography.labelMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onPrimaryContainer,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onEditName,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit profile")
                }

                Button(
                    onClick = onAddFriend,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Add friend")
                }
            }
        }
    }
}

@Composable
private fun IncomingFriendRequestsCard(
    requests: List<IncomingFriendRequestUi>,
    respondingRequestId: String?,
    onRespond: (requestId: String, accept: Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Friend requests",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            requests.forEach { request ->
                val isResponding = respondingRequestId == request.requestId
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = request.senderDisplayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    TextButton(
                        onClick = { onRespond(request.requestId, false) },
                        enabled = respondingRequestId == null,
                    ) {
                        Text("Decline")
                    }
                    Button(
                        onClick = { onRespond(request.requestId, true) },
                        enabled = respondingRequestId == null,
                    ) {
                        if (isResponding) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Accept")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImpactSummaryCard(
    profileData: ProfileData,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Your impact",
            style =
                MaterialTheme.typography.titleMedium,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            ImpactValue(
                value =
                    profileData.impactSummary
                        .ecoPointsBalance
                        .toString(),
                label = "EcoPoints",
                modifier = Modifier.weight(1f),
            )

            ImpactValue(
                value =
                    profileData.impactSummary
                        .totalJourneys
                        .toString(),
                label = "Counted journeys",
                modifier = Modifier.weight(1f),
            )

            ImpactValue(
                value =
                    carbonKilogramsAsGramsText(profileData.impactSummary.carbonSavedKg),
                label = "Counted CO₂ saved",
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "All-time server totals. CO₂ savings use a car journey of the same distance as the baseline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profileData.impactSummary.isAwaitingServer) {
            Text(
                text = if (profileData.impactSummary.pendingJourneys > 0) {
                    "Pending calculation: ${profileData.impactSummary.pendingJourneys} journeys · " +
                        "${carbonGramsText(profileData.impactSummary.pendingCarbonSavedGrams)} estimated CO₂. " +
                        "Excluded from totals and rankings until processed."
                } else {
                    "Server totals are updating. EcoPoints are awarded for eligible completed missions."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImpactValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 8.dp,
                        vertical = 14.dp,
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style =
                    MaterialTheme.typography.titleMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = label,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RankingHeader(
    selectedScope: RankingScope,
    showCommunityRanking: Boolean,
    onSelectScope: (RankingScope) -> Unit,
) {
    Column(
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Carbon ranking",
            style =
                MaterialTheme.typography.headlineMedium,
        )

        if (showCommunityRanking) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp),
            ) {
                RankingScope.entries.forEach { scope ->
                    FilterChip(
                        selected =
                            selectedScope == scope,
                        onClick = {
                            onSelectScope(scope)
                        },
                        label = {
                            Text(scope.displayName)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color =
                    MaterialTheme.colorScheme
                        .secondaryContainer,
            ) {
                Text(
                    text = "Friends",
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 9.dp,
                    ),
                    style =
                        MaterialTheme.typography.labelLarge,
                    color =
                        MaterialTheme.colorScheme
                            .onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RankingPeriodSelector(
    selectedPeriod: RankingPeriod,
    onSelectPeriod: (RankingPeriod) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(10.dp),
    ) {
        RankingPeriod.entries.forEach { period ->
            FilterChip(
                selected =
                    selectedPeriod == period,
                onClick = {
                    onSelectPeriod(period)
                },
                label = {
                    Text(period.displayName)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RankingRow(
    entry: CarbonRankingEntry,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                if (entry.isCurrentUser) {
                    MaterialTheme.colorScheme
                        .primaryContainer
                } else {
                    MaterialTheme.colorScheme
                        .surfaceVariant
                },
        ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = rankingBadgeColor(entry.rank),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.rank.toString(),
                        style =
                            MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment =
                        Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = entry.displayName,
                        style =
                            MaterialTheme.typography.titleMedium,
                    )

                    if (entry.isCurrentUser) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color =
                                MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "You",
                                modifier =
                                    Modifier.padding(
                                        horizontal = 8.dp,
                                        vertical = 2.dp,
                                    ),
                                style =
                                    MaterialTheme.typography.labelMedium,
                                color =
                                    MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Text(
                    text =
                        "${entry.completedJourneys} completed journeys",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text =
                        carbonKilogramsAsGramsText(entry.carbonSavedKg),
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    text = "CO₂ saved",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun rankingBadgeColor(
    rank: Int,
) = when (rank) {
    1 ->
        MaterialTheme.colorScheme.tertiaryContainer

    2 ->
        MaterialTheme.colorScheme.secondaryContainer

    3 ->
        MaterialTheme.colorScheme.primaryContainer

    else ->
        MaterialTheme.colorScheme.surface
}

@Composable
private fun RankingEmptyState(
    scope: RankingScope,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text =
                    if (scope == RankingScope.FRIENDS) {
                        "No friends to rank yet"
                    } else {
                        "No community ranking available"
                    },
                style =
                    MaterialTheme.typography.titleMedium,
            )

            Text(
                text =
                    if (scope == RankingScope.FRIENDS) {
                        "Add friends to compare your carbon savings."
                    } else {
                        "Complete verified journeys to join the ranking."
                    },
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSettingsPage(
    profileData: ProfileData,
    onDismiss: () -> Unit,
    onEditName: () -> Unit,
    onRoutineLearningChanged: (Boolean) -> Unit,
    onMissionNotificationsChanged: (Boolean) -> Unit,
    onReminderTimeClick: () -> Unit,
    onAutomaticDetectionChanged: (Boolean) -> Unit,
    onCommunityRankingChanged: (Boolean) -> Unit,
    onOpenLocationSettings: () -> Unit,
    onSignOut: () -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    val preferences = profileData.preferences

    // Re-read permission state after a request and whenever the user returns from Settings.
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionCheck by remember { mutableIntStateOf(0) }
    val currentOnPermissionsChanged by rememberUpdatedState(onPermissionsChanged)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionCheck++
                currentOnPermissionsChanged()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionCheck++
        currentOnPermissionsChanged()
    }
    val missingNotificationPermissions = remember(permissionCheck) {
        missingPermissions(context, notificationPermissions())
    }
    val missingDetectionPermissions = remember(permissionCheck) {
        missingPermissions(context, detectionPermissions())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "←",
                        style =
                            MaterialTheme.typography
                                .headlineLarge,
                        color =
                            MaterialTheme.colorScheme
                                .onBackground,
                    )
                }

                Text(
                    text = "Settings",
                    style =
                        MaterialTheme.typography
                            .headlineLarge,
                    color =
                        MaterialTheme.colorScheme
                            .onBackground,
                )
            }

            HorizontalDivider()

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 22.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement =
                    Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SettingsSectionTitle("Profile")
                }

                item {
                    SettingsCard {
                        SettingsActionRow(
                            title = "Display Name",
                            supportingText =
                                profileData.user.displayName,
                            actionLabel = "Edit",
                            onClick = onEditName,
                        )
                    }
                }

                item {
                    SettingsSectionTitle("Missions")
                }

                item {
                    SettingsCard {
                        SettingsSwitchRow(
                            title = "Routine Learning",
                            supportingText =
                                "Learn from recurring journeys",
                            checked =
                                preferences
                                    .routineLearningEnabled,
                            onCheckedChange =
                                onRoutineLearningChanged,
                        )

                        HorizontalDivider(
                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                ),
                        )

                        SettingsSwitchRow(
                            title = "Mission Notifications",
                            supportingText =
                                "Receive upcoming mission reminders",
                            checked =
                                preferences
                                    .missionNotificationsEnabled,
                            onCheckedChange =
                                onMissionNotificationsChanged,
                            warning = if (
                                preferences.missionNotificationsEnabled &&
                                missingNotificationPermissions.isNotEmpty()
                            ) {
                                "Notifications are blocked, so reminders cannot appear."
                            } else {
                                null
                            },
                            onFixWarning = {
                                permissionLauncher.launch(
                                    missingNotificationPermissions.toTypedArray(),
                                )
                            },
                        )

                        HorizontalDivider(
                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                ),
                        )

                        SettingsActionRow(
                            title = "Default Reminder Time",
                            supportingText =
                                formatReminderTime(
                                    preferences
                                        .defaultReminderMinutes,
                                ),
                            actionLabel = "Change",
                            onClick = onReminderTimeClick,
                        )

                        HorizontalDivider(
                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                ),
                        )

                        SettingsSwitchRow(
                            title =
                                "Automatic Journey Detection",
                            supportingText =
                                "Auto-start while EcoStep is open; end and save manually",
                            checked =
                                preferences
                                    .automaticJourneyDetectionEnabled,
                            onCheckedChange =
                                onAutomaticDetectionChanged,
                            warning = if (
                                preferences.automaticJourneyDetectionEnabled &&
                                missingDetectionPermissions.isNotEmpty()
                            ) {
                                "Not active: precise location and physical activity access are needed."
                            } else {
                                null
                            },
                            onFixWarning = {
                                permissionLauncher.launch(
                                    missingDetectionPermissions.toTypedArray(),
                                )
                            },
                        )
                    }
                }

                item {
                    SettingsSectionTitle(
                        "Privacy & Community",
                    )
                }

                item {
                    SettingsCard {
                        SettingsSwitchRow(
                            title =
                                "Community Ranking",
                            supportingText =
                                "Show your carbon savings in the community ranking",
                            checked =
                                preferences
                                    .communityRankingEnabled,
                            onCheckedChange =
                                onCommunityRankingChanged,
                        )

                        HorizontalDivider(
                            modifier =
                                Modifier.padding(
                                    horizontal = 16.dp,
                                ),
                        )

                        SettingsActionRow(
                            title = "Location Permission",
                            supportingText =
                                "Manage permission in Android settings",
                            actionLabel = "Open",
                            onClick =
                                onOpenLocationSettings,
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                item {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            content = content,
        )
    }
}

@Composable
private fun SettingsSectionTitle(
    title: String,
) {
    Text(
        text = title,
        style =
            MaterialTheme.typography.labelMedium,
        color =
            MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

private fun notificationPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

private fun detectionPermissions(): List<String> =
    buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
    }

private fun missingPermissions(context: Context, permissions: List<String>): List<String> =
    permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun SettingsSwitchRow(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    /** Shown when the switch is on but cannot work, e.g. a permission is missing. */
    warning: String? = null,
    onFixWarning: () -> Unit = {},
) {
    Column {
        SettingsSwitchRowContent(
            title = title,
            supportingText = supportingText,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )

        warning?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                TextButton(onClick = onFixWarning) {
                    Text("Allow")
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRowContent(
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onCheckedChange(!checked)
                }
                .padding(
                    horizontal = 16.dp,
                    vertical = 16.dp,
                ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
            )

            Text(
                text = supportingText,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    supportingText: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = 16.dp,
                    vertical = 17.dp,
                ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
            )

            Text(
                text = supportingText,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            )
        }

        Text(
            text = actionLabel,
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFriendSheet(
    query: String,
    results: List<FriendSearchResult>,
    isSearching: Boolean,
    requestUserId: String?,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSendRequest: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 36.dp,
                    ),
            verticalArrangement =
                Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Add friend",
                style =
                    MaterialTheme.typography.headlineMedium,
            )

            Text(
                text =
                    "Search using an EcoStep display name or email.",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("Name or email")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch()
                    },
                ),
            )

            Button(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    query.isNotBlank() &&
                            !isSearching,
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Search")
                }
            }

            results.forEach { result ->
                FriendSearchResultCard(
                    result = result,
                    isSending =
                        requestUserId == result.userId,
                    onSendRequest = {
                        onSendRequest(result.userId)
                    },
                )
            }

            if (
                query.isNotBlank() &&
                results.isEmpty() &&
                !isSearching
            ) {
                Text(
                    text =
                        "No matching EcoStep users found.",
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FriendSearchResultCard(
    result: FriendSearchResult,
    isSending: Boolean,
    onSendRequest: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text =
                            result.displayName
                                .firstOrNull()
                                ?.uppercase()
                                ?: "?",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = result.displayName,
                    style =
                        MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = result.email,
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (result.requestStatus) {
                FriendRequestStatus.NONE -> {
                    Button(
                        onClick = onSendRequest,
                        enabled = !isSending,
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Add")
                        }
                    }
                }

                FriendRequestStatus.ALREADY_FRIENDS -> {
                    Text(
                        text = "Friends",
                        color =
                            MaterialTheme.colorScheme.primary,
                    )
                }

                FriendRequestStatus.REQUEST_SENT -> {
                    Text(
                        text = "Sent",
                        color =
                            MaterialTheme.colorScheme.primary,
                    )
                }

                FriendRequestStatus.REQUEST_RECEIVED -> {
                    Text(
                        text = "Wants to connect",
                        color =
                            MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EditDisplayNameDialog(
    currentName: String,
    isSaving: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayName by remember(currentName) {
        mutableStateOf(currentName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit display name")
        },
        text = {
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it
                },
                label = {
                    Text("Display name")
                },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(displayName)
                },
                enabled =
                    displayName.isNotBlank() &&
                            !isSaving,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun ReminderTimeDialog(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val reminderOptions =
        listOf(
            15 to "15 minutes before",
            30 to "30 minutes before",
            60 to "1 hour before",
            120 to "2 hours before",
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Default reminder time")
        },
        text = {
            Column {
                reminderOptions.forEach { option ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(option.first)
                                },
                        verticalAlignment =
                            Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected =
                                selectedMinutes ==
                                        option.first,
                            onClick = {
                                onSelect(option.first)
                            },
                        )

                        Text(option.second)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text("Cancel")
            }
        },
    )
}

private fun formatReminderTime(
    minutes: Int,
): String {
    return when (minutes) {
        15 -> "15 minutes before"
        30 -> "30 minutes before"
        60 -> "1 hour before"
        120 -> "2 hours before"
        else -> "$minutes minutes before"
    }
}
