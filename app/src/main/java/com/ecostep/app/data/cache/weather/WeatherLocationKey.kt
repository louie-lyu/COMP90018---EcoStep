package com.ecostep.app.data.cache.weather

import com.ecostep.app.data.model.GeoPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * A privacy-minimised location identifier for weather requests and caching.
 *
 * Weather does not need route-level location precision, so coordinates are
 * rounded to two decimal places before being sent to Open-Meteo or used as a
 * persistent cache key.
 */
internal class WeatherLocationKey private constructor(
    val roundedLatitude: Double,
    val roundedLongitude: Double,
) {

    val value: String = String.format(
        Locale.US,
        "weather_%.2f_%.2f",
        roundedLatitude,
        roundedLongitude,
    )

    val roundedLocation: GeoPoint = GeoPoint(
        latitude = roundedLatitude,
        longitude = roundedLongitude,
    )

    companion object {

        fun from(location: GeoPoint): WeatherLocationKey {
            require(
                location.latitude.isFinite() &&
                        location.latitude in -90.0..90.0,
            ) {
                "Latitude must be finite and between -90 and 90"
            }

            require(
                location.longitude.isFinite() &&
                        location.longitude in -180.0..180.0,
            ) {
                "Longitude must be finite and between -180 and 180"
            }

            return WeatherLocationKey(
                roundedLatitude = location.latitude.roundForWeather(),
                roundedLongitude = location.longitude.roundForWeather(),
            )
        }
    }
}

private fun Double.roundForWeather(): Double {
    val rounded = BigDecimal.valueOf(this)
        .setScale(2, RoundingMode.HALF_UP)
        .toDouble()

    return if (rounded == 0.0) 0.0 else rounded
}