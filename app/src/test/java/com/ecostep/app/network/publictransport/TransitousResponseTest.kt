package com.ecostep.app.network.publictransport

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitousResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `Transitous response parses itinerary and transit legs`() {
        val sampleJson = """
            {
              "requestParameters": {},
              "debugOutput": {
                "algorithm": 1,
                "execute_time": 26
              },
              "direct": [],
              "itineraries": [
                {
                  "duration": 2220,
                  "startTime": "2026-09-29T04:43:00Z",
                  "endTime": "2026-09-29T05:20:00Z",
                  "transfers": 0,
                  "id": "ignored-provider-id",
                  "legs": [
                    {
                      "mode": "WALK",
                      "duration": 300,
                      "startTime": "2026-09-29T04:43:00Z",
                      "endTime": "2026-09-29T04:48:00Z",
                      "realTime": false,
                      "distance": 306.0
                    },
                    {
                      "mode": "TRAM",
                      "duration": 1740,
                      "startTime": "2026-09-29T04:48:00Z",
                      "endTime": "2026-09-29T05:17:00Z",
                      "routeShortName": "19",
                      "displayName": "19",
                      "routeLongName": "North Coburg - Flinders Street Station",
                      "realTime": false,
                      "tripId": "ignored-trip-id"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString(
            TransitousResponse.serializer(),
            sampleJson,
        )

        assertEquals(1, response.itineraries.size)

        val itinerary = response.itineraries.first()

        assertEquals(2220L, itinerary.duration)
        assertEquals("2026-09-29T04:43:00Z", itinerary.startTime)
        assertEquals("2026-09-29T05:20:00Z", itinerary.endTime)
        assertEquals(0, itinerary.transfers)
        assertEquals(2, itinerary.legs.size)

        val tramLeg = itinerary.legs[1]

        assertEquals("TRAM", tramLeg.mode)
        assertEquals("19", tramLeg.routeShortName)
        assertEquals("19", tramLeg.displayName)
        assertEquals(
            "North Coburg - Flinders Street Station",
            tramLeg.routeLongName,
        )
        assertFalse(tramLeg.realTime)
    }

    @Test
    fun `Transitous response defaults missing itineraries to empty list`() {
        val response = json.decodeFromString(
            TransitousResponse.serializer(),
            "{}",
        )

        assertTrue(response.itineraries.isEmpty())
    }
}
