package com.ecostep.app.network.publictransport

import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitousPublicTransportDataSourceTest {

    @Test
    fun `data source passes coordinates and returns mapped options`() =
        runTest {
            val fakeApi = FakeTransitousApi(
                response = TransitousResponse(
                    itineraries = listOf(
                        TransitousItinerary(
                            duration = 2220L,
                            startTime =
                                "2026-09-29T04:43:00Z",
                            endTime =
                                "2026-09-29T05:20:00Z",
                            transfers = 0,
                            legs = listOf(
                                TransitousLeg(
                                    mode = "WALK",
                                    startTime =
                                        "2026-09-29T04:43:00Z",
                                    endTime =
                                        "2026-09-29T04:48:00Z",
                                ),
                                TransitousLeg(
                                    mode = "TRAM",
                                    startTime =
                                        "2026-09-29T04:48:00Z",
                                    endTime =
                                        "2026-09-29T05:17:00Z",
                                    routeShortName = "19",
                                    displayName = "19",
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val dataSource =
                TransitousPublicTransportDataSource(
                    transitousApi = fakeApi,
                )

            val results =
                dataSource.getPublicTransportOptions(
                    start = GeoPoint(
                        latitude = -37.8183,
                        longitude = 144.9671,
                    ),
                    end = GeoPoint(
                        latitude = -37.7697,
                        longitude = 144.9611,
                    ),
                )

            assertEquals(
                "-37.8183,144.9671",
                fakeApi.requestedFromPlace,
            )
            assertEquals(
                "-37.7697,144.9611",
                fakeApi.requestedToPlace,
            )
            assertEquals(
                false,
                fakeApi.requestedArriveBy,
            )
            assertEquals(
                2,
                fakeApi.requestedMaxTransfers,
            )
            assertEquals(
                false,
                fakeApi.requestedDetailedLegs,
            )
            assertEquals(
                false,
                fakeApi.requestedDetailedTransfers,
            )
            assertEquals(
                TransitousApi.USER_AGENT,
                fakeApi.requestedUserAgent,
            )

            assertEquals(1, results.size)
            assertEquals(
                "Tram 19",
                results.first().line,
            )
            assertEquals(
                2220L,
                results.first()
                    .estimatedDurationSeconds,
            )
        }

    @Test
    fun `empty Transitous response returns empty list`() =
        runTest {
            val fakeApi = FakeTransitousApi(
                response = TransitousResponse(),
            )

            val dataSource =
                TransitousPublicTransportDataSource(
                    transitousApi = fakeApi,
                )

            val results =
                dataSource.getPublicTransportOptions(
                    start = GeoPoint(
                        latitude = -37.8183,
                        longitude = 144.9671,
                    ),
                    end = GeoPoint(
                        latitude = -37.7697,
                        longitude = 144.9611,
                    ),
                )

            assertTrue(results.isEmpty())
        }

    private class FakeTransitousApi(
        private val response: TransitousResponse,
    ) : TransitousApi {

        var requestedFromPlace: String = ""
        var requestedToPlace: String = ""
        var requestedArriveBy: Boolean = true
        var requestedMaxTransfers: Int = -1
        var requestedDetailedLegs: Boolean = true
        var requestedDetailedTransfers: Boolean = true
        var requestedUserAgent: String = ""

        override suspend fun planJourney(
            fromPlace: String,
            toPlace: String,
            arriveBy: Boolean,
            maxTransfers: Int,
            detailedLegs: Boolean,
            detailedTransfers: Boolean,
            userAgent: String,
        ): TransitousResponse {
            requestedFromPlace = fromPlace
            requestedToPlace = toPlace
            requestedArriveBy = arriveBy
            requestedMaxTransfers = maxTransfers
            requestedDetailedLegs = detailedLegs
            requestedDetailedTransfers =
                detailedTransfers
            requestedUserAgent = userAgent

            return response
        }
    }
}
