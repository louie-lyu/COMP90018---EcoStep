package com.ecostep.app.network.route

import com.ecostep.app.data.model.TransportMode
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenRouteServiceRouteMapperTest {

    @Test
    fun `profiles map to shared transport modes`() {
        val response = routeResponse(
            distance = 2110.0,
            duration = 1519.2,
        )

        assertEquals(
            TransportMode.WALKING,
            response.toRouteInfo(
                OpenRouteServiceProfile.WALKING,
            ).mode,
        )
        assertEquals(
            TransportMode.CYCLING,
            response.toRouteInfo(
                OpenRouteServiceProfile.CYCLING,
            ).mode,
        )
        assertEquals(
            TransportMode.CAR,
            response.toRouteInfo(
                OpenRouteServiceProfile.CAR,
            ).mode,
        )
    }

    @Test
    fun `route summary maps to shared RouteInfo`() {
        val response = routeResponse(
            distance = 2110.0,
            duration = 1519.2,
        )

        val routeInfo = response.toRouteInfo(
            OpenRouteServiceProfile.WALKING,
        )

        assertEquals(
            TransportMode.WALKING,
            routeInfo.mode,
        )
        assertEquals(
            2110.0,
            routeInfo.distanceMeters,
            0.0,
        )
        assertEquals(
            1519L,
            routeInfo.durationSeconds,
        )
    }

    @Test
    fun `duration is rounded to nearest second`() {
        val response = routeResponse(
            distance = 2891.6,
            duration = 433.9,
        )

        val routeInfo = response.toRouteInfo(
            OpenRouteServiceProfile.CAR,
        )

        assertEquals(
            434L,
            routeInfo.durationSeconds,
        )
    }

    @Test
    fun `empty route list is rejected`() {
        val response = OpenRouteServiceResponse(
            routes = emptyList(),
        )

        assertThrows(
            SerializationException::class.java,
        ) {
            response.toRouteInfo(
                OpenRouteServiceProfile.WALKING,
            )
        }
    }

    @Test
    fun `non-positive distance is rejected`() {
        val response = routeResponse(
            distance = 0.0,
            duration = 100.0,
        )

        assertThrows(
            SerializationException::class.java,
        ) {
            response.toRouteInfo(
                OpenRouteServiceProfile.CYCLING,
            )
        }
    }

    @Test
    fun `non-positive duration is rejected`() {
        val response = routeResponse(
            distance = 100.0,
            duration = 0.0,
        )

        assertThrows(
            SerializationException::class.java,
        ) {
            response.toRouteInfo(
                OpenRouteServiceProfile.CAR,
            )
        }
    }

    private fun routeResponse(
        distance: Double,
        duration: Double,
    ): OpenRouteServiceResponse {
        return OpenRouteServiceResponse(
            routes = listOf(
                OpenRouteServiceRoute(
                    summary = OpenRouteServiceRouteSummary(
                        distance = distance,
                        duration = duration,
                    ),
                ),
            ),
        )
    }
}
