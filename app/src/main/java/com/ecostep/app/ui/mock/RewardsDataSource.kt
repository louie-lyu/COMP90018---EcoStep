package com.ecostep.app.ui.mock

data class RewardOffer(
    val rewardId: String,
    val merchantName: String,
    val title: String,
    val description: String,
    val pointsRequired: Int,
    val category: RewardCategory,
)

enum class RewardCategory(
    val displayName: String,
) {
    FOOD_AND_DRINK("Food & Drink"),
    SHOPPING("Shopping"),
    TRANSPORT("Transport"),
    OTHER("Other"),
}

data class RedeemedReward(
    val redemptionId: String,
    val reward: RewardOffer,
    val redemptionCode: String,
    val redeemedAtMillis: Long,
    val expiresAtMillis: Long?,
    val isUsed: Boolean,
)

data class RewardsData(
    val pointsBalance: Int,
    val availableRewards: List<RewardOffer>,
    val redeemedRewards: List<RedeemedReward>,
)

interface RewardsDataSource {

    suspend fun getRewardsData(): RewardsData
}

/**
 * Temporary reward data used while the user-points and reward services
 * are unavailable.
 *
 * TODO(Rewards):
 * Replace this data source with repositories that:
 * - load the signed-in user's verified EcoPoints balance;
 * - load currently available rewards from Firebase or a reward provider;
 * - redeem rewards securely and prevent duplicate redemption;
 * - store redemption codes, expiry dates and usage status;
 * - update the user's balance after a successful redemption.
 */
class MockRewardsDataSource : RewardsDataSource {

    override suspend fun getRewardsData(): RewardsData {
        return RewardsData(
            pointsBalance = 780,
            availableRewards = listOf(
                RewardOffer(
                    rewardId = "reward-coffee-1",
                    merchantName = "Green Bean Café",
                    title = "Free regular coffee",
                    description =
                        "Redeem one regular hot or iced coffee.",
                    pointsRequired = 300,
                    category = RewardCategory.FOOD_AND_DRINK,
                ),
                RewardOffer(
                    rewardId = "reward-grocery-1",
                    merchantName = "Local Harvest",
                    title = "\$5 grocery discount",
                    description =
                        "Save \$5 on an eligible purchase.",
                    pointsRequired = 500,
                    category = RewardCategory.SHOPPING,
                ),
                RewardOffer(
                    rewardId = "reward-transport-1",
                    merchantName = "City Bike Share",
                    title = "30-minute bike pass",
                    description =
                        "Unlock a bike for one 30-minute ride.",
                    pointsRequired = 650,
                    category = RewardCategory.TRANSPORT,
                ),
                RewardOffer(
                    rewardId = "reward-lunch-1",
                    merchantName = "Fresh Bowl",
                    title = "Free lunch bowl",
                    description =
                        "Choose one eligible regular lunch bowl.",
                    pointsRequired = 900,
                    category = RewardCategory.FOOD_AND_DRINK,
                ),
            ),
            redeemedRewards = emptyList(),
        )
    }
}