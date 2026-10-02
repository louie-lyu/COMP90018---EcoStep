package com.ecostep.app.data.cache.publictransport

import com.ecostep.app.data.model.PublicTransportInfo

internal class PublicTransportCachePolicy(
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
        entry: PublicTransportCacheEntry,
        currentTimeMillis: Long,
    ): Boolean {
        val age = entry.ageMillis(currentTimeMillis) ?: return false
        return age < freshMaxAgeMillis
    }

    fun canUseAsOfflineFallback(
        entry: PublicTransportCacheEntry,
        currentTimeMillis: Long,
    ): Boolean {
        val age = entry.ageMillis(currentTimeMillis) ?: return false
        return age <= offlineMaxAgeMillis
    }

    fun futureOptions(
        entry: PublicTransportCacheEntry,
        currentTimeMillis: Long,
    ): List<PublicTransportInfo> {
        return entry.options.filter { option ->
            option.departureTimeMillis > currentTimeMillis
        }
    }

    private fun PublicTransportCacheEntry.ageMillis(
        currentTimeMillis: Long,
    ): Long? {
        val age = currentTimeMillis - fetchedAtMillis
        return age.takeIf { it >= 0L }
    }

    companion object {
        const val DEFAULT_FRESH_MAX_AGE_MILLIS: Long =
            2L * 60L * 1_000L
        const val DEFAULT_OFFLINE_MAX_AGE_MILLIS: Long =
            15L * 60L * 1_000L
    }
}
