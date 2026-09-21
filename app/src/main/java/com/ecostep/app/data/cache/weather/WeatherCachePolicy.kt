package com.ecostep.app.data.cache.weather

/**
 * Defines when cached weather can be used directly or as an offline fallback.
 */
internal class WeatherCachePolicy(
    private val freshMaxAgeMillis: Long = DEFAULT_FRESH_MAX_AGE_MILLIS,
    private val offlineMaxAgeMillis: Long = DEFAULT_OFFLINE_MAX_AGE_MILLIS,
) {

    init {
        require(freshMaxAgeMillis > 0L) {
            "Fresh cache lifetime must be positive"
        }
        require(offlineMaxAgeMillis >= freshMaxAgeMillis) {
            "Offline fallback lifetime must not be shorter than fresh lifetime"
        }
    }

    fun isFresh(
        entry: WeatherCacheEntry,
        currentTimeMillis: Long,
    ): Boolean {
        return entry.ageMillis(currentTimeMillis) < freshMaxAgeMillis
    }

    fun canUseAsOfflineFallback(
        entry: WeatherCacheEntry,
        currentTimeMillis: Long,
    ): Boolean {
        return entry.ageMillis(currentTimeMillis) <= offlineMaxAgeMillis
    }

    private fun WeatherCacheEntry.ageMillis(
        currentTimeMillis: Long,
    ): Long {
        return (currentTimeMillis - fetchedAtMillis).coerceAtLeast(0L)
    }

    companion object {
        const val DEFAULT_FRESH_MAX_AGE_MILLIS: Long =
            30L * 60L * 1_000L

        const val DEFAULT_OFFLINE_MAX_AGE_MILLIS: Long =
            6L * 60L * 60L * 1_000L
    }
}
