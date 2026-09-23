package com.ecostep.app.network.route

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenRouteServiceResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `OpenRouteService response parses route summary correctly`() {
        val sampleJson = """
            {
              "bbox": [
                144.959768,
                -37.813596,
                144.963112,
                -37.796280
              ],
              "routes": [
                {
                  "summary": {
                    "distance": 2110.0,
                    "duration": 1519.2
                  },
                  "geometry": "encoded-route-geometry",
                  "way_points": [
                    0,
                    126
                  ],
                  "warnings": [
                    {
                      "code": 4,
                      "message": "Non-fatal provider warning"
                    }
                  ]
                }
              ],
              "metadata": {
                "service": "routing",
                "query": {
                  "profile": "foot-walking"
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString(
            OpenRouteServiceResponse.serializer(),
            sampleJson,
        )

        assertEquals(1, response.routes.size)
        assertEquals(
            2110.0,
            response.routes.first().summary.distance,
            0.0,
        )
        assertEquals(
            1519.2,
            response.routes.first().summary.duration,
            0.0,
        )
    }
}
