package com.ecostep.app.data.cache.route

import com.ecostep.app.data.model.RouteInfo
import kotlinx.serialization.Serializable

@Serializable
internal data class RouteCacheEntry(
    val routes: List<RouteInfo>,
    val fetchedAtMillis: Long,
)
