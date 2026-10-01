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

enum class RewardRedemptionStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}

data class RedeemedReward(
    val redemptionId: String,
    val reward: RewardOffer,
    val redemptionCode: String,
    val redeemedAtMillis: Long,
    val expiresAtMillis: Long?,
    val isUsed: Boolean,
) {
    fun statusAt(
        currentTimeMillis: Long,
    ): RewardRedemptionStatus {
        return when {
            isUsed ->
                RewardRedemptionStatus.USED

            expiresAtMillis != null &&
                    expiresAtMillis <= currentTimeMillis ->
                RewardRedemptionStatus.EXPIRED

            else ->
                RewardRedemptionStatus.AVAILABLE
        }
    }
}

data class RewardsData(
    val pointsBalance: Int,
    val availableRewards: List<RewardOffer>,
    val redeemedRewards: List<RedeemedReward>,
)

interface RewardsDataSource {

    suspend fun getRewardsData(): RewardsData
}

/*
 * TODO(EcoPoints):
 * Replace the mock balance with a shared EcoPoints repository once its
 * ownership and interface have been agreed by the team.
 *
 * The shared repository should provide the signed-in user's accumulated
 * balance. Rewards should read and spend that balance, while verified
 * journeys should add newly calculated EcoPoints to it.
 *
 * TODO(Rewards):
 * Persist redeemed reward history so available, used and expired rewards
 * can be restored after the app restarts.
 */
class MockRewardsDataSource : RewardsDataSource {

    override suspend fun getRewardsData(): RewardsData {
        val currentTimeMillis = System.currentTimeMillis()
        val oneDayMillis = 24L * 60L * 60L * 1000L

        val coffeeReward = RewardOffer(
            rewardId = "reward-coffee-1",
            merchantName = "Green Bean Café",
            title = "Free regular coffee",
            description =
                "Redeem one regular hot or iced coffee.",
            pointsRequired = 300,
            category = RewardCategory.FOOD_AND_DRINK,
        )

        val groceryReward = RewardOffer(
            rewardId = "reward-grocery-1",
            merchantName = "Local Harvest",
            title = "\$5 grocery discount",
            description =
                "Save \$5 on an eligible purchase.",
            pointsRequired = 500,
            category = RewardCategory.SHOPPING,
        )

        val transportReward = RewardOffer(
            rewardId = "reward-transport-1",
            merchantName = "City Bike Share",
            title = "30-minute bike pass",
            description =
                "Unlock a bike for one 30-minute ride.",
            pointsRequired = 650,
            category = RewardCategory.TRANSPORT,
        )

        val lunchReward = RewardOffer(
            rewardId = "reward-lunch-1",
            merchantName = "Fresh Bowl",
            title = "Free lunch bowl",
            description =
                "Choose one eligible regular lunch bowl.",
            pointsRequired = 900,
            category = RewardCategory.FOOD_AND_DRINK,
        )

        return RewardsData(
            pointsBalance = 780,
            availableRewards = listOf(
                coffeeReward,
                groceryReward,
                transportReward,
                lunchReward,
            ),
            redeemedRewards = listOf(
                RedeemedReward(
                    redemptionId = "redemption-active-coffee",
                    reward = coffeeReward,
                    redemptionCode = "ECO-COFFEE-25",
                    redeemedAtMillis =
                        currentTimeMillis - 2L * oneDayMillis,
                    expiresAtMillis =
                        currentTimeMillis + 28L * oneDayMillis,
                    isUsed = false,
                ),
                RedeemedReward(
                    redemptionId = "redemption-used-grocery",
                    reward = groceryReward,
                    redemptionCode = "ECO-SAVE-5",
                    redeemedAtMillis =
                        currentTimeMillis - 20L * oneDayMillis,
                    expiresAtMillis =
                        currentTimeMillis + 10L * oneDayMillis,
                    isUsed = true,
                ),
                RedeemedReward(
                    redemptionId = "redemption-expired-bike",
                    reward = transportReward,
                    redemptionCode = "ECO-BIKE-30",
                    redeemedAtMillis =
                        currentTimeMillis - 45L * oneDayMillis,
                    expiresAtMillis =
                        currentTimeMillis - 15L * oneDayMillis,
                    isUsed = false,
                ),
            ),
        )
    }
}