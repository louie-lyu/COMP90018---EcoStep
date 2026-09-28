package com.ecostep.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ecostep.app.ui.mock.RedeemedReward
import com.ecostep.app.ui.mock.RewardOffer
import com.ecostep.app.ui.viewmodels.RewardsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RewardsScreen(
    viewModel: RewardsViewModel,
) {
    val uiState by
    viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable {
        mutableIntStateOf(0)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        RewardsHeader(
            pointsBalance = uiState.pointsBalance,
        )

        TabRow(
            selectedTabIndex = selectedTabIndex,
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = {
                    selectedTabIndex = 0
                },
                text = {
                    Text("Available")
                },
            )

            Tab(
                selected = selectedTabIndex == 1,
                onClick = {
                    selectedTabIndex = 1
                },
                text = {
                    Text("My Rewards")
                },
            )
        }

        when {
            uiState.isLoading -> {
                RewardsLoadingState()
            }

            selectedTabIndex == 0 -> {
                AvailableRewardsContent(
                    rewards = uiState.availableRewards,
                    pointsBalance = uiState.pointsBalance,
                    onRedeem = viewModel::selectReward,
                )
            }

            else -> {
                RedeemedRewardsContent(
                    rewards = uiState.redeemedRewards,
                )
            }
        }
    }

    uiState.selectedReward?.let { reward ->
        RedeemConfirmationDialog(
            reward = reward,
            pointsBalance = uiState.pointsBalance,
            isRedeeming = uiState.isRedeeming,
            onConfirm = viewModel::redeemSelectedReward,
            onDismiss =
                viewModel::dismissRewardConfirmation,
        )
    }

    uiState.recentlyRedeemedReward?.let { redeemedReward ->
        RedemptionSuccessDialog(
            redeemedReward = redeemedReward,
            onDismiss = {
                viewModel.dismissRedemptionSuccess()
                selectedTabIndex = 1
            },
        )
    }

    uiState.errorMessage?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = {
                Text("Unable to continue")
            },
            text = {
                Text(errorMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::dismissError,
                ) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun RewardsHeader(
    pointsBalance: Int,
) {
    Column(
        modifier = Modifier.padding(
            horizontal = 18.dp,
            vertical = 16.dp,
        ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Rewards",
            style = MaterialTheme.typography.headlineLarge,
        )

        Text(
            text = "Turn your greener journeys into rewards.",
            style = MaterialTheme.typography.bodyLarge,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement =
                    Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Your EcoPoints",
                    style =
                        MaterialTheme.typography.labelLarge,
                    color =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer,
                )

                Text(
                    text = "$pointsBalance",
                    style =
                        MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer,
                )

                Text(
                    text = "Available to redeem",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RewardsLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AvailableRewardsContent(
    rewards: List<RewardOffer>,
    pointsBalance: Int,
    onRedeem: (RewardOffer) -> Unit,
) {
    if (rewards.isEmpty()) {
        RewardsEmptyState(
            title = "No rewards available",
            message =
                "New rewards will appear here when they become available.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp,
                vertical = 16.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Available rewards",
                style =
                    MaterialTheme.typography.headlineMedium,
            )
        }

        items(
            items = rewards,
            key = { reward ->
                reward.rewardId
            },
        ) { reward ->
            RewardOfferCard(
                reward = reward,
                canAfford =
                    pointsBalance >= reward.pointsRequired,
                onRedeem = {
                    onRedeem(reward)
                },
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RewardOfferCard(
    reward: RewardOffer,
    canAfford: Boolean,
    onRedeem: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                color =
                    MaterialTheme.colorScheme
                        .secondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = reward.category.displayName,
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 6.dp,
                    ),
                    style =
                        MaterialTheme.typography.labelMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSecondaryContainer,
                )
            }

            Text(
                text = reward.merchantName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = reward.title,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = reward.description,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "${reward.pointsRequired}",
                        style =
                            MaterialTheme.typography
                                .headlineMedium,
                        color =
                            MaterialTheme.colorScheme.primary,
                    )

                    Text(
                        text = "EcoPoints",
                        style =
                            MaterialTheme.typography.labelMedium,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant,
                    )
                }

                Button(
                    onClick = onRedeem,
                    enabled = canAfford,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text =
                            if (canAfford) {
                                "Redeem"
                            } else {
                                "Not enough points"
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun RedeemedRewardsContent(
    rewards: List<RedeemedReward>,
) {
    if (rewards.isEmpty()) {
        RewardsEmptyState(
            title = "No rewards yet",
            message =
                "Rewards you redeem will be stored here.",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp,
                vertical = 16.dp,
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "My rewards",
                style =
                    MaterialTheme.typography.headlineMedium,
            )
        }

        items(
            items = rewards,
            key = { reward ->
                reward.redemptionId
            },
        ) { redeemedReward ->
            RedeemedRewardCard(
                redeemedReward = redeemedReward,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RedeemedRewardCard(
    redeemedReward: RedeemedReward,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = redeemedReward.reward.merchantName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = redeemedReward.reward.title,
                style = MaterialTheme.typography.titleMedium,
            )

            Text(
                text = "Redemption code",
                style = MaterialTheme.typography.labelMedium,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = redeemedReward.redemptionCode,
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            redeemedReward.expiresAtMillis?.let {
                Text(
                    text =
                        "Expires ${formatRewardDate(it)}",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant,
                )
            }

            Text(
                text =
                    if (redeemedReward.isUsed) {
                        "Used"
                    } else {
                        "Ready to use"
                    },
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (redeemedReward.isUsed) {
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
    }
}

@Composable
private fun RewardsEmptyState(
    title: String,
    message: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.headlineMedium,
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RedeemConfirmationDialog(
    reward: RewardOffer,
    pointsBalance: Int,
    isRedeeming: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val remainingBalance =
        pointsBalance - reward.pointsRequired

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Redeem reward?")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text =
                        "${reward.merchantName} — ${reward.title}",
                )

                Text(
                    text =
                        "${reward.pointsRequired} EcoPoints will be deducted.",
                )

                Text(
                    text =
                        "Remaining balance: $remainingBalance EcoPoints",
                    style =
                        MaterialTheme.typography.labelLarge,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRedeeming,
            ) {
                Text(
                    if (isRedeeming) {
                        "Redeeming..."
                    } else {
                        "Confirm"
                    },
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isRedeeming,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RedemptionSuccessDialog(
    redeemedReward: RedeemedReward,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Reward redeemed!")
        },
        text = {
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = redeemedReward.reward.title,
                )

                Text(
                    text = "Your redemption code is:",
                )

                Text(
                    text = redeemedReward.redemptionCode,
                    style =
                        MaterialTheme.typography
                            .headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Text(
                    text =
                        "You can find this reward again under My Rewards.",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
            ) {
                Text("View My Rewards")
            }
        },
    )
}

private fun formatRewardDate(
    timeMillis: Long,
): String {
    val formatter = SimpleDateFormat(
        "d MMM yyyy",
        Locale.getDefault(),
    )

    return formatter.format(Date(timeMillis))
}