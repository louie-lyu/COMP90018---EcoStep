package com.ecostep.app.network.route

import com.ecostep.app.data.model.GeoPoint
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProxyRequestTest {

    @Test
    fun `request serializes agreed proxy contract without credentials`() {
        val request = RouteProxyRequest(
            profile = "foot-walking",
            start = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
            end = GeoPoint(
                latitude = -37.7963,
                longitude = 144.9614,
            ),
        )

        val encoded = Json.encodeToString(
            RouteProxyRequest.serializer(),
            request,
        )

        val decoded = Json.decodeFromString(
            RouteProxyRequest.serializer(),
            encoded,
        )

        assertEquals(request, decoded)
        assertTrue(encoded.contains("\"profile\":\"foot-walking\""))
        assertTrue(encoded.contains("\"start\""))
        assertTrue(encoded.contains("\"end\""))

        assertFalse(
            encoded.contains(
                other = "apiKey",
                ignoreCase = true,
            ),
        )
        assertFalse(
            encoded.contains(
                other = "authorization",
                ignoreCase = true,
            ),
        )
        assertFalse(
            encoded.contains(
                other = "secret",
                ignoreCase = true,
            ),
        )
    }
}
