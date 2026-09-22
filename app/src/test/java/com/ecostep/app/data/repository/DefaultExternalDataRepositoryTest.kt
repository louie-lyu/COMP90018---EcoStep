package com.ecostep.app.data.repository

import com.ecostep.app.data.cache.weather.WeatherCache
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.cache.weather.WeatherCachePolicy
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoCurrentWeather
import com.ecostep.app.network.weather.OpenMeteoResponse
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class DefaultExternalDataRepositoryTest {

    private val location = GeoPoint(
        latitude = -37.8136,
        longitude = 144.9631,
    )

    private val currentTimeMillis = 1_800_000_000_000L

    @Test
    fun `network response is returned and saved using rounded coordinates`() = runTest {
        val api = FakeOpenMeteoApi {
            successfulResponse()
        }
        val cache = FakeWeatherCache()

        val repository = repository(
            api = api,
            cache = cache,
        )

        val weatherData = repository.getWeather(location)

        assertEquals(1, api.requestCount)
        assertEquals(-37.81, requireNotNull(api.lastLatitude), 0.0)
        assertEquals(144.96, requireNotNull(api.lastLongitude), 0.0)
        assertEquals(18.6, weatherData.temperatureCelsius, 0.0)
        assertEquals("Partly cloudy", weatherData.conditions)

        assertEquals(-37.81, requireNotNull(cache.savedLocation).latitude, 0.0)
        assertEquals(144.96, requireNotNull(cache.savedLocation).longitude, 0.0)
        assertEquals(
            currentTimeMillis,
            requireNotNull(cache.entry).fetchedAtMillis,
        )
        assertEquals(
            weatherData,
            requireNotNull(cache.entry).weatherData,
        )
    }

    @Test
    fun `fresh cache is returned without making a network request`() = runTest {
        val cachedWeather = sampleCachedWeather()
        val cache = FakeWeatherCache(
            initialEntry = WeatherCacheEntry(
                weatherData = cachedWeather,
                fetchedAtMillis = currentTimeMillis - 60_000L,
            ),
        )
        val api = FakeOpenMeteoApi {
            error("Network must not be called for fresh cache")
        }

        val repository = repository(
            api = api,
            cache = cache,
        )

        val result = repository.getWeather(location)

        assertEquals(cachedWeather, result)
        assertEquals(0, api.requestCount)
        assertNull(cache.savedLocation)
    }

    @Test
    fun `stale cache is refreshed when network succeeds`() = runTest {
        val oldCachedWeather = sampleCachedWeather()
        val cache = FakeWeatherCache(
            initialEntry = WeatherCacheEntry(
                weatherData = oldCachedWeather,
                fetchedAtMillis = currentTimeMillis -
                        WeatherCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS,
            ),
        )
        val api = FakeOpenMeteoApi {
            successfulResponse()
        }

        val repository = repository(
            api = api,
            cache = cache,
        )

        val result = repository.getWeather(location)

        assertEquals(1, api.requestCount)
        assertEquals(18.6, result.temperatureCelsius, 0.0)
        assertEquals("Partly cloudy", result.conditions)
        assertEquals(
            currentTimeMillis,
            requireNotNull(cache.entry).fetchedAtMillis,
        )
        assertEquals(
            result,
            requireNotNull(cache.entry).weatherData,
        )
    }

    @Test
    fun `network failure returns cache within offline lifetime`() = runTest {
        val cachedWeather = sampleCachedWeather()
        val cache = FakeWeatherCache(
            initialEntry = WeatherCacheEntry(
                weatherData = cachedWeather,
                fetchedAtMillis = currentTimeMillis -
                        WeatherCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS -
                        1L,
            ),
        )
        val networkFailure = IOException("Network unavailable")
        val api = FakeOpenMeteoApi {
            throw networkFailure
        }

        val repository = repository(
            api = api,
            cache = cache,
        )

        val result = repository.getWeather(location)

        assertEquals(cachedWeather, result)
        assertEquals(1, api.requestCount)
        assertNull(cache.removedLocation)
    }

    @Test
    fun `network failure with expired cache throws ExternalDataException`() = runTest {
        val cache = FakeWeatherCache(
            initialEntry = WeatherCacheEntry(
                weatherData = sampleCachedWeather(),
                fetchedAtMillis = currentTimeMillis -
                        WeatherCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS -
                        1L,
            ),
        )
        val networkFailure = IOException("Network unavailable")
        val api = FakeOpenMeteoApi {
            throw networkFailure
        }

        val repository = repository(
            api = api,
            cache = cache,
        )

        val actual = captureExternalDataException {
            repository.getWeather(location)
        }

        assertEquals(
            ExternalDataException.Reason.NETWORK,
            actual.reason,
        )
        assertSame(networkFailure, actual.cause)
        assertEquals(-37.81, requireNotNull(cache.removedLocation).latitude, 0.0)
        assertEquals(144.96, requireNotNull(cache.removedLocation).longitude, 0.0)
        assertNull(cache.entry)
    }

    @Test
    fun `CancellationException is propagated unchanged`() = runTest {
        val cancellation = CancellationException("Request cancelled")
        val api = FakeOpenMeteoApi {
            throw cancellation
        }
        val repository = repository(
            api = api,
            cache = FakeWeatherCache(),
        )

        try {
            repository.getWeather(location)
            throw AssertionError("Expected CancellationException")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    private fun repository(
        api: OpenMeteoApi,
        cache: WeatherCache,
    ): DefaultExternalDataRepository {
        return DefaultExternalDataRepository(
            weatherDataSource = OpenMeteoWeatherDataSource(api),
            weatherCache = cache,
            currentTimeMillis = { currentTimeMillis },
        )
    }

    private fun successfulResponse(): OpenMeteoResponse {
        return OpenMeteoResponse(
            current = OpenMeteoCurrentWeather(
                temperatureCelsius = 18.6,
                weatherCode = 2,
            ),
        )
    }

    private fun sampleCachedWeather(): WeatherData {
        return WeatherData(
            temperatureCelsius = 17.4,
            conditions = "Mainly clear",
        )
    }

    private suspend fun captureExternalDataException(
        block: suspend () -> Unit,
    ): ExternalDataException {
        try {
            block()
        } catch (error: ExternalDataException) {
            return error
        }

        throw AssertionError("Expected ExternalDataException")
    }

    private class FakeOpenMeteoApi(
        private val response: suspend () -> OpenMeteoResponse,
    ) : OpenMeteoApi {

        var requestCount: Int = 0
        var lastLatitude: Double? = null
        var lastLongitude: Double? = null

        override suspend fun getCurrentWeather(
            latitude: Double,
            longitude: Double,
            current: String,
            temperatureUnit: String,
        ): OpenMeteoResponse {
            requestCount += 1
            lastLatitude = latitude
            lastLongitude = longitude
            return response()
        }
    }

    private class FakeWeatherCache(
        initialEntry: WeatherCacheEntry? = null,
    ) : WeatherCache {

        var entry: WeatherCacheEntry? = initialEntry
        var savedLocation: GeoPoint? = null
        var removedLocation: GeoPoint? = null

        override suspend fun get(
            location: GeoPoint,
        ): WeatherCacheEntry? {
            return entry
        }

        override suspend fun save(
            location: GeoPoint,
            entry: WeatherCacheEntry,
        ) {
            savedLocation = location
            this.entry = entry
        }

        override suspend fun remove(
            location: GeoPoint,
        ) {
            removedLocation = location
            entry = null
        }
    }
}
