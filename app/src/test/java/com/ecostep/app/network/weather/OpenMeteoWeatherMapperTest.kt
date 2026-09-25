package com.ecostep.app.network.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoWeatherMapperTest {

    @Test
    fun `response maps to shared WeatherData`() {
        val response = OpenMeteoResponse(
            current = OpenMeteoCurrentWeather(
                temperatureCelsius = 18.6,
                weatherCode = 2,
            ),
        )

        val weatherData = response.toWeatherData()

        assertEquals(18.6, weatherData.temperatureCelsius, 0.0)
        assertEquals("Partly cloudy", weatherData.conditions)
    }

    @Test
    fun `unknown weather code maps to safe fallback`() {
        val response = OpenMeteoResponse(
            current = OpenMeteoCurrentWeather(
                temperatureCelsius = 20.0,
                weatherCode = -1,
            ),
        )

        val weatherData = response.toWeatherData()

        assertEquals("Unknown weather", weatherData.conditions)
    }
}
