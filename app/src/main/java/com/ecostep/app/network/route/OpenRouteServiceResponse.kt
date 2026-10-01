package com.ecostep.app.network.route

import kotlinx.serialization.Serializable

/**
 * Provider response returned by OpenRouteService.
 *
 * Only fields required by EcoStep's shared RouteInfo model are represented
 * here. Geometry, metadata, way points and non-fatal provider warnings are
 * intentionally ignored by the JSON configuration.
 */
@Serializable
data class OpenRouteServiceResponse(
    val routes: List<OpenRouteServiceRoute>,
)

@Serializable
data class OpenRouteServiceRoute(
    val summary: OpenRouteServiceRouteSummary,
)

@Serializable
data class OpenRouteServiceRouteSummary(
    val distance: Double,
    val duration: Double,
)
