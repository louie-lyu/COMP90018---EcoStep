package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.ui.mock.MockRewardsDataSource
import com.ecostep.app.ui.mock.RedeemedReward
import com.ecostep.app.ui.mock.RewardOffer
import com.ecostep.app.ui.mock.RewardsDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RewardsUiState(
    val pointsBalance: Int = 0,
    val availableRewards: List<RewardOffer> = emptyList(),
    val redeemedRewards: List<RedeemedReward> = emptyList(),
    val selectedReward: RewardOffer? = null,
    val recentlyRedeemedReward: RedeemedReward? = null,
    val isLoading: Boolean = true,
    val isRedeeming: Boolean = false,
    val errorMessage: String? = null,
)

class RewardsViewModel(
    private val rewardsDataSource: RewardsDataSource =
        MockRewardsDataSource(),
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(RewardsUiState())

    val uiState: StateFlow<RewardsUiState> =
        _uiState.asStateFlow()

    init {
        loadRewards()
    }

    fun loadRewards() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                val rewardsData =
                    rewardsDataSource.getRewardsData()

                _uiState.update {
                    it.copy(
                        pointsBalance = rewardsData.pointsBalance,
                        availableRewards =
                            rewardsData.availableRewards,
                        redeemedRewards =
                            rewardsData.redeemedRewards,
                        isLoading = false,
                    )
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage =
                            exception.message
                                ?: "Unable to load rewards.",
                    )
                }
            }
        }
    }

    fun selectReward(reward: RewardOffer) {
        _uiState.update {
            it.copy(
                selectedReward = reward,
                errorMessage = null,
            )
        }
    }

    fun dismissRewardConfirmation() {
        _uiState.update {
            it.copy(selectedReward = null)
        }
    }

    fun redeemSelectedReward() {
        val currentState = _uiState.value
        val reward = currentState.selectedReward ?: return

        if (currentState.pointsBalance < reward.pointsRequired) {
            _uiState.update {
                it.copy(
                    selectedReward = null,
                    errorMessage =
                        "You do not have enough EcoPoints for this reward.",
                )
            }
            return
        }

        _uiState.update {
            it.copy(isRedeeming = true)
        }

        /*
         * Temporary in-memory redemption used for the UI prototype.
         *
         * TODO(Rewards):
         * Replace this block with a secure redemption request through
         * RewardsRepository. The repository should verify the user's balance,
         * deduct points atomically, create the voucher and persist the result.
         * The repository must also enforce reward stock and per-user redemption limits.
         */
        val currentTimeMillis = System.currentTimeMillis()

        val redeemedReward = RedeemedReward(
            redemptionId =
                "mock-redemption-$currentTimeMillis",
            reward = reward,
            redemptionCode =
                "ECO-${currentTimeMillis.toString().takeLast(6)}",
            redeemedAtMillis = currentTimeMillis,
            expiresAtMillis =
                currentTimeMillis + 30L * 24L * 60L * 60L * 1000L,
            isUsed = false,
        )

        _uiState.update {
            it.copy(
                pointsBalance =
                    it.pointsBalance - reward.pointsRequired,
                redeemedRewards =
                    listOf(redeemedReward) + it.redeemedRewards,
                selectedReward = null,
                recentlyRedeemedReward = redeemedReward,
                isRedeeming = false,
                errorMessage = null,
            )
        }
    }

    fun dismissRedemptionSuccess() {
        _uiState.update {
            it.copy(recentlyRedeemedReward = null)
        }
    }

    fun dismissError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }
}