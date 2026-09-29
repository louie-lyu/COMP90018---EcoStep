package com.ecostep.app.network.route

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.TransportMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProxyDataSourceTest {

    private val start = GeoPoint(
        latitude = -37.8136,
        longitude = 144.9631,
    )

    private val end = GeoPoint(
        latitude = -37.7963,
        longitude = 144.9614,
    )

    @Test
    fun `request uses bearer token and maps walking route`() = runTest {
        val api = FakeRouteProxyApi(
            response = successfulResponse(
                distance = 2110.0,
                duration = 1519.2,
            ),
        )
        val dataSource = RouteProxyDataSource(
            routeProxyApi = api,
            accessTokenProvider = RouteAccessTokenProvider {
                "firebase-id-token"
            },
        )

        val result = dataSource.getRoute(
            start = start,
            end = end,
            profile = OpenRouteServiceProfile.WALKING,
        )

        assertEquals(
            "Bearer firebase-id-token",
            api.authorization,
        )
        assertEquals(
            "foot-walking",
            api.request?.profile,
        )
        assertEquals(start, api.request?.start)
        assertEquals(end, api.request?.end)
        assertEquals(
            TransportMode.WALKING,
            result.mode,
        )
        assertEquals(
            2110.0,
            result.distanceMeters,
            0.0,
        )
        assertEquals(
            1519L,
            result.durationSeconds,
        )
    }

    @Test
    fun `missing token fails before network request`() = runTest {
        val api = FakeRouteProxyApi(
            response = successfulResponse(
                distance = 2110.0,
                duration = 1519.2,
            ),
        )
        val dataSource = RouteProxyDataSource(
            routeProxyApi = api,
            accessTokenProvider = RouteAccessTokenProvider {
                null
            },
        )

        try {
            dataSource.getRoute(
                start = start,
                end = end,
                profile = OpenRouteServiceProfile.WALKING,
            )
            throw AssertionError(
                "Expected RouteProxyAuthenticationException",
            )
        } catch (_: RouteProxyAuthenticationException) {
            // Expected.
        }

        assertFalse(api.wasCalled)
    }

    @Test
    fun `provider mapping failure is propagated`() = runTest {
        val invalidResponse = OpenRouteServiceResponse(
            routes = emptyList(),
        )
        val api = FakeRouteProxyApi(
            response = invalidResponse,
        )
        val dataSource = RouteProxyDataSource(
            routeProxyApi = api,
            accessTokenProvider = RouteAccessTokenProvider {
                "token"
            },
        )

        try {
            dataSource.getRoute(
                start = start,
                end = end,
                profile = OpenRouteServiceProfile.CAR,
            )
            throw AssertionError(
                "Expected a mapping failure",
            )
        } catch (actual: Exception) {
            assertTrue(
                actual is kotlinx.serialization.SerializationException,
            )
        }
    }

    private fun successfulResponse(
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

    private class FakeRouteProxyApi(
        private val response: OpenRouteServiceResponse,
    ) : RouteProxyApi {

        var wasCalled: Boolean = false
        var authorization: String? = null
        var request: RouteProxyRequest? = null

        override suspend fun getRoute(
            authorization: String,
            request: RouteProxyRequest,
        ): OpenRouteServiceResponse {
            wasCalled = true
            this.authorization = authorization
            this.request = request
            return response
        }
    }
}
