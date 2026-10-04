package com.ecostep.app.network.route

import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `decodes the reference polyline from the format specification`() {
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 1e-9)
        assertEquals(-120.2, points[0].longitude, 1e-9)
        assertEquals(40.7, points[1].latitude, 1e-9)
        assertEquals(-120.95, points[1].longitude, 1e-9)
        assertEquals(43.252, points[2].latitude, 1e-9)
        assertEquals(-126.453, points[2].longitude, 1e-9)
    }

    @Test
    fun `malformed input yields an empty path instead of failing`() {
        assertTrue(decodePolyline("_p~iF~ps|U_ul").isEmpty())
        assertTrue(decodePolyline("\u0001\u0002").isEmpty())
        assertTrue(decodePolyline("").isEmpty())
    }

    @Test
    fun `route mapper keeps the geometry as the route path`() {
        val response = OpenRouteServiceResponse(
            routes = listOf(
                OpenRouteServiceRoute(
                    summary = OpenRouteServiceRouteSummary(distance = 2_300.0, duration = 1_700.0),
                    geometry = "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
                ),
            ),
        )

        val route = response.toRouteInfo(OpenRouteServiceProfile.WALKING)

        assertEquals(TransportMode.WALKING, route.mode)
        assertEquals(3, route.path.size)
    }

    @Test
    fun `route without geometry still maps with an empty path`() {
        val response = OpenRouteServiceResponse(
            routes = listOf(OpenRouteServiceRoute(OpenRouteServiceRouteSummary(1_000.0, 600.0))),
        )

        assertTrue(response.toRouteInfo(OpenRouteServiceProfile.CAR).path.isEmpty())
    }
}
