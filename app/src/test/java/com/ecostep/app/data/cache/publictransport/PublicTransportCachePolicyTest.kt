package com.ecostep.app.data.cache.publictransport

import com.ecostep.app.data.model.PublicTransportInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicTransportCachePolicyTest {
    private val policy = PublicTransportCachePolicy()
    private val now = 1_800_000_000_000L

    @Test
    fun `entry just under two minutes old is fresh`() {
        assertTrue(policy.isFresh(entryWithAge(120_000L - 1L), now))
    }

    @Test
    fun `entry exactly two minutes old requires refresh`() {
        val entry = entryWithAge(PublicTransportCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS)
        assertFalse(policy.isFresh(entry, now))
        assertTrue(policy.canUseAsOfflineFallback(entry, now))
    }

    @Test
    fun `entry exactly fifteen minutes old is available offline`() {
        assertTrue(
            policy.canUseAsOfflineFallback(
                entryWithAge(PublicTransportCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS),
                now,
            ),
        )
    }

    @Test
    fun `entry older than fifteen minutes is unavailable offline`() {
        assertFalse(
            policy.canUseAsOfflineFallback(
                entryWithAge(PublicTransportCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS + 1L),
                now,
            ),
        )
    }

    @Test
    fun `departed options are removed`() {
        val entry = PublicTransportCacheEntry(
            options = listOf(
                option(now - 1L, "Departed"),
                option(now + 60_000L, "Future"),
            ),
            fetchedAtMillis = now - 1_000L,
        )

        assertEquals(
            listOf("Future"),
            policy.futureOptions(entry, now).map { it.line },
        )
    }

    @Test
    fun `future timestamp is rejected`() {
        val entry = PublicTransportCacheEntry(
            options = listOf(option(now + 60_000L, "Future")),
            fetchedAtMillis = now + 1L,
        )
        assertFalse(policy.isFresh(entry, now))
        assertFalse(policy.canUseAsOfflineFallback(entry, now))
    }

    private fun entryWithAge(age: Long) = PublicTransportCacheEntry(
        options = listOf(option(now + 60_000L, "Tram 19")),
        fetchedAtMillis = now - age,
    )

    private fun option(departure: Long, line: String) = PublicTransportInfo(
        line = line,
        departureTimeMillis = departure,
        estimatedDurationSeconds = 1_200L,
    )
}
