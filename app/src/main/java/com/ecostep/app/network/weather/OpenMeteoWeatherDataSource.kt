package com.ecostep.app.network.weather

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.WeatherData

internal class OpenMeteoWeatherDataSource(
    private val openMeteoApi: OpenMeteoApi,
) {

    suspend fun getWeather(location: GeoPoint): WeatherData {
        val response = openMeteoApi.getCurrentWeather(
            latitude = location.latitude,
            longitude = location.longitude,
        )

        return response.toWeatherData()
    }
}
