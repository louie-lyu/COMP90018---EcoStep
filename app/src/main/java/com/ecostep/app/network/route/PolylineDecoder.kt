package com.ecostep.app.network.route

import com.ecostep.app.data.model.GeoPoint

/**
 * Decodes an encoded polyline (Google format, which OpenRouteService returns as `geometry`
 * by default). Returns an empty list for malformed input instead of throwing, because route
 * shape is display-only and must not fail the route request.
 */
internal fun decodePolyline(encoded: String, precision: Int = 5): List<GeoPoint> {
    val factor = Math.pow(10.0, precision.toDouble())
    val points = mutableListOf<GeoPoint>()
    var index = 0
    var latitude = 0L
    var longitude = 0L

    fun nextValue(): Long? {
        var result = 0L
        var shift = 0
        while (index < encoded.length) {
            val byte = encoded[index++].code - 63
            if (byte < 0) return null
            result = result or ((byte and 0x1f).toLong() shl shift)
            shift += 5
            if (byte < 0x20) {
                return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            }
            if (shift > 60) return null
        }
        return null
    }

    while (index < encoded.length) {
        val deltaLatitude = nextValue() ?: return emptyList()
        val deltaLongitude = nextValue() ?: return emptyList()
        latitude += deltaLatitude
        longitude += deltaLongitude
        val point = GeoPoint(latitude / factor, longitude / factor)
        if (point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) return emptyList()
        points += point
    }
    return points
}
