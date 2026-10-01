package com.ecostep.app.network.publictransport

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitousPublicTransportMapperTest {

    @Test
    fun `direct tram itinerary maps to shared model`() {
        val response = TransitousResponse(
            itineraries = listOf(
                TransitousItinerary(
                    duration = 2220L,
                    startTime = "2026-09-29T04:43:00Z",
                    endTime = "2026-09-29T05:20:00Z",
                    transfers = 0,
                    legs = listOf(
                        TransitousLeg(
                            mode = "WALK",
                            startTime = "2026-09-29T04:43:00Z",
                            endTime = "2026-09-29T04:48:00Z",
                        ),
                        TransitousLeg(
                            mode = "TRAM",
                            startTime = "2026-09-29T04:48:00Z",
                            endTime = "2026-09-29T05:17:00Z",
                            routeShortName = "19",
                            displayName = "19",
                            routeLongName =
                                "North Coburg - Flinders Street Station",
                        ),
                    ),
                ),
            ),
        )

        val results = response.toPublicTransportInfoList()

        assertEquals(1, results.size)

        val result = results.first()

        assertEquals("Tram 19", result.line)
        assertEquals(
            Instant.parse(
                "2026-09-29T04:48:00Z",
            ).toEpochMilli(),
            result.departureTimeMillis,
        )
        assertEquals(
            2220L,
            result.estimatedDurationSeconds,
        )
    }

    @Test
    fun `train to tram transfer combines both route labels`() {
        val response = TransitousResponse(
            itineraries = listOf(
                TransitousItinerary(
                    duration = 1920L,
                    startTime = "2026-09-29T04:40:00Z",
                    endTime = "2026-09-29T05:12:00Z",
                    transfers = 1,
                    legs = listOf(
                        TransitousLeg(
                            mode = "WALK",
                            startTime = "2026-09-29T04:40:00Z",
                            endTime = "2026-09-29T04:44:00Z",
                        ),
                        TransitousLeg(
                            mode = "SUBWAY",
                            startTime = "2026-09-29T04:45:00Z",
                            endTime = "2026-09-29T04:57:00Z",
                            routeShortName = "Sunbury",
                            displayName = "Sunbury",
                            routeLongName = "Sunbury Line",
                        ),
                        TransitousLeg(
                            mode = "WALK",
                            startTime = "2026-09-29T04:57:00Z",
                            endTime = "2026-09-29T05:01:00Z",
                        ),
                        TransitousLeg(
                            mode = "TRAM",
                            startTime = "2026-09-29T05:02:00Z",
                            endTime = "2026-09-29T05:10:00Z",
                            routeShortName = "19",
                            displayName = "19",
                        ),
                    ),
                ),
            ),
        )

        val results = response.toPublicTransportInfoList()

        assertEquals(1, results.size)
        assertEquals(
            "Train Sunbury → Tram 19",
            results.first().line,
        )
        assertEquals(
            Instant.parse(
                "2026-09-29T04:45:00Z",
            ).toEpochMilli(),
            results.first().departureTimeMillis,
        )
        assertEquals(
            1920L,
            results.first().estimatedDurationSeconds,
        )
    }

    @Test
    fun `walk only itinerary is ignored`() {
        val response = TransitousResponse(
            itineraries = listOf(
                TransitousItinerary(
                    duration = 600L,
                    startTime = "2026-09-29T04:40:00Z",
                    endTime = "2026-09-29T04:50:00Z",
                    legs = listOf(
                        TransitousLeg(
                            mode = "WALK",
                            startTime = "2026-09-29T04:40:00Z",
                            endTime = "2026-09-29T04:50:00Z",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            response.toPublicTransportInfoList().isEmpty(),
        )
    }

    @Test
    fun `invalid transit departure time is ignored`() {
        val response = TransitousResponse(
            itineraries = listOf(
                TransitousItinerary(
                    duration = 900L,
                    startTime = "invalid-time",
                    endTime = "2026-09-29T05:00:00Z",
                    legs = listOf(
                        TransitousLeg(
                            mode = "BUS",
                            startTime = "invalid-time",
                            endTime = "2026-09-29T05:00:00Z",
                            routeShortName = "207",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            response.toPublicTransportInfoList().isEmpty(),
        )
    }

    @Test
    fun `non positive duration is ignored`() {
        val response = TransitousResponse(
            itineraries = listOf(
                TransitousItinerary(
                    duration = 0L,
                    startTime = "2026-09-29T04:40:00Z",
                    endTime = "2026-09-29T04:40:00Z",
                    legs = listOf(
                        TransitousLeg(
                            mode = "BUS",
                            startTime = "2026-09-29T04:40:00Z",
                            endTime = "2026-09-29T04:40:00Z",
                            routeShortName = "207",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(
            response.toPublicTransportInfoList().isEmpty(),
        )
    }
}
