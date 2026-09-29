package com.ecostep.app.network.route

import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import kotlin.math.roundToLong
import kotlinx.serialization.SerializationException

internal enum class OpenRouteServiceProfile(
    val apiValue: String,
    val transportMode: TransportMode,
) {
    WALKING(
        apiValue = "foot-walking",
        transportMode = TransportMode.WALKING,
    ),

    CYCLING(
        apiValue = "cycling-regular",
        transportMode = TransportMode.CYCLING,
    ),

    CAR(
        apiValue = "driving-car",
        transportMode = TransportMode.CAR,
    ),
}

internal fun OpenRouteServiceResponse.toRouteInfo(
    profile: OpenRouteServiceProfile,
): RouteInfo {
    val summary = routes.firstOrNull()?.summary
        ?: throw SerializationException(
            "OpenRouteService response contains no routes.",
        )

    if (
        !summary.distance.isFinite() ||
        summary.distance <= 0.0
    ) {
        throw SerializationException(
            "OpenRouteService returned an invalid route distance.",
        )
    }

    if (
        !summary.duration.isFinite() ||
        summary.duration <= 0.0
    ) {
        throw SerializationException(
            "OpenRouteService returned an invalid route duration.",
        )
    }

    val durationSeconds = summary.duration.roundToLong()

    if (durationSeconds <= 0L) {
        throw SerializationException(
            "OpenRouteService route duration rounds to zero seconds.",
        )
    }

    return RouteInfo(
        mode = profile.transportMode,
        distanceMeters = summary.distance,
        durationSeconds = durationSeconds,
    )
}
