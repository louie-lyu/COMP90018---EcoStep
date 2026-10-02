package com.ecostep.app.data.cache.publictransport

import com.ecostep.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PublicTransportCacheKeyTest {
    private val start = GeoPoint(-37.81834, 144.96714)
    private val end = GeoPoint(-37.76974, 144.96114)

    @Test
    fun `coordinates are rounded to four decimal places`() {
        val key = PublicTransportCacheKey.from(start, end)
        assertEquals(GeoPoint(-37.8183, 144.9671), key.roundedStart)
        assertEquals(GeoPoint(-37.7697, 144.9611), key.roundedEnd)
        assertEquals(
            "public_transport_-37.8183_144.9671_-37.7697_144.9611",
            key.value,
        )
    }

    @Test
    fun `nearby coordinates that round equally share a key`() {
        val first = PublicTransportCacheKey.from(start, end)
        val second = PublicTransportCacheKey.from(
            GeoPoint(-37.81833, 144.96713),
            GeoPoint(-37.76973, 144.96113),
        )
        assertEquals(first.value, second.value)
    }

    @Test
    fun `reversing journey creates a different key`() {
        assertNotEquals(
            PublicTransportCacheKey.from(start, end).value,
            PublicTransportCacheKey.from(end, start).value,
        )
    }

    @Test
    fun `invalid coordinates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicTransportCacheKey.from(
                start,
                GeoPoint(-37.0, 181.0),
            )
        }
    }
}
