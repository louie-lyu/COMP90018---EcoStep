package com.ecostep.app.data.repository

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.WeatherData

/**
 * Owner: Jianing Xia (Weather, Maps and Public Transport APIs module).
 * Due: Route & Weather by 27 Sep, per docs/WORK_PLAN.md.
 *
 * Which weather/maps/public-transport services back this interface is Jianing's decision;
 * other modules only depend on this contract. See docs/DEPENDENCIES.md "Using Mock Data" for
 * the mock response shape to use before this is ready.
 */
interface ExternalDataRepository {
    suspend fun getWeather(location: GeoPoint): WeatherData

    suspend fun getRouteOptions(start: GeoPoint, end: GeoPoint): List<RouteInfo>

    suspend fun getPublicTransportOptions(start: GeoPoint, end: GeoPoint): List<PublicTransportInfo>
}
