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
    private val locations = mutableMapOf<String, GeoPoint>()

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

    /**
     * Finds a place by name, preferring results within about 30 km of [near]. Returns null
     * when the name cannot be found (e.g. personal labels like "Home") or geocoding is
     * unavailable.
     */
    suspend fun locate(query: String, near: GeoPoint): GeoPoint? {
        val name = query.trim()
        if (name.isEmpty() || !Geocoder.isPresent()) return null
        val key = "%s@%.2f,%.2f".format(Locale.US, name.lowercase(Locale.ROOT), near.latitude, near.longitude)
        synchronized(locations) { locations[key] }?.let { return it }

        val address = withTimeoutOrNull(TIMEOUT_MILLIS) { lookupByName(name, near) } ?: return null
        val point = GeoPoint(address.latitude, address.longitude)
        synchronized(locations) { locations[key] = point }
        return point
    }

    private suspend fun lookupByName(name: String, near: GeoPoint): Address? {
        val south = (near.latitude - SEARCH_RADIUS_DEGREES).coerceAtLeast(-90.0)
        val north = (near.latitude + SEARCH_RADIUS_DEGREES).coerceAtMost(90.0)
        val west = (near.longitude - SEARCH_RADIUS_DEGREES).coerceAtLeast(-180.0)
        val east = (near.longitude + SEARCH_RADIUS_DEGREES).coerceAtMost(180.0)
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(
                    name,
                    1,
                    south,
                    west,
                    north,
                    east,
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
                    geocoder.getFromLocationName(name, 1, south, west, north, east)?.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            }
        }
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
        const val SEARCH_RADIUS_DEGREES = 0.3
    }
}
