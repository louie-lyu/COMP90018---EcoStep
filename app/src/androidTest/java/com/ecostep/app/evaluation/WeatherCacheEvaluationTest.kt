package com.ecostep.app.evaluation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.DefaultExternalDataRepository
import com.ecostep.app.data.repository.ExternalDataException
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoCurrentWeather
import com.ecostep.app.network.weather.OpenMeteoResponse
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android DataStore and repository, with a controlled provider and clock. */
@RunWith(AndroidJUnit4::class)
class WeatherCacheEvaluationTest {
    @Test
    fun evaluatePersistentWeatherCache() = runBlocking {
        val startedAt = System.currentTimeMillis()
        val report = weatherEvaluationReport(
            startedAt,
            "Device integration correctness: real DataStore and repository, controlled provider and clock. " +
                "Offline cases inject IOException; they do not toggle device networking. " +
                "Persistence reopens the store in the same process, not across an app restart.",
        )
        val records = JSONArray()

        suspend fun evaluate(name: String, expected: String, block: suspend (Fixture) -> Unit) {
            val fixture = Fixture()
            val record = JSONObject().apply {
                put("scenario", name)
                put("expected", expected)
                put("passed", false)
            }
            try {
                withTimeout(10000) { block(fixture) }
                record.put("passed", true)
            } catch (error: Throwable) {
                record.put("error", evaluationError(error))
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
            } finally {
                record.put("providerCalls", fixture.api.calls)
                try {
                    fixture.store.close()
                } catch (error: Exception) {
                    record.put("passed", false)
                    record.put("cleanupError", evaluationError(error))
                    throw error
                } finally {
                    records.put(record)
                }
            }
        }

        var completed = false
        try {
            evaluate("fresh_cache", "At 30 min minus 1 ms: cached value, zero provider calls") { fixture ->
                fixture.seed(FRESH_AGE_MS - 1)
                assertEquals(CACHED_WEATHER, fixture.repository().getWeather(LOCATION))
                assertEquals(0, fixture.api.calls)
            }
            evaluate("refresh_at_fresh_boundary", "At 30 min: fetch, replace cache and timestamp") { fixture ->
                fixture.seed(FRESH_AGE_MS)
                assertEquals(NETWORK_WEATHER, fixture.repository().getWeather(LOCATION))
                assertEquals(1, fixture.api.calls)
                assertEquals(WeatherCacheEntry(NETWORK_WEATHER, NOW), fixture.store.cache.get(LOCATION))
            }
            evaluate("offline_stale_fallback", "At 30 min: failed fetch returns cached value without renewing age") { fixture ->
                fixture.seed(FRESH_AGE_MS)
                fixture.offline = true
                assertEquals(CACHED_WEATHER, fixture.repository().getWeather(LOCATION))
                assertEquals(1, fixture.api.calls)
                assertEquals(NOW - FRESH_AGE_MS, fixture.store.cache.get(LOCATION)?.fetchedAtMillis)
            }
            evaluate("offline_at_six_hours", "Exactly 6 hours: failed fetch may still use cached value") { fixture ->
                fixture.seed(OFFLINE_AGE_MS)
                fixture.offline = true
                assertEquals(CACHED_WEATHER, fixture.repository().getWeather(LOCATION))
                assertEquals(1, fixture.api.calls)
            }
            evaluate("offline_after_six_hours", "6 hours plus 1 ms: NETWORK error and expired entry removed") { fixture ->
                fixture.seed(OFFLINE_AGE_MS + 1)
                fixture.offline = true
                fixture.assertNetworkFailure()
                assertNull(fixture.store.cache.get(LOCATION))
            }
            evaluate("offline_without_cache", "Empty cache: NETWORK error, no fabricated weather") { fixture ->
                fixture.offline = true
                fixture.assertNetworkFailure()
                assertNull(fixture.store.cache.get(LOCATION))
            }
            evaluate("online_after_failure", "An earlier offline failure does not prevent a later fetch") { fixture ->
                fixture.offline = true
                fixture.assertNetworkFailure()
                fixture.offline = false
                assertEquals(NETWORK_WEATHER, fixture.repository().getWeather(LOCATION))
                assertEquals(2, fixture.api.calls)
                assertEquals(WeatherCacheEntry(NETWORK_WEATHER, NOW), fixture.store.cache.get(LOCATION))
            }
            evaluate("persistent_readback", "Provider result survives closing and reopening DataStore") { fixture ->
                assertEquals(NETWORK_WEATHER, fixture.repository().getWeather(LOCATION))
                fixture.store.reopen()
                fixture.offline = true
                assertEquals(WeatherCacheEntry(NETWORK_WEATHER, NOW), fixture.store.cache.get(LOCATION))
                assertEquals(NETWORK_WEATHER, fixture.repository().getWeather(LOCATION))
                assertEquals(1, fixture.api.calls)
            }
            evaluate("persistent_removal", "Removed entry stays absent after DataStore reopens") { fixture ->
                fixture.seed(0)
                fixture.store.cache.remove(LOCATION)
                fixture.store.reopen()
                assertNull(fixture.store.cache.get(LOCATION))
                assertEquals(0, fixture.api.calls)
            }
            completed = true
        } catch (error: Throwable) {
            report.put("runError", evaluationError(error))
            throw error
        } finally {
            val passed = (0 until records.length()).count { records.getJSONObject(it).getBoolean("passed") }
            report.put("plannedCases", 9)
            report.put("totalCases", records.length())
            report.put("passedCases", passed)
            report.put("failedCases", records.length() - passed)
            report.put("passed", completed && passed == 9)
            report.put("provider", "Controlled OpenMeteoApi; no live requests")
            report.put("records", records)
            saveWeatherEvaluationReport("weather-cache", startedAt, report)
        }
        assertTrue("Cache evaluation failed; inspect the JSON report", report.getBoolean("passed"))
    }

    private class Fixture {
        val store = EvaluationWeatherCache()
        var offline = false
        val api = CountingWeatherApi(object : OpenMeteoApi {
            override suspend fun getCurrentWeather(
                latitude: Double,
                longitude: Double,
                current: String,
                temperatureUnit: String,
            ): OpenMeteoResponse {
                if (offline) throw IOException("Evaluation provider is offline")
                return OpenMeteoResponse(OpenMeteoCurrentWeather(22.0, 0))
            }
        })

        fun repository() = DefaultExternalDataRepository(
            weatherDataSource = OpenMeteoWeatherDataSource(api),
            weatherCache = store.cache,
            currentTimeMillis = { NOW },
        )

        suspend fun seed(ageMillis: Long) {
            store.cache.save(LOCATION, WeatherCacheEntry(CACHED_WEATHER, NOW - ageMillis))
        }

        suspend fun assertNetworkFailure() {
            val callsBefore = api.calls
            try {
                repository().getWeather(LOCATION)
                throw AssertionError("Expected NETWORK failure")
            } catch (error: ExternalDataException) {
                assertEquals(ExternalDataException.Reason.NETWORK, error.reason)
            }
            assertEquals(callsBefore + 1, api.calls)
        }
    }

    private companion object {
        val LOCATION = GeoPoint(-37.8136, 144.9631)
        val CACHED_WEATHER = WeatherData(17.4, "Mainly clear")
        val NETWORK_WEATHER = WeatherData(22.0, "Clear sky")
        const val NOW = 1_800_000_000_000L
        const val FRESH_AGE_MS = 30 * 60 * 1000L
        const val OFFLINE_AGE_MS = 6 * 60 * 60 * 1000L
    }
}
