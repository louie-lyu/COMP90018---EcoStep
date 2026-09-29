package com.ecostep.app.network.route

import com.ecostep.app.data.model.GeoPoint
import kotlinx.serialization.Serializable

/**
 * Request body sent from the Android app to the team's Route proxy.
 *
 * The OpenRouteService API key is deliberately excluded. The proxy adds
 * the provider credential on the server side.
 */
@Serializable
internal data class RouteProxyRequest(
    val profile: String,
    val start: GeoPoint,
    val end: GeoPoint,
)
