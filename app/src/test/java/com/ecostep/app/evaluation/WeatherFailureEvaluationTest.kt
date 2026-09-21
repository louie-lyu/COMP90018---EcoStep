package com.ecostep.app.evaluation

import com.ecostep.app.data.cache.weather.WeatherCache
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.repository.DefaultExternalDataRepository
import com.ecostep.app.data.repository.ExternalDataException
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoResponse
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * Real repository and data source with controlled provider failures.
 *
 * Provider-specific failures are converted to ExternalDataException, while
 * coroutine cancellation remains unchanged.
 */
class WeatherFailureEvaluationTest {

    private val location = GeoPoint(-37.8136, 144.9631)

    @Test
    fun `network failure becomes NETWORK ExternalDataException`() = runTest {
        assertApiFailure(
            expectedCause = IOException("Network unavailable"),
            expectedReason = ExternalDataException.Reason.NETWORK,
        )
    }

    @Test
    fun `socket timeout becomes NETWORK ExternalDataException`() = runTest {
        assertApiFailure(
            expectedCause = SocketTimeoutException("Read timed out"),
            expectedReason = ExternalDataException.Reason.NETWORK,
        )
    }

    @Test
    fun `HTTP failure becomes SERVICE ExternalDataException`() = runTest {
        val response = Response.error<OpenMeteoResponse>(
            503,
            "Service unavailable".toResponseBody(),
        )

        assertApiFailure(
            expectedCause = HttpException(response),
            expectedReason = ExternalDataException.Reason.SERVICE,
        )
    }

    @Test
    fun `serialization failure becomes INVALID_RESPONSE ExternalDataException`() = runTest {
        assertApiFailure(
            expectedCause = SerializationException("Malformed weather response"),
            expectedReason = ExternalDataException.Reason.INVALID_RESPONSE,
        )
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `coroutine timeout cancels the API and records timeout`() = runTest {
        val tracker = LatencyTracker(
            nanoTime = { testScheduler.currentTime * 1_000_000 },
        )
        var apiCancelled = false
        val repository = repository {
            try {
                awaitCancellation()
            } finally {
                apiCancelled = true
            }
        }

        try {
            tracker.measure("weather.fetch") {
                withTimeout(100) {
                    repository.getWeather(location)
                }
            }
            fail("Expected coroutine timeout")
        } catch (_: TimeoutCancellationException) {
            // Timeout cancellation must propagate unchanged.
        }

        assertTrue(apiCancelled)
        assertEquals(
            LatencyRecord(
                scenario = "weather.fetch",
                durationMs = 100.0,
                outcome = Outcome.TIMEOUT,
                errorType = "TimeoutCancellationException",
            ),
            tracker.snapshot().single(),
        )
    }

    @Test
    fun `caller cancellation stops the API and records cancellation`() = runTest {
        val tracker = LatencyTracker(nanoTime = { 0L })
        val cancellation = CancellationException("Evaluation cancelled")
        var apiCancelled = false
        var propagated: CancellationException? = null
        val repository = repository {
            try {
                awaitCancellation()
            } finally {
                apiCancelled = true
            }
        }

        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                tracker.measure("weather.fetch") {
                    repository.getWeather(location)
                }
            } catch (error: CancellationException) {
                propagated = error
                throw error
            }
        }

        request.cancel(cancellation)
        request.join()

        assertTrue(request.isCancelled)
        assertTrue(apiCancelled)
        assertEquals(cancellation.message, propagated?.message)
        assertEquals(
            LatencyRecord(
                scenario = "weather.fetch",
                durationMs = 0.0,
                outcome = Outcome.CANCELLED,
                errorType = "CancellationException",
            ),
            tracker.snapshot().single(),
        )
    }

    private suspend fun assertApiFailure(
        expectedCause: Exception,
        expectedReason: ExternalDataException.Reason,
    ) {
        var now = 0L
        val tracker = LatencyTracker(nanoTime = { now })
        val repository = repository {
            now = 25_000_000L
            throw expectedCause
        }

        try {
            tracker.measure("weather.fetch") {
                repository.getWeather(location)
            }
            fail("Expected API failure")
        } catch (actual: ExternalDataException) {
            assertEquals(expectedReason, actual.reason)
            assertSame(expectedCause, actual.cause)
        }

        assertEquals(
            LatencyRecord(
                scenario = "weather.fetch",
                durationMs = 25.0,
                outcome = Outcome.FAILURE,
                errorType = "ExternalDataException",
            ),
            tracker.snapshot().single(),
        )
    }

    private fun repository(
        response: suspend () -> OpenMeteoResponse,
    ): DefaultExternalDataRepository {
        val api = object : OpenMeteoApi {

            override suspend fun getCurrentWeather(
                latitude: Double,
                longitude: Double,
                current: String,
                temperatureUnit: String,
            ): OpenMeteoResponse {
                return response()
            }
        }

        return DefaultExternalDataRepository(
            weatherDataSource = OpenMeteoWeatherDataSource(api),
            weatherCache = EmptyWeatherCache,
        )
    }

    private object EmptyWeatherCache : WeatherCache {

        override suspend fun get(
            location: GeoPoint,
        ): WeatherCacheEntry? {
            return null
        }

        override suspend fun save(
            location: GeoPoint,
            entry: WeatherCacheEntry,
        ) {
            // Evaluation failures do not produce a value to cache.
        }

        override suspend fun remove(
            location: GeoPoint,
        ) {
            // There is no cached entry to remove.
        }
    }
}
