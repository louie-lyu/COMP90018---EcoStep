package com.ecostep.app.evaluation

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.core.di.AppContainer
import com.ecostep.app.data.cache.weather.DataStoreWeatherCache
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.cache.weather.WeatherCachePolicy
import com.ecostep.app.data.cache.weather.weatherDataStore
import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Changes emulator networking only; never run concurrently with other network tests. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class WeatherNetworkRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Test
    fun weatherRequestUsesCacheOfflineAndRefreshesAfterRecovery() = runBlocking {
        assumeTrue(
            "This test is emulator-only",
            shell("getprop ro.kernel.qemu") == "1",
        )

        val wifiEnabled = readSwitch("wifi_on")
        val dataEnabled = readSwitch("mobile_data")
        val repository = AppContainer(context).externalDataRepository
        val weatherCache = DataStoreWeatherCache(
            dataStore = context.weatherDataStore,
        )
        val location = GeoPoint(-37.8136, 144.9631)
        val tracker = LatencyTracker()
        val startedAt = System.currentTimeMillis()
        var passed = false

        // Ensure the first request really comes from the network.
        weatherCache.remove(location)

        try {
            waitForNetwork(online = true)

            val firstWeather = tracker.measure(
                "weather.network.first_fetch",
            ) {
                withTimeout(30_000) {
                    repository.getWeather(location)
                }
            }

            assertTrue(
                "First network request must return weather",
                firstWeather.conditions.isNotBlank(),
            )

            setSwitch("wifi", false)
            setSwitch("data", false)
            waitForNetwork(online = false)

            val offlineWeather = tracker.measure(
                "weather.cache.hit",
            ) {
                withTimeout(30_000) {
                    repository.getWeather(location)
                }
            }

            assertEquals(
                "Offline request must return the cached weather",
                firstWeather,
                offlineWeather,
            )

            Log.i(
                TAG,
                "Offline request returned cached weather successfully",
            )

            restoreSwitches(
                wifi = wifiEnabled,
                data = dataEnabled,
            )
            waitForNetwork(online = true)

            // Make the saved entry exactly 30 minutes old so the next request
            // must refresh it from the network.
            weatherCache.save(
                location = location,
                entry = WeatherCacheEntry(
                    weatherData = offlineWeather,
                    fetchedAtMillis = System.currentTimeMillis() -
                            WeatherCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS,
                ),
            )

            val refreshedWeather = tracker.measure(
                "weather.network.refresh",
            ) {
                withTimeout(30_000) {
                    repository.getWeather(location)
                }
            }

            assertTrue(
                "Expired cache must refresh after network recovery",
                refreshedWeather.conditions.isNotBlank(),
            )

            passed = true
        } finally {
            // Always restore emulator networking, including after an assertion
            // failure or coroutine cancellation.
            withContext(NonCancellable) {
                try {
                    restoreSwitches(
                        wifi = wifiEnabled,
                        data = dataEnabled,
                    )
                } finally {
                    saveReport(
                        tracker = tracker,
                        startedAt = startedAt,
                        passed = passed,
                    )
                }
            }
        }
    }

    private suspend fun waitForNetwork(online: Boolean) {
        withTimeout(30_000) {
            while (true) {
                val network = connectivity.activeNetwork
                val validated = connectivity.getNetworkCapabilities(network)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                if (if (online) validated else network == null) return@withTimeout
                delay(250)
            }
        }
        Log.i(TAG, if (online) "Network is online" else "Network is offline")
    }

    private fun readSwitch(name: String): Boolean {
        val value = shell("settings get global $name")
        check(value == "0" || value == "1") { "Cannot determine initial $name state: $value" }
        return value == "1"
    }

    private fun setSwitch(service: String, enabled: Boolean) {
        shell("svc $service ${if (enabled) "enable" else "disable"}")
    }

    private fun restoreSwitches(wifi: Boolean, data: Boolean) {
        try {
            setSwitch("wifi", wifi)
        } finally {
            setSwitch("data", data)
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText().trim() }

    private fun saveReport(tracker: LatencyTracker, startedAt: Long, passed: Boolean) {
        val report = JSONObject().apply {
            put("startedAtMillis", startedAt)
            put("passed", passed)
            put("records", JSONArray().apply {
                tracker.snapshot().forEach { record ->
                    put(JSONObject().apply {
                        put("scenario", record.scenario)
                        put("durationMs", record.durationMs)
                        put("outcome", record.outcome.name)
                        put("errorType", record.errorType ?: JSONObject.NULL)
                    })
                }
            })
        }
        val directory = File(context.filesDir, "evaluation")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        val file = File(directory, "weather-recovery-$startedAt.json")
        file.writeText(report.toString(2), Charsets.UTF_8)
        Log.i(TAG, "JSON saved: ${file.absolutePath}")
    }

    private companion object {
        const val TAG = "WeatherRecovery"
    }
}
