package com.ecostep.app.data.cache.weather

import com.ecostep.app.data.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WeatherLocationKeyTest {

    @Test
    fun `Melbourne coordinates are rounded to two decimal places`() {
        val key = WeatherLocationKey.from(
            GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
        )

        assertEquals(-37.81, key.roundedLatitude, 0.0)
        assertEquals(144.96, key.roundedLongitude, 0.0)
        assertEquals("weather_-37.81_144.96", key.value)
        assertEquals(
            GeoPoint(
                latitude = -37.81,
                longitude = 144.96,
            ),
            key.roundedLocation,
        )
    }

    @Test
    fun `nearby coordinates that round equally share one cache key`() {
        val first = WeatherLocationKey.from(
            GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
        )

        val second = WeatherLocationKey.from(
            GeoPoint(
                latitude = -37.8144,
                longitude = 144.9644,
            ),
        )

        assertEquals(first.value, second.value)
        assertEquals(first.roundedLocation, second.roundedLocation)
    }

    @Test
    fun `coordinates in a different rounded area use a different key`() {
        val first = WeatherLocationKey.from(
            GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
        )

        val second = WeatherLocationKey.from(
            GeoPoint(
                latitude = -37.816,
                longitude = 144.966,
            ),
        )

        assertNotEquals(first.value, second.value)
    }

    @Test
    fun `invalid latitude is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeatherLocationKey.from(
                GeoPoint(
                    latitude = 91.0,
                    longitude = 144.9631,
                ),
            )
        }
    }

    @Test
    fun `invalid longitude is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeatherLocationKey.from(
                GeoPoint(
                    latitude = -37.8136,
                    longitude = 181.0,
                ),
            )
        }
    }
}