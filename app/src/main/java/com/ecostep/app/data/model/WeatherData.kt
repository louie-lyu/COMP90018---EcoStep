package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/** Produced by the External API module (Jianing). Part of [MissionContext]. */
@Serializable
data class WeatherData(
    val temperatureCelsius: Double,
    val conditions: String,
)
