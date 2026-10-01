package com.ecostep.app.network.publictransport

import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.MotionHint
import com.ecostep.app.algorithm.TransitStop
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.sensors.tracking.FeatureAccumulator
import com.ecostep.app.sensors.tracking.LocationSample
import com.ecostep.app.sensors.tracking.RecordingResult
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultTransportEvidenceProviderTest {

    @Test
    fun `combines activity trace and stops for journey departure time`() = runTest {
        val api = FakeTransitousApi(
            TransitousResponse(
                itineraries = listOf(
                    TransitousItinerary(
                        duration = 600,
                        startTime = "2026-10-01T08:00:00Z",
                        endTime = "2026-10-01T08:10:00Z",
                        legs = listOf(
                            TransitousLeg(
                                mode = "TRAM",
                                startTime = "2026-10-01T08:00:00Z",
                                endTime = "2026-10-01T08:10:00Z",
                                from = TransitousPlace(-37.810, 144.960),
                                to = TransitousPlace(-37.800, 144.970),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val recording = recording()
        val journey = journey(recording)

        val evidence =
            DefaultTransportEvidenceProvider(recording, api).getEvidence(journey)

        assertEquals(recording.activityHint, evidence.activity)
        assertEquals(3, evidence.track.size)
        assertEquals(0.0, evidence.track[1].speedMps, 0.01)
        assertEquals(
            listOf(
                listOf(
                    TransitStop(-37.810, 144.960),
                    TransitStop(-37.800, 144.970),
                ),
            ),
            evidence.transitRoutes,
        )
        assertEquals(
            Instant.ofEpochMilli(recording.startTimeMillis).toString(),
            api.requestedTime,
        )
    }

    @Test
    fun `network failure keeps local evidence without transit stops`() = runTest {
        val recording = recording()
        val evidence = DefaultTransportEvidenceProvider(
            recording,
            FakeTransitousApi(null),
        ).getEvidence(journey(recording))

        assertEquals(recording.activityHint, evidence.activity)
        assertEquals(3, evidence.track.size)
        assertTrue(evidence.transitRoutes.isEmpty())
    }

    private fun recording(): RecordingResult {
        val trace = listOf(0L, 5_000L, 10_000L).map { time ->
            LocationSample(
                latitude = -37.810,
                longitude = 144.960,
                accuracyMeters = 5f,
                speedMps = null,
                timeMillis = 1_700_000_000_000L + time,
            )
        }
        return RecordingResult(
            startTimeMillis = trace.first().timeMillis,
            endTimeMillis = trace.first().timeMillis + 30_000L,
            first = trace.first(),
            last = trace.last(),
            distanceMeters = 0.0,
            features = FeatureAccumulator().toFeatures(30_000L),
            trace = trace,
            activityHint = ActivityHint(MotionHint.IN_VEHICLE, 80),
        )
    }

    private fun journey(recording: RecordingResult) = JourneySummary(
        journeyId = "journey-1",
        userId = "user-1",
        startLocation = GeoPoint(-37.810, 144.960),
        endLocation = GeoPoint(-37.800, 144.970),
        startTimeMillis = recording.startTimeMillis,
        endTimeMillis = recording.endTimeMillis,
        distanceMeters = 1000.0,
        transportMode = TransportMode.UNKNOWN,
    )

    private class FakeTransitousApi(
        private val response: TransitousResponse?,
    ) : TransitousApi {
        var requestedTime: String? = null

        override suspend fun planJourney(
            fromPlace: String,
            toPlace: String,
            time: String?,
            arriveBy: Boolean,
            maxTransfers: Int,
            detailedLegs: Boolean,
            detailedTransfers: Boolean,
            userAgent: String,
        ): TransitousResponse {
            requestedTime = time
            return response ?: throw IOException("Offline")
        }
    }
}
