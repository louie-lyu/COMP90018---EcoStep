package com.ecostep.app.evaluation

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.core.di.AppContainer
import com.ecostep.app.data.cache.weather.DataStoreWeatherCache
import com.ecostep.app.data.cache.weather.weatherDataStore
import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.Locale

/** Live network evaluation: run on a connected device with internet access. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class WeatherLatencyEvaluationTest {

    @Test
    fun measureLiveWeatherRequestLatency() = runBlocking {
        val startedAt = Instant.now()
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val repository = AppContainer(context).externalDataRepository
        val location = GeoPoint(
            latitude = -37.8136,
            longitude = 144.9631,
        )

        val weatherCache = DataStoreWeatherCache(
            dataStore = context.weatherDataStore,
        )
        // Make request 1 a real network fetch.
        weatherCache.remove(location)

        val tracker = LatencyTracker(
            context = "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}",
        )
        Log.i(TAG, "Device: ${tracker.context}; requests=$REQUEST_COUNT")

        try {
            repeat(REQUEST_COUNT) { index ->
                // Request 1 fetches Open-Meteo and writes the cache.
                // Later requests should be served by the fresh cache.
                val scenario = if (index == 0) {
                    "weather.network.first_fetch"
                } else {
                    "weather.cache.hit"
                }
                try {
                    tracker.measure(scenario) {
                        withTimeout(REQUEST_TIMEOUT_MS) {
                            repository.getWeather(location)
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    // Already recorded by the tracker; continue collecting samples.
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Keep failures in the results instead of stopping at the first one.
                }

                val record = tracker.snapshot().last()
                Log.i(
                    TAG,
                    "Request ${index + 1}/$REQUEST_COUNT: ${record.scenario} " +
                        "${record.outcome}, ${record.durationMs.formatMs()} ms, " +
                        "error=${record.errorType ?: "none"}",
                )
                // Pacing is outside the measurement and does not affect reported latency.
                if (index < REQUEST_COUNT - 1) delay(REQUEST_INTERVAL_MS)
            }
        } finally {
            for (summary in tracker.summaries()) {
                Log.i(
                    TAG,
                    "Summary: ${summary.scenario} ${summary.outcome}, n=${summary.count}, " +
                        "P50=${summary.p50Ms.formatMs()} ms, P95=${summary.p95Ms.formatMs()} ms",
                )
            }
            val records = tracker.snapshot()
            Log.i(TAG, "Successful requests: ${records.count { it.outcome == Outcome.SUCCESS }}/${records.size}")
            saveJsonReport(tracker, startedAt)
        }

        // No arbitrary speed threshold: this evaluates latency under the current network.
        assertEquals(
            "Some weather requests failed. See Logcat tag $TAG for measurements.",
            REQUEST_COUNT,
            tracker.snapshot().count { it.outcome == Outcome.SUCCESS },
        )
    }

    /** Runs outside measured requests, including when a request fails or is cancelled. */
    private fun saveJsonReport(tracker: LatencyTracker, startedAt: Instant) {
        val records = tracker.snapshot()
        // Request latency includes network waits and parsing, not just connection setup.
        val successfulRecords = records.filter {
            it.outcome == Outcome.SUCCESS
        }

        val networkFirstFetchRecords = records.filter {
            it.scenario == "weather.network.first_fetch" &&
                    it.outcome == Outcome.SUCCESS
        }

        val cacheHitRecords = records.filter {
            it.scenario == "weather.cache.hit" &&
                    it.outcome == Outcome.SUCCESS
        }
        val report = JSONObject().apply {
            put("startedAt", startedAt.toString())
            put("finishedAt", Instant.now().toString())
            put("device", tracker.context)
            put("plannedRequests", REQUEST_COUNT)
            put("recordedRequests", records.size)
            put("successfulRequests", records.count { it.outcome == Outcome.SUCCESS })
            put(
                "averageSuccessfulDurationMs",
                successfulRecords.averageDurationOrNull(),
            )
            put(
                "averageNetworkFirstFetchDurationMs",
                networkFirstFetchRecords.averageDurationOrNull(),
            )
            put(
                "averageCacheHitDurationMs",
                cacheHitRecords.averageDurationOrNull(),
            )
            put("requestTimeoutMs", REQUEST_TIMEOUT_MS)
            put("requestIntervalMs", REQUEST_INTERVAL_MS)
            put("records", JSONArray().apply {
                records.forEachIndexed { index, record ->
                    put(JSONObject().apply {
                        put("requestNumber", index + 1)
                        put("scenario", record.scenario)
                        put("durationMs", record.durationMs)
                        put("outcome", record.outcome.name)
                        put("errorType", record.errorType ?: JSONObject.NULL)
                    })
                }
            })
            put("summaries", JSONArray().apply {
                tracker.summaries().forEach { summary ->
                    put(JSONObject().apply {
                        put("scenario", summary.scenario)
                        put("outcome", summary.outcome.name)
                        put("count", summary.count)
                        put("p50Ms", summary.p50Ms)
                        put("p95Ms", summary.p95Ms)
                    })
                }
            })
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "evaluation")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        val file = File(directory, "weather-latency-${startedAt.toEpochMilli()}.json")
        file.writeText(report.toString(2), Charsets.UTF_8)
        Log.i(TAG, "JSON saved: ${file.absolutePath}")
    }

    private fun Double.formatMs(): String = String.format(Locale.US, "%.2f", this)

    /** No successful samples means no average, represented by JSON null. */
    private fun List<LatencyRecord>.averageDurationOrNull(): Any =
        if (isEmpty()) JSONObject.NULL else map { it.durationMs }.average()

    private companion object {
        const val TAG = "WeatherLatency"
        const val REQUEST_COUNT = 100
        const val REQUEST_TIMEOUT_MS = 30_000L
        const val REQUEST_INTERVAL_MS = 1_000L
    }
}
