package com.ecostep.app.data.cache.weather

import com.ecostep.app.data.model.WeatherData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCachePolicyTest {

    private val policy = WeatherCachePolicy()
    private val currentTimeMillis = 1_800_000_000_000L

    @Test
    fun `cache just under thirty minutes old is fresh`() {
        val entry = entryWithAge(
            WeatherCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS - 1L,
        )

        assertTrue(
            policy.isFresh(
                entry = entry,
                currentTimeMillis = currentTimeMillis,
            ),
        )
    }

    @Test
    fun `cache exactly thirty minutes old requires a refresh`() {
        val entry = entryWithAge(
            WeatherCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS,
        )

        assertFalse(
            policy.isFresh(
                entry = entry,
                currentTimeMillis = currentTimeMillis,
            ),
        )
        assertTrue(
            policy.canUseAsOfflineFallback(
                entry = entry,
                currentTimeMillis = currentTimeMillis,
            ),
        )
    }

    @Test
    fun `cache exactly six hours old can be used offline`() {
        val entry = entryWithAge(
            WeatherCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS,
        )

        assertTrue(
            policy.canUseAsOfflineFallback(
                entry = entry,
                currentTimeMillis = currentTimeMillis,
            ),
        )
    }

    @Test
    fun `cache older than six hours cannot be used offline`() {
        val entry = entryWithAge(
            WeatherCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS + 1L,
        )

        assertFalse(
            policy.canUseAsOfflineFallback(
                entry = entry,
                currentTimeMillis = currentTimeMillis,
            ),
        )
    }

    @Test
    fun `future timestamp is treated as a new cache entry`() {
        val entry = WeatherCacheEntry(
            weatherData = sampleWeather(),
            fetchedAtMillis = currentTimeMillis + 60_000L,
        )

        assertTrue(
            policy.isFresh(
                entry = entry,
                currentTimeMillis = currentTimeMillis,
            ),
        )
    }

    @Test
    fun `offline lifetime cannot be shorter than fresh lifetime`() {
        assertThrows(IllegalArgumentException::class.java) {
            WeatherCachePolicy(
                freshMaxAgeMillis = 30_000L,
                offlineMaxAgeMillis = 29_999L,
            )
        }
    }

    private fun entryWithAge(
        ageMillis: Long,
    ): WeatherCacheEntry {
        return WeatherCacheEntry(
            weatherData = sampleWeather(),
            fetchedAtMillis = currentTimeMillis - ageMillis,
        )
    }

    private fun sampleWeather(): WeatherData {
        return WeatherData(
            temperatureCelsius = 17.4,
            conditions = "Mainly clear",
        )
    }
}
