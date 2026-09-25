package com.ecostep.app.network.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoResponse(
    val current: OpenMeteoCurrentWeather,
)

@Serializable
data class OpenMeteoCurrentWeather(
    @SerialName("temperature_2m")
    val temperatureCelsius: Double,

    @SerialName("weather_code")
    val weatherCode: Int,
)
