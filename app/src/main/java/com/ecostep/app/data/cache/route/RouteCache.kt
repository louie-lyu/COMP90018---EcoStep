package com.ecostep.app.data.cache.route

import com.ecostep.app.data.model.GeoPoint

internal interface RouteCache {
    suspend fun get(
        start: GeoPoint,
        end: GeoPoint,
    ): RouteCacheEntry?

    suspend fun save(
        start: GeoPoint,
        end: GeoPoint,
        entry: RouteCacheEntry,
    )

    suspend fun remove(
        start: GeoPoint,
        end: GeoPoint,
    )
}
