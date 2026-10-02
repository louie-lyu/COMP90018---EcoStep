package com.ecostep.app.data.cache.route

import com.ecostep.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RouteCacheKeyTest {
    private val start = GeoPoint(-37.81364, 144.96314)
    private val end = GeoPoint(-37.79634, 144.96144)

    @Test
    fun `route coordinates are rounded to four decimal places`() {
        val key = RouteCacheKey.from(start, end)

        assertEquals(GeoPoint(-37.8136, 144.9631), key.roundedStart)
        assertEquals(GeoPoint(-37.7963, 144.9614), key.roundedEnd)
        assertEquals(
            "route_-37.8136_144.9631_-37.7963_144.9614",
            key.value,
        )
    }

    @Test
    fun `nearby coordinates that round equally share a key`() {
        val first = RouteCacheKey.from(start, end)
        val second = RouteCacheKey.from(
            GeoPoint(-37.81363, 144.96313),
            GeoPoint(-37.79633, 144.96143),
        )

        assertEquals(first.value, second.value)
    }

    @Test
    fun `reversing a route creates a different key`() {
        assertNotEquals(
            RouteCacheKey.from(start, end).value,
            RouteCacheKey.from(end, start).value,
        )
    }

    @Test
    fun `invalid coordinates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteCacheKey.from(
                GeoPoint(91.0, 144.0),
                end,
            )
        }
    }
}
