package com.ecostep.app.data.cache.route

import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCachePolicyTest {
    private val policy = RouteCachePolicy()
    private val now = 1_800_000_000_000L

    @Test
    fun `entry just under thirty minutes old is fresh`() {
        assertTrue(policy.isFresh(entryWithAge(30L * 60L * 1_000L - 1L), now))
    }

    @Test
    fun `entry exactly thirty minutes old requires refresh`() {
        val entry = entryWithAge(RouteCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS)
        assertFalse(policy.isFresh(entry, now))
        assertTrue(policy.canUseAsOfflineFallback(entry, now))
    }

    @Test
    fun `entry exactly twenty four hours old is available offline`() {
        assertTrue(
            policy.canUseAsOfflineFallback(
                entryWithAge(RouteCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS),
                now,
            ),
        )
    }

    @Test
    fun `entry older than twenty four hours is unavailable offline`() {
        assertFalse(
            policy.canUseAsOfflineFallback(
                entryWithAge(RouteCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS + 1L),
                now,
            ),
        )
    }

    @Test
    fun `future timestamp is rejected`() {
        val entry = entryWithFetchedAt(now + 1L)
        assertFalse(policy.isFresh(entry, now))
        assertFalse(policy.canUseAsOfflineFallback(entry, now))
    }

    @Test
    fun `offline lifetime cannot be shorter than fresh lifetime`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteCachePolicy(30_000L, 29_999L)
        }
    }

    private fun entryWithAge(age: Long) = entryWithFetchedAt(now - age)

    private fun entryWithFetchedAt(fetchedAt: Long) = RouteCacheEntry(
        routes = listOf(
            RouteInfo(TransportMode.WALKING, 1_000.0, 600L),
        ),
        fetchedAtMillis = fetchedAt,
    )
}
