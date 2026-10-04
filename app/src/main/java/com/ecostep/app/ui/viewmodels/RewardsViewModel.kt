package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.repository.BackendException
import com.ecostep.app.ui.mock.RedeemedReward
import com.ecostep.app.ui.mock.RewardOffer
import com.ecostep.app.ui.mock.RewardRedemptionStatus
import com.ecostep.app.ui.mock.RewardsDataSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RewardsUiState(
    val pointsBalance: Int = 0,
    val availableRewards: List<RewardOffer> = emptyList(),
    val activeRedeemedRewards: List<RedeemedReward> = emptyList(),
    val rewardHistory: List<RedeemedReward> = emptyList(),
    val selectedReward: RewardOffer? = null,
    val recentlyRedeemedReward: RedeemedReward? = null,
    val isLoading: Boolean = true,
    val isRedeeming: Boolean = false,
    val errorMessage: String? = null,
)

class RewardsViewModel(
    private val rewardsDataSource: RewardsDataSource,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    /**
     * Request ID of a redemption whose outcome is unknown (e.g. network failure). Retrying the
     * same reward reuses it, so the backend can never charge twice for one intent.
     */
    private var pendingRequest: Pair<String, String>? = null


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

                val currentTimeMillis =
                    System.currentTimeMillis()

                val activeRedeemedRewards =
                    rewardsData.redeemedRewards.filter { redeemedReward ->
                        redeemedReward.statusAt(currentTimeMillis) ==
                                RewardRedemptionStatus.AVAILABLE
                    }

                val rewardHistory =
                    rewardsData.redeemedRewards.filter { redeemedReward ->
                        redeemedReward.statusAt(currentTimeMillis) !=
                                RewardRedemptionStatus.AVAILABLE
                    }

                _uiState.update {
                    it.copy(
                        pointsBalance = rewardsData.pointsBalance,
                        availableRewards =
                            rewardsData.availableRewards,
                        activeRedeemedRewards =
                            activeRedeemedRewards,
                        rewardHistory = rewardHistory,
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

        // Ignore repeated taps while a request is in flight.
        if (currentState.isRedeeming) {
            return
        }

        // A quick local check for feedback only; the backend re-checks inside its transaction.
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

        val requestId = pendingRequest
            ?.takeIf { (rewardId, _) -> rewardId == reward.rewardId }
            ?.second
            ?: requestIdFactory()
        pendingRequest = reward.rewardId to requestId

        _uiState.update {
            it.copy(isRedeeming = true, errorMessage = null)
        }

        viewModelScope.launch {
            try {
                val redeemedReward =
                    rewardsDataSource.redeemReward(
                        rewardId = reward.rewardId,
                        requestId = requestId,
                    )
                pendingRequest = null

                _uiState.update {
                    it.copy(
                        selectedReward = null,
                        recentlyRedeemedReward = redeemedReward,
                        isRedeeming = false,
                    )
                }

                // Balance and history come from the server, never from local arithmetic.
                loadRewards()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // A definitive rejection ends this intent; an unknown outcome keeps the ID
                // so that "try again" is safe.
                if (exception is BackendException && exception.code in DEFINITIVE_REJECTIONS) {
                    pendingRequest = null
                }
                _uiState.update {
                    it.copy(
                        isRedeeming = false,
                        selectedReward = null,
                        errorMessage =
                            exception.message
                                ?: "Unable to redeem this reward.",
                    )
                }
            }
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

    private companion object {
        val DEFINITIVE_REJECTIONS = setOf(
            "FAILED_PRECONDITION",
            "INVALID_ARGUMENT",
            "NOT_FOUND",
            "ALREADY_EXISTS",
            "PERMISSION_DENIED",
        )
    }
}
