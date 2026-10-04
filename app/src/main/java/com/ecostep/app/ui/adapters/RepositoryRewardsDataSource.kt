package com.ecostep.app.ui.adapters

import com.ecostep.app.data.model.RedemptionStatus
import com.ecostep.app.data.model.Reward
import com.ecostep.app.data.model.RewardRedemption
import com.ecostep.app.data.repository.EcoPointsRepository
import com.ecostep.app.data.repository.RewardsRepository
import com.ecostep.app.ui.mock.RedeemedReward
import com.ecostep.app.ui.mock.RewardCategory
import com.ecostep.app.ui.mock.RewardOffer
import com.ecostep.app.ui.mock.RewardsData
import com.ecostep.app.ui.mock.RewardsDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Rewards screen data from the catalog, the backend balance and the redemption history. */
class RepositoryRewardsDataSource(
    private val rewardsRepository: RewardsRepository,
    private val ecoPointsRepository: EcoPointsRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : RewardsDataSource {

    override suspend fun getRewardsData(): RewardsData = coroutineScope {
        val stats = async { ecoPointsRepository.getStats() }
        val rewards = async { rewardsRepository.getRewards() }
        val redemptions = async { rewardsRepository.getRedemptions() }
        val now = clock()
        RewardsData(
            pointsBalance = stats.await().pointsBalance,
            availableRewards = rewards.await()
                .filter { it.isRedeemableAt(now) }
                .map { it.toOffer() },
            redeemedRewards = redemptions.await()
                .filter { it.status != RedemptionStatus.CANCELLED }
                .map { it.toRedeemedReward() },
        )
    }

    override suspend fun redeemReward(rewardId: String, requestId: String): RedeemedReward =
        rewardsRepository.redeem(rewardId, requestId).toRedeemedReward()
}

private fun Reward.toOffer() = RewardOffer(
    rewardId = rewardId,
    merchantName = merchantName,
    title = title,
    description = description,
    pointsRequired = pointsRequired,
    category = category.toRewardCategory(),
)

private fun RewardRedemption.toRedeemedReward() = RedeemedReward(
    redemptionId = redemptionId,
    reward = RewardOffer(
        rewardId = rewardId,
        merchantName = merchantName,
        title = rewardTitle,
        description = rewardDescription,
        pointsRequired = pointsSpent,
        category = category.toRewardCategory(),
    ),
    redemptionCode = redemptionCode,
    redeemedAtMillis = redeemedAtMillis ?: 0L,
    expiresAtMillis = expiresAtMillis,
    isUsed = status == RedemptionStatus.USED,
)

private fun String.toRewardCategory(): RewardCategory = when (lowercase()) {
    "food_and_drink", "food & drink", "food" -> RewardCategory.FOOD_AND_DRINK
    "shopping" -> RewardCategory.SHOPPING
    "transport" -> RewardCategory.TRANSPORT
    else -> RewardCategory.OTHER
}
