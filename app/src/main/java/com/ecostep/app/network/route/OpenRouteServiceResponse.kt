package com.ecostep.app.network.route

import kotlinx.serialization.Serializable

/**
 * Provider response returned by OpenRouteService.
 *
 * Only fields required by EcoStep's shared RouteInfo model are represented
 * here, including the encoded route geometry for map display. Metadata, way
 * points and non-fatal provider warnings are ignored by the JSON configuration.
 */
@Serializable
data class OpenRouteServiceResponse(
    val routes: List<OpenRouteServiceRoute>,
)

@Serializable
data class OpenRouteServiceRoute(
    val summary: OpenRouteServiceRouteSummary,
    /** Encoded polyline of the route shape (OpenRouteService's default geometry format). */
    val geometry: String? = null,
)

@Serializable
data class OpenRouteServiceRouteSummary(
    val distance: Double,
    val duration: Double,
)
