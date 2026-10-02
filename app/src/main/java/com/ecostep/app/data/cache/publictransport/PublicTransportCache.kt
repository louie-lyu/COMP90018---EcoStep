package com.ecostep.app.data.cache.publictransport

import com.ecostep.app.data.model.GeoPoint

internal interface PublicTransportCache {
    suspend fun get(
        start: GeoPoint,
        end: GeoPoint,
    ): PublicTransportCacheEntry?

    suspend fun save(
        start: GeoPoint,
        end: GeoPoint,
        entry: PublicTransportCacheEntry,
    )

    suspend fun remove(
        start: GeoPoint,
        end: GeoPoint,
    )
}
