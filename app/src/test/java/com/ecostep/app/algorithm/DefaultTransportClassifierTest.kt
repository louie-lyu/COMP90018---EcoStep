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
    fun vehicleWithoutTransitEvidenceReturnsCar() = runBlocking {
        val classifier = classifierWith(
            TransportEvidence(
                activity = ActivityHint(MotionHint.IN_VEHICLE, 90),
            ),
        )

        val result = classifier.classify(journey())

        assertEquals(TransportMode.CAR, result.mode)
    }

    @Test
    fun steadyMotionAtCyclingSpeedIsTreatedAsVehicle() = runBlocking {
        val classifier = classifierWith(TransportEvidence(), motionThresholds)

        val result = classifier.classify(
            journey(features = slowVehicleFeatures(accelStd = 0.3)),
        )

        assertEquals(TransportMode.CAR, result.mode)
    }

    @Test
    fun vibratingMotionAtCyclingSpeedStaysCycling() = runBlocking {
        val classifier = classifierWith(TransportEvidence(), motionThresholds)

        val result = classifier.classify(
            journey(features = slowVehicleFeatures(accelStd = 2.5)),
        )

        assertEquals(TransportMode.CYCLING, result.mode)
    }

    @Test
    fun steadyMotionAtWalkingSpeedIsUnknown() = runBlocking {
        val classifier = classifierWith(TransportEvidence(), motionThresholds)

        val result = classifier.classify(
            journey(
                features = features(
                    average = 1.3,
                    p95 = 2.0,
                    maximum = 2.8,
                    accelStd = 0.3,
                    accelSamples = 3_000,
                ),
            ),
        )

        assertEquals(TransportMode.UNKNOWN, result.mode)
    }

    @Test
    fun tooFewMotionSamplesKeepSpeedResult() = runBlocking {
        val classifier = classifierWith(TransportEvidence(), motionThresholds)

        val result = classifier.classify(
            journey(features = slowVehicleFeatures(accelStd = 0.3, accelSamples = 10)),
        )

        assertEquals(TransportMode.CYCLING, result.mode)
    }

    @Test
    fun motionIsIgnoredWithoutThresholds() = runBlocking {
        val classifier = classifierWith(TransportEvidence(), motionThresholds = null)

        val result = classifier.classify(
            journey(features = slowVehicleFeatures(accelStd = 0.3)),
        )

        assertEquals(TransportMode.CYCLING, result.mode)
    }

    @Test
    fun activityHintStillWinsOverMotion() = runBlocking {
        val classifier = classifierWith(
            TransportEvidence(
                activity = ActivityHint(MotionHint.CYCLING, 85),
            ),
            motionThresholds,
        )

        val result = classifier.classify(
            journey(features = slowVehicleFeatures(accelStd = 0.3)),
        )

        assertEquals(TransportMode.CYCLING, result.mode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun motionThresholdsRejectNonPositiveLimit() {
        MotionThresholds(steadyAccelStd = 0.0, minAccelSamples = 100)
    }

    // 测试用的阈值，不是校准结果。
    private val motionThresholds = MotionThresholds(
        steadyAccelStd = 0.8,
        minAccelSamples = 500,
    )

    private fun slowVehicleFeatures(
        accelStd: Double,
        accelSamples: Int = 3_000,
    ) = features(
        average = 4.0,
        p95 = 8.0,
        maximum = 12.0,
        accelStd = accelStd,
        accelSamples = accelSamples,
    )

    private fun classifierWith(
        evidence: TransportEvidence,
        motionThresholds: MotionThresholds? = null,
    ) = DefaultTransportClassifier(
        evidenceProvider = object : TransportEvidenceProvider {
            override suspend fun getEvidence(
                journey: JourneySummary,
            ) = evidence
        },
        motionThresholds = motionThresholds,
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
        accelStd: Double = 0.0,
        accelSamples: Int = 10,
    ) = SensorFeatures(
        averageSpeedMps = average,
        p95SpeedMps = p95,
        maxSpeedMps = maximum,
        stopRatio = 0.1,
        averageGpsAccuracyMeters = 10.0,
        gpsSampleCount = 10,
        accelMagnitudeMean = 0.0,
        accelMagnitudeStd = accelStd,
        accelSampleCount = accelSamples,
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