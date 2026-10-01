package com.ecostep.app.network.publictransport

import kotlinx.serialization.Serializable

/**
 * Minimal response returned by the Transitous MOTIS journey-planning API.
 *
 * Only fields needed to produce EcoStep's shared PublicTransportInfo model
 * are represented here. Provider-specific geometry, debug output, fares and
 * step instructions are intentionally ignored.
 */
@Serializable
data class TransitousResponse(
    val itineraries: List<TransitousItinerary> = emptyList(),
)

@Serializable
data class TransitousItinerary(
    val duration: Long,
    val startTime: String,
    val endTime: String,
    val transfers: Int = 0,
    val legs: List<TransitousLeg> = emptyList(),
)

@Serializable
data class TransitousLeg(
    val mode: String,
    val startTime: String,
    val endTime: String,
    val routeShortName: String? = null,
    val displayName: String? = null,
    val routeLongName: String? = null,
    val realTime: Boolean = false,
)
