package com.ecostep.app.data.repository

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource

internal class DefaultExternalDataRepository(
    private val weatherDataSource: OpenMeteoWeatherDataSource,
) : ExternalDataRepository {

    override suspend fun getWeather(location: GeoPoint): WeatherData {
        return weatherDataSource.getWeather(location)
    }

    override suspend fun getRouteOptions(
        start: GeoPoint,
        end: GeoPoint,
    ): List<RouteInfo> {
        throw UnsupportedOperationException(
            "Route API is not configured yet",
        )
    }

    override suspend fun getPublicTransportOptions(
        start: GeoPoint,
        end: GeoPoint,
    ): List<PublicTransportInfo> {
        throw UnsupportedOperationException(
            "Public transport API is not configured yet",
        )
    }
}
