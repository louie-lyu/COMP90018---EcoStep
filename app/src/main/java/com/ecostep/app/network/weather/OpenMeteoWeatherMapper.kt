package com.ecostep.app.network.weather

import com.ecostep.app.data.model.WeatherData

internal fun OpenMeteoResponse.toWeatherData(): WeatherData {
    return WeatherData(
        temperatureCelsius = current.temperatureCelsius,
        conditions = weatherCodeToCondition(current.weatherCode),
    )
}

private fun weatherCodeToCondition(code: Int): String {
    return when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Unknown weather"
    }
}
