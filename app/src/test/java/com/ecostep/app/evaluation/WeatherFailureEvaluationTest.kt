package com.ecostep.app.evaluation

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.repository.DefaultExternalDataRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Real repository and data source, with controlled API failures; no network or device needed. */
class WeatherFailureEvaluationTest {
    private val location = GeoPoint(-37.8136, 144.9631)

    @Test
    fun `network failure reaches caller and is recorded once`() = runTest {
        assertApiFailure(IOException("Network unavailable"))
    }

    @Test
    fun `socket timeout remains a failure with its original exception type`() = runTest {
        // LatencyTracker reserves TIMEOUT for coroutine timeouts.
        assertApiFailure(SocketTimeoutException("Read timed out"))
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `coroutine timeout cancels the API and records timeout`() = runTest {
        val tracker = LatencyTracker(nanoTime = { testScheduler.currentTime * 1_000_000 })
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
                withTimeout(100) { repository.getWeather(location) }
            }
            fail("Expected coroutine timeout")
        } catch (_: TimeoutCancellationException) {
            // The timeout must propagate through both the repository and tracker.
        }

        assertTrue(apiCancelled)
        assertEquals(
            LatencyRecord("weather.fetch", 100.0, Outcome.TIMEOUT, "TimeoutCancellationException"),
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

        // Start immediately so cancellation happens while the API is suspended.
        val request = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                tracker.measure("weather.fetch") { repository.getWeather(location) }
            } catch (error: CancellationException) {
                propagated = error
                throw error
            }
        }
        request.cancel(cancellation)
        request.join()

        assertTrue(request.isCancelled)
        assertTrue(apiCancelled)
        // Coroutine stack-trace recovery may copy the cancellation exception.
        assertEquals(cancellation.message, propagated?.message)
        assertEquals(
            LatencyRecord("weather.fetch", 0.0, Outcome.CANCELLED, "CancellationException"),
            tracker.snapshot().single(),
        )
    }

    private suspend fun assertApiFailure(expected: Exception) {
        var now = 0L
        val tracker = LatencyTracker(nanoTime = { now })
        val repository = repository {
            now = 25_000_000L
            throw expected
        }

        try {
            tracker.measure("weather.fetch") { repository.getWeather(location) }
            fail("Expected API failure")
        } catch (actual: Exception) {
            assertSame(expected, actual)
        }

        assertEquals(
            LatencyRecord("weather.fetch", 25.0, Outcome.FAILURE, expected.javaClass.simpleName),
            tracker.snapshot().single(),
        )
    }

    private fun repository(response: suspend () -> OpenMeteoResponse): DefaultExternalDataRepository {
        val api = object : OpenMeteoApi {
            override suspend fun getCurrentWeather(
                latitude: Double,
                longitude: Double,
                current: String,
                temperatureUnit: String,
            ): OpenMeteoResponse = response()
        }
        return DefaultExternalDataRepository(OpenMeteoWeatherDataSource(api))
    }
}
