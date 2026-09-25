package com.ecostep.app.network.weather

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.core.di.AppContainer
import com.ecostep.app.data.cache.weather.DataStoreWeatherCache
import com.ecostep.app.data.cache.weather.weatherDataStore
import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class OpenMeteoIntegrationTest {

    @Test
    fun liveOpenMeteoRequestReturnsMelbourneWeather() = runBlocking {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val location = GeoPoint(
            latitude = -37.8136,
            longitude = 144.9631,
        )

        val weatherCache = DataStoreWeatherCache(
            dataStore = context.weatherDataStore,
        )
        // Remove an earlier result so this integration test must use Open-Meteo.
        weatherCache.remove(location)

        val repository = AppContainer(context).externalDataRepository
        val weatherData = repository.getWeather(location)

        Log.i(
            "OpenMeteoIntegration",
            "Melbourne weather: " +
                    "${weatherData.temperatureCelsius}°C, " +
                    weatherData.conditions,
        )

        assertTrue(
            "Temperature should be within a realistic range",
            weatherData.temperatureCelsius in -50.0..60.0,
        )
        assertTrue(
            "Weather condition should not be blank",
            weatherData.conditions.isNotBlank(),
        )
    }
}
