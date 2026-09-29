package com.ecostep.app.sensors.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.ecostep.app.data.model.GeoPoint
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Turns journey endpoints into human-readable place names for display only.
 *
 * Uses the platform Geocoder (no API key). Returns null when geocoding is unavailable,
 * offline or slow, so callers can keep showing coordinates as a fallback.
 * Results are cached in memory to avoid repeated lookups for the same point.
 */
class PlaceNameResolver(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())
    private val cache = mutableMapOf<String, String>()

    suspend fun resolve(point: GeoPoint): String? {
        if (!Geocoder.isPresent()) return null
        val key = "%.4f,%.4f".format(Locale.US, point.latitude, point.longitude)
        synchronized(cache) { cache[key] }?.let { return it }

        val name = withTimeoutOrNull(TIMEOUT_MILLIS) { lookup(point) }
            ?.toShortName()
            ?: return null
        synchronized(cache) { cache[key] = name }
        return name
    }

    private suspend fun lookup(point: GeoPoint): Address? =
        if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    point.latitude,
                    point.longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    },
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            }
        }

    /** "Building 151, University of Melbourne, Wilson Ave, Parkville ..." -> first two parts. */
    private fun Address.toShortName(): String? {
        val line = getAddressLine(0)?.takeIf { it.isNotBlank() }
        if (line != null) {
            return line.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .take(2).joinToString(", ")
        }
        return listOfNotNull(featureName, thoroughfare, locality)
            .distinct().take(2).joinToString(", ").ifBlank { null }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
