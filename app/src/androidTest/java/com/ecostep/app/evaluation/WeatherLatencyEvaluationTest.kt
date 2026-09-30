package com.ecostep.app.evaluation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.core.di.AppContainer
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.repository.DefaultExternalDataRepository
import com.ecostep.app.network.weather.OpenMeteoClient
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Live provider timings and verified disk-cache timings, collected in separate groups. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class WeatherLatencyEvaluationTest {
    @Test
    fun measureLiveWeatherRequestLatency() = runBlocking {
        val startedAt = System.currentTimeMillis()
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String, default: Int, range: IntRange): Int {
            val value = arguments.getString(name)?.toInt() ?: default
            require(value in range) { "$name must be in $range" }
            return value
        }
        val networkSamples = argument("networkSamples", 20, 1..500)
        val cacheSamples = argument("cacheSamples", 100, 1..1000)
        val intervalMs = argument("intervalMs", 1000, 0..60000).toLong()
        val timeoutMs = argument("timeoutMs", 30000, 1..120000).toLong()
        val report = weatherEvaluationReport(
            startedAt,
            "Sequential repository latency on a debug build; network includes parsing and disk writes. " +
                "Cache removal, warm-up and pacing are excluded from sample percentiles. " +
                "HTTP connections may be reused; this is not a cold-connection benchmark.",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val api = CountingWeatherApi(OpenMeteoClient.create(AppContainer(context).okHttpClient))
        val store = EvaluationWeatherCache()
        val repository = DefaultExternalDataRepository(OpenMeteoWeatherDataSource(api), store.cache)
        val location = GeoPoint(-37.8136, 144.9631)
        val tracker = LatencyTracker()
        val records = JSONArray()
        var warmupPassed = false
        var cacheSkipReason: String? = "Network group did not complete"
        var completed = false

        suspend fun sample(scenario: String, expectedCalls: Int): Boolean {
            val callsBefore = api.calls
            var failure: Throwable? = null
            try {
                tracker.measure(scenario) {
                    withTimeout(timeoutMs) {
                        val weather = repository.getWeather(location)
                        check(weather.conditions.isNotBlank()) { "Empty weather conditions" }
                        check(api.calls - callsBefore == expectedCalls) { "Unexpected weather source" }
                    }
                }
            } catch (error: Exception) {
                failure = error
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
            } finally {
                val record = tracker.snapshot().last()
                records.put(JSONObject().apply {
                    put("scenario", scenario)
                    put("durationMs", record.durationMs)
                    put("outcome", record.outcome.name)
                    put("expectedProviderCalls", expectedCalls)
                    put("providerCalls", api.calls - callsBefore)
                    put("error", failure?.let(::evaluationError) ?: JSONObject.NULL)
                    put("timedOut", failure is TimeoutCancellationException ||
                        failure is SocketTimeoutException || failure?.cause is SocketTimeoutException)
                })
            }
            return failure == null
        }

        try {
            repeat(networkSamples) { index ->
                withTimeout(timeoutMs) { store.cache.remove(location) }
                sample(NETWORK, expectedCalls = 1)
                if (index < networkSamples - 1) delay(intervalMs)
            }

            // A separate successful warm-up is required even if every network sample failed.
            cacheSkipReason = "Warm-up did not complete"
            withTimeout(timeoutMs) { store.cache.remove(location) }
            delay(intervalMs)
            warmupPassed = sample(WARMUP, expectedCalls = 1)
            cacheSkipReason = "Warm-up failed"
            if (warmupPassed) {
                cacheSkipReason = "Warm-up did not persist weather"
                check(withTimeout(timeoutMs) { store.cache.get(location) } != null) {
                    "Warm-up did not persist weather"
                }
                cacheSkipReason = "Cache group did not complete"
                repeat(cacheSamples) { sample(CACHE, expectedCalls = 0) }
                cacheSkipReason = null
            }
            completed = true
        } catch (error: Throwable) {
            report.put("runError", evaluationError(error))
            throw error
        } finally {
            val summaries = JSONArray()
            for ((scenario, planned) in listOf(NETWORK to networkSamples, CACHE to cacheSamples)) {
                val samples = tracker.snapshot().filter { it.scenario == scenario }
                val successful = tracker.summaries().find { it.scenario == scenario && it.outcome == Outcome.SUCCESS }
                val scenarioRecords = (0 until records.length()).map { records.getJSONObject(it) }
                    .filter { it.getString("scenario") == scenario }
                summaries.put(JSONObject().apply {
                    put("scenario", scenario)
                    put("plannedSamples", planned)
                    put("recordedSamples", samples.size)
                    put("skippedSamples", planned - samples.size)
                    put("successfulSamples", successful?.count ?: 0)
                    put("failedSamples", samples.count { it.outcome != Outcome.SUCCESS })
                    put("timeoutSamples", scenarioRecords.count { it.getBoolean("timedOut") })
                    put("successRate", if (samples.isEmpty()) JSONObject.NULL else
                        samples.count { it.outcome == Outcome.SUCCESS }.toDouble() / samples.size)
                    put("p50Ms", successful?.p50Ms ?: JSONObject.NULL)
                    put("p95Ms", successful?.p95Ms ?: JSONObject.NULL)
                    put("skipReason", if (scenario == CACHE) cacheSkipReason ?: JSONObject.NULL else
                        if (samples.size < planned) "Network group did not complete" else JSONObject.NULL)
                })
            }
            report.put("passed", completed && warmupPassed &&
                tracker.snapshot().count { it.outcome == Outcome.SUCCESS } == networkSamples + cacheSamples + 1)
            report.put("requestTimeoutMs", timeoutMs)
            report.put("networkIntervalMs", intervalMs)
            report.put("percentileMethod", "Nearest rank, successful samples only; warm-up excluded")
            report.put("warmupPassed", warmupPassed)
            report.put("records", records)
            report.put("summaries", summaries)
            try {
                store.close()
            } catch (error: Exception) {
                report.put("passed", false)
                report.put("cleanupError", evaluationError(error))
                throw error
            } finally {
                saveWeatherEvaluationReport("weather-latency", startedAt, report)
            }
        }
        assertTrue("Weather evaluation failed; inspect the JSON report", report.getBoolean("passed"))
    }

    private companion object {
        const val NETWORK = "weather.network.fetch"
        const val CACHE = "weather.cache.hit"
        const val WARMUP = "weather.cache.warmup"
    }
}
