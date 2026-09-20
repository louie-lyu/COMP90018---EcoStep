package com.ecostep.app.network.weather

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `Open-Meteo current weather response parses correctly`() {
        val sampleJson = """
            {
              "latitude": -37.8,
              "longitude": 144.9,
              "generationtime_ms": 0.04,
              "utc_offset_seconds": 0,
              "timezone": "GMT",
              "current_units": {
                "temperature_2m": "°C",
                "weather_code": "wmo code"
              },
              "current": {
                "time": "2026-09-17T10:00",
                "interval": 900,
                "temperature_2m": 18.6,
                "weather_code": 2
              }
            }
        """.trimIndent()

        val response = json.decodeFromString(
            OpenMeteoResponse.serializer(),
            sampleJson,
        )

        assertEquals(18.6, response.current.temperatureCelsius, 0.0)
        assertEquals(2, response.current.weatherCode)
    }
}
