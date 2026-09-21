package com.ecostep.app.data.cache.weather

import com.ecostep.app.data.model.WeatherData
import kotlinx.serialization.Serializable

/**
 * One weather result stored in the local cache.
 *
 * [fetchedAtMillis] records when the network response was received so the
 * repository can apply its fresh-cache and offline-fallback policies.
 */
@Serializable
internal data class WeatherCacheEntry(
    val weatherData: WeatherData,
    val fetchedAtMillis: Long,
)