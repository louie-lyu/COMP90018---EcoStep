package com.ecostep.app.ui.viewmodels

import com.ecostep.app.data.model.RedemptionStatus
import com.ecostep.app.data.model.Reward
import com.ecostep.app.data.model.RewardRedemption
import com.ecostep.app.data.model.UserStats
import com.ecostep.app.data.repository.BackendException
import com.ecostep.app.data.repository.RewardsRepository
import com.ecostep.app.testing.FakeEcoPointsRepository
import com.ecostep.app.testing.MainDispatcherRule
import com.ecostep.app.ui.adapters.RepositoryRewardsDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RewardsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val points = FakeEcoPointsRepository(UserStats(pointsBalance = 700))

    /** Behaves like the callable: idempotent per requestId, balance owned by the "server". */
    private inner class FakeRewardsBackend : RewardsRepository {
        val requests = mutableListOf<Pair<String, String>>()
        val redemptions = linkedMapOf<String, RewardRedemption>()
        var gate: CompletableDeferred<Unit>? = null
        var failNext: Exception? = null

        val coffee = Reward("coffee", "Green Bean", "Coffee", "One coffee", 300, "food_and_drink", true, null, null, 5)

        override suspend fun getRewards() = listOf(
            coffee,
            coffee.copy(rewardId = "retired", active = false),
        )

        override suspend fun getRedemptions() = redemptions.values.toList()

        override suspend fun redeem(rewardId: String, requestId: String): RewardRedemption {
            requests += rewardId to requestId
            gate?.await()
            failNext?.let { failNext = null; throw it }
            redemptions[requestId]?.let { return it }
            val reward = getRewards().first { it.rewardId == rewardId }
            points.stats = points.stats.copy(pointsBalance = points.stats.pointsBalance - reward.pointsRequired)
            return RewardRedemption(
                requestId, rewardId, reward.title, reward.merchantName, reward.description, reward.category,
                reward.pointsRequired, "ECO-TEST-${redemptions.size}", RedemptionStatus.AVAILABLE, 1L, Long.MAX_VALUE, null,
            ).also { redemptions[requestId] = it }
        }
    }

    private val backend = FakeRewardsBackend()
    private var nextId = 0
    private fun viewModel() = RewardsViewModel(
        RepositoryRewardsDataSource(backend, points),
        requestIdFactory = { "request-${++nextId}" },
    )

    @Test
    fun `catalog shows only redeemable rewards and the server balance`() {
        val state = viewModel().uiState.value

        assertEquals(700, state.pointsBalance)
        assertEquals(listOf("coffee"), state.availableRewards.map { it.rewardId })
    }

    @Test
    fun `repeated taps while redeeming send a single request`() = runTest {
        backend.gate = CompletableDeferred()
        val viewModel = viewModel()
        val coffee = viewModel.uiState.value.availableRewards.single()

        viewModel.selectReward(coffee)
        viewModel.redeemSelectedReward()
        viewModel.redeemSelectedReward()
        viewModel.redeemSelectedReward()
        assertTrue(viewModel.uiState.value.isRedeeming)
        backend.gate!!.complete(Unit)

        assertEquals(1, backend.requests.size)
        assertEquals(400, viewModel.uiState.value.pointsBalance)
        assertEquals("ECO-TEST-0", viewModel.uiState.value.recentlyRedeemedReward?.redemptionCode)
        assertEquals(1, viewModel.uiState.value.activeRedeemedRewards.size)
    }

    @Test
    fun `retry after a network failure reuses the request ID and charges once`() {
        val viewModel = viewModel()
        val coffee = viewModel.uiState.value.availableRewards.single()
        backend.failNext = BackendException("Network unavailable. Please try again.", "UNAVAILABLE")

        viewModel.selectReward(coffee)
        viewModel.redeemSelectedReward()
        assertEquals("Network unavailable. Please try again.", viewModel.uiState.value.errorMessage)
        assertEquals(700, viewModel.uiState.value.pointsBalance)

        viewModel.dismissError()
        viewModel.selectReward(coffee)
        viewModel.redeemSelectedReward()

        assertEquals(listOf("coffee" to "request-1", "coffee" to "request-1"), backend.requests)
        assertEquals(400, viewModel.uiState.value.pointsBalance)
    }

    @Test
    fun `a definitive rejection starts a fresh request next time`() {
        val viewModel = viewModel()
        val coffee = viewModel.uiState.value.availableRewards.single()
        backend.failNext = BackendException("This reward is no longer available.", "FAILED_PRECONDITION")

        viewModel.selectReward(coffee)
        viewModel.redeemSelectedReward()
        viewModel.selectReward(coffee)
        viewModel.redeemSelectedReward()

        assertEquals(listOf("request-1", "request-2"), backend.requests.map { it.second })
    }

    @Test
    fun `insufficient balance is reported without calling the backend or changing the balance`() {
        points.stats = UserStats(pointsBalance = 100)
        val viewModel = viewModel()

        viewModel.selectReward(viewModel.uiState.value.availableRewards.single())
        viewModel.redeemSelectedReward()

        assertTrue(backend.requests.isEmpty())
        assertEquals(100, viewModel.uiState.value.pointsBalance)
        assertNull(viewModel.uiState.value.recentlyRedeemedReward)
        assertFalse(viewModel.uiState.value.isRedeeming)
    }
}
