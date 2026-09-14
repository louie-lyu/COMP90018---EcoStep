package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/** A latitude/longitude pair, shared by [JourneySummary] and [RouteInfo]. */
@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)
