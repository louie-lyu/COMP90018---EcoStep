package com.ecostep.app.evaluation.new.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ecostep.app.evaluation.WeatherCacheEvaluationTest
import com.ecostep.app.evaluation.WeatherLatencyEvaluationTest
import com.ecostep.app.evaluation.WeatherNetworkRecoveryTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeatherLatencyDeviceTest {
    @Test fun measureNetworkAndCache() = exportWeatherReport("weather-latency") {
        WeatherLatencyEvaluationTest().measureLiveWeatherRequestLatency()
    }
}

@RunWith(AndroidJUnit4::class)
class WeatherCacheDeviceTest {
    @Test fun verifyPersistentCache() = exportWeatherReport("weather-cache") {
        WeatherCacheEvaluationTest().evaluatePersistentWeatherCache()
    }
}

@RunWith(AndroidJUnit4::class)
class WeatherRecoveryDeviceTest {
    @Test fun verifyNetworkRecovery() = exportWeatherReport("weather-recovery") {
        WeatherNetworkRecoveryTest().weatherRequestUsesCacheOfflineAndRefreshesAfterRecovery()
    }
}
