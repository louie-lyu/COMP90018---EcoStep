package com.ecostep.app.network.publictransport

import com.ecostep.app.algorithm.TransitStop
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitousStopMapperTest {

    @Test
    fun `reads transit stops in travel order from API response`() {
        val json = """
            {
              "itineraries": [{
                "duration": 600,
                "startTime": "2026-10-01T08:00:00Z",
                "endTime": "2026-10-01T08:10:00Z",
                "legs": [{
                  "mode": "TRAM",
                  "startTime": "2026-10-01T08:00:00Z",
                  "endTime": "2026-10-01T08:10:00Z",
                  "from": {"name": "A", "lat": -37.810, "lon": 144.960},
                  "intermediateStops": [
                    {"name": "B", "lat": -37.805, "lon": 144.965}
                  ],
                  "to": {"name": "C", "lat": -37.800, "lon": 144.970}
                }]
              }]
            }
        """.trimIndent()

        val response = Json { ignoreUnknownKeys = true }
            .decodeFromString<TransitousResponse>(json)

        assertEquals(
            listOf(
                listOf(
                    TransitStop(-37.810, 144.960),
                    TransitStop(-37.805, 144.965),
                    TransitStop(-37.800, 144.970),
                ),
            ),
            response.toTransitStopRoutes(),
        )
    }

    @Test
    fun `walking leg without stops produces no transit route`() {
        val response = TransitousResponse(
            itineraries = listOf(
                TransitousItinerary(
                    duration = 300,
                    startTime = "2026-10-01T08:00:00Z",
                    endTime = "2026-10-01T08:05:00Z",
                    legs = listOf(
                        TransitousLeg(
                            mode = "WALK",
                            startTime = "2026-10-01T08:00:00Z",
                            endTime = "2026-10-01T08:05:00Z",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(response.toTransitStopRoutes().isEmpty())
    }
}