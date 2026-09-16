package com.ecostep.app.network.weather

import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoWeatherDataSourceTest {

    @Test
    fun `getWeather passes coordinates and returns shared WeatherData`() = runTest {
        val fakeApi = FakeOpenMeteoApi(
            response = OpenMeteoResponse(
                current = OpenMeteoCurrentWeather(
                    temperatureCelsius = 18.6,
                    weatherCode = 2,
                ),
            ),
        )

        val dataSource = OpenMeteoWeatherDataSource(fakeApi)

        val weatherData = dataSource.getWeather(
            location = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
        )

        assertEquals(-37.8136, fakeApi.requestedLatitude, 0.0)
        assertEquals(144.9631, fakeApi.requestedLongitude, 0.0)
        assertEquals("temperature_2m,weather_code", fakeApi.requestedCurrent)
        assertEquals("celsius", fakeApi.requestedTemperatureUnit)
        assertEquals(18.6, weatherData.temperatureCelsius, 0.0)
        assertEquals("Partly cloudy", weatherData.conditions)
    }

    private class FakeOpenMeteoApi(
        private val response: OpenMeteoResponse,
    ) : OpenMeteoApi {

        var requestedLatitude: Double = Double.NaN
        var requestedLongitude: Double = Double.NaN
        var requestedCurrent: String = ""
        var requestedTemperatureUnit: String = ""

        override suspend fun getCurrentWeather(
            latitude: Double,
            longitude: Double,
            current: String,
            temperatureUnit: String,
        ): OpenMeteoResponse {
            requestedLatitude = latitude
            requestedLongitude = longitude
            requestedCurrent = current
            requestedTemperatureUnit = temperatureUnit

            return response
        }
    }
}
