package com.ecostep.app.algorithm

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.SensorFeatures
import com.ecostep.app.data.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTransportClassifierTest {

    @Test
    fun walkingSensorFeaturesReturnWalking() = runBlocking {
        val classifier = classifierWith(TransportEvidence())

        val result = classifier.classify(
            journey(
                features = features(
                    average = 1.3,
                    p95 = 2.0,
                    maximum = 2.8,
                ),
            ),
        )

        assertEquals(TransportMode.WALKING, result.mode)
    }

    @Test
    fun bicycleActivityReturnsCycling() = runBlocking {
        val classifier = classifierWith(
            TransportEvidence(
                activity = ActivityHint(MotionHint.CYCLING, 85),
            ),
        )

        val result = classifier.classify(journey())

        assertEquals(TransportMode.CYCLING, result.mode)
    }

    @Test
    fun vehicleStoppingAtTwoOrderedStationsReturnsPublicTransport() =
        runBlocking {
            val firstStop = TransitStop(-37.8136, 144.9631)
            val secondStop = TransitStop(-37.8150, 144.9690)

            val classifier = classifierWith(
                TransportEvidence(
                    activity = ActivityHint(MotionHint.IN_VEHICLE, 90),
                    track = listOf(
                        point(firstStop, 0L),
                        point(firstStop, 11_000L),
                        point(secondStop, 30_000L),
                        point(secondStop, 42_000L),
                    ),
                    transitRoutes = listOf(
                        listOf(firstStop, secondStop),
                    ),
                ),
            )

            val result = classifier.classify(journey())

            assertEquals(TransportMode.PUBLIC_TRANSPORT, result.mode)
        }

    @Test
    fun vehicleWithoutTransitEvidenceRemainsUnknown() = runBlocking {
        val classifier = classifierWith(
            TransportEvidence(
                activity = ActivityHint(MotionHint.IN_VEHICLE, 90),
            ),
        )

        val result = classifier.classify(journey())

        assertEquals(TransportMode.UNKNOWN, result.mode)
    }

    private fun classifierWith(
        evidence: TransportEvidence,
    ) = DefaultTransportClassifier(
        evidenceProvider = object : TransportEvidenceProvider {
            override suspend fun getEvidence(
                journey: JourneySummary,
            ) = evidence
        },
    )

    private fun journey(
        features: SensorFeatures? = null,
    ) = JourneySummary(
        journeyId = "test-journey",
        userId = "test-user",
        startLocation = GeoPoint(-37.8136, 144.9631),
        endLocation = GeoPoint(-37.8150, 144.9690),
        startTimeMillis = 0L,
        endTimeMillis = 60_000L,
        distanceMeters = 800.0,
        transportMode = TransportMode.UNKNOWN,
        sensorFeatures = features,
    )

    private fun features(
        average: Double,
        p95: Double,
        maximum: Double,
    ) = SensorFeatures(
        averageSpeedMps = average,
        p95SpeedMps = p95,
        maxSpeedMps = maximum,
        stopRatio = 0.1,
        averageGpsAccuracyMeters = 10.0,
        gpsSampleCount = 10,
        accelMagnitudeMean = 0.0,
        accelMagnitudeStd = 0.0,
        accelSampleCount = 10,
        gyroMagnitudeMean = 0.0,
        gyroMagnitudeStd = 0.0,
        gyroSampleCount = 10,
    )

    private fun point(
        stop: TransitStop,
        time: Long,
    ) = TransportTrackPoint(
        latitude = stop.latitude,
        longitude = stop.longitude,
        timeMillis = time,
        speedMps = 0.0,
        accuracyMeters = 10.0,
    )
}