package com.ecostep.app.data.repository

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoCurrentWeather
import com.ecostep.app.network.weather.OpenMeteoResponse
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultExternalDataRepositoryTest {

    @Test
    fun `getWeather returns shared WeatherData through repository contract`() = runTest {
        val fakeApi = FakeOpenMeteoApi()

        val repository: ExternalDataRepository = DefaultExternalDataRepository(
            weatherDataSource = OpenMeteoWeatherDataSource(fakeApi),
        )

        val weatherData = repository.getWeather(
            location = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
        )

        assertEquals(1, fakeApi.requestCount)
        assertEquals(18.6, weatherData.temperatureCelsius, 0.0)
        assertEquals("Partly cloudy", weatherData.conditions)
    }

    private class FakeOpenMeteoApi : OpenMeteoApi {

        var requestCount: Int = 0

        override suspend fun getCurrentWeather(
            latitude: Double,
            longitude: Double,
            current: String,
            temperatureUnit: String,
        ): OpenMeteoResponse {
            requestCount += 1

            return OpenMeteoResponse(
                current = OpenMeteoCurrentWeather(
                    temperatureCelsius = 18.6,
                    weatherCode = 2,
                ),
            )
        }
    }
}
