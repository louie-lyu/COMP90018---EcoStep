package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.LeaderboardPeriod
import com.ecostep.app.data.model.LeaderboardPeriods
import com.ecostep.app.data.model.RedemptionStatus
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreSocialMappersTest {

    private fun utc(year: Int, month: Int, day: Int, hour: Int = 12) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `week IDs follow UTC ISO weeks, including year boundaries`() {
        assertEquals("week-2026-W40", LeaderboardPeriods.weekId(utc(2026, 10, 3)))
        assertEquals("week-2026-W53", LeaderboardPeriods.weekId(utc(2027, 1, 1)))
        assertEquals("week-2025-W01", LeaderboardPeriods.weekId(utc(2024, 12, 30)))
        assertEquals("all_time", LeaderboardPeriods.periodId(LeaderboardPeriod.ALL_TIME, 0L))
    }

    @Test
    fun `ranking orders by carbon, then journeys, then name`() {
        val ranked = rankLeaderboard(
            listOf(
                LeaderboardTotals("b", "Bea", 500.0, 3),
                LeaderboardTotals("a", "Al", 900.0, 1),
                LeaderboardTotals("c", "Cy", 500.0, 5),
            ),
            currentUid = "b",
        )

        assertEquals(listOf("a", "c", "b"), ranked.map { it.uid })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertTrue(ranked[2].isCurrentUser)
    }

    @Test
    fun `stale weekly block of a friend counts as zero this week`() {
        val stats = mapOf(
            "displayName" to "Maya",
            "allTime" to mapOf("carbonSavedGrams" to 9_000.0, "completedJourneys" to 12L),
            "currentWeek" to mapOf("weekId" to "week-2026-W39", "carbonSavedGrams" to 800.0, "completedJourneys" to 2L),
        )

        val thisWeek = stats.toFriendTotals("m", "week-2026-W40")
        val lastWeek = stats.toFriendTotals("m", "week-2026-W39")
        val allTime = stats.toFriendTotals("m", "all_time")

        assertEquals(0.0, thisWeek.carbonSavedGrams, 0.0)
        assertEquals(800.0, lastWeek.carbonSavedGrams, 0.0)
        assertEquals(12, allTime.completedJourneys)
    }

    @Test
    fun `reward without a positive point price is ignored`() {
        assertNull(mapOf("title" to "Free", "pointsRequired" to 0L).toReward("r0"))
        assertNull(mapOf("title" to "Bad", "pointsRequired" to -5L).toReward("r1"))
        val reward = mapOf(
            "title" to "Coffee",
            "merchantName" to "Bean",
            "pointsRequired" to 300L,
            "active" to true,
            "inventory" to 2L,
        ).toReward("r2")!!
        assertTrue(reward.isRedeemableAt(1L))
        assertFalse(reward.copy(inventory = 0).isRedeemableAt(1L))
        assertFalse(reward.copy(validUntilMillis = 1L).isRedeemableAt(1L))
    }

    @Test
    fun `callable redemption response decodes millis fields`() {
        val redemption = mapOf(
            "redemptionId" to "req-1",
            "rewardId" to "r2",
            "rewardTitle" to "Coffee",
            "pointsSpent" to 300,
            "redemptionCode" to "ECO-ABCD-EFGH",
            "status" to "available",
            "redeemedAtMillis" to 1_000L,
            "expiresAtMillis" to 2_000L,
        ).toRewardRedemption()!!

        assertEquals("req-1", redemption.redemptionId)
        assertEquals(300, redemption.pointsSpent)
        assertEquals(RedemptionStatus.AVAILABLE, redemption.status)
        assertEquals(2_000L, redemption.expiresAtMillis)
    }

    @Test
    fun `friend request with an unknown status is dropped`() {
        assertNull(
            mapOf("senderUid" to "a", "receiverUid" to "b", "status" to "maybe").toFriendRequest("r"),
        )
    }

    @Test
    fun `stats decode clamps invalid values`() {
        val stats = mapOf(
            "pointsBalance" to -50L,
            "totalJourneys" to 4L,
            "carbonSavedGrams" to Double.NaN,
        ).toUserStats()

        assertEquals(0, stats.pointsBalance)
        assertEquals(4, stats.totalJourneys)
        assertEquals(0.0, stats.carbonSavedGrams, 0.0)
    }
}
