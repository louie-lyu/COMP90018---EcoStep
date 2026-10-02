package com.ecostep.app.data.cache.publictransport

import com.ecostep.app.data.model.GeoPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

internal class PublicTransportCacheKey private constructor(
    val roundedStart: GeoPoint,
    val roundedEnd: GeoPoint,
) {
    val value: String = String.format(
        Locale.US,
        "public_transport_%.4f_%.4f_%.4f_%.4f",
        roundedStart.latitude,
        roundedStart.longitude,
        roundedEnd.latitude,
        roundedEnd.longitude,
    )

    companion object {
        fun from(
            start: GeoPoint,
            end: GeoPoint,
        ): PublicTransportCacheKey {
            validate(start)
            validate(end)

            return PublicTransportCacheKey(
                roundedStart = start.roundedForPublicTransportCache(),
                roundedEnd = end.roundedForPublicTransportCache(),
            )
        }

        private fun validate(location: GeoPoint) {
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
        }
    }
}

private fun GeoPoint.roundedForPublicTransportCache(): GeoPoint {
    return GeoPoint(
        latitude = latitude.roundToFourDecimals(),
        longitude = longitude.roundToFourDecimals(),
    )
}

private fun Double.roundToFourDecimals(): Double {
    val rounded = BigDecimal.valueOf(this)
        .setScale(4, RoundingMode.HALF_UP)
        .toDouble()
    return if (rounded == 0.0) 0.0 else rounded
}
