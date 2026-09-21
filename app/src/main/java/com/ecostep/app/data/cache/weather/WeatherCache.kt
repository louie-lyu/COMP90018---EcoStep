package com.ecostep.app.data.cache.weather

import com.ecostep.app.data.model.GeoPoint

/**
 * Internal contract for reading and writing persistent weather cache entries.
 *
 * The implementation is responsible for converting the location into a
 * privacy-preserving rounded-coordinate key.
 */
internal interface WeatherCache {

    suspend fun get(location: GeoPoint): WeatherCacheEntry?

    suspend fun save(
        location: GeoPoint,
        entry: WeatherCacheEntry,
    )

    suspend fun remove(location: GeoPoint)
}