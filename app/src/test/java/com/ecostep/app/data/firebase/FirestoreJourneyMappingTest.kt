package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.SensorFeatures
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreJourneyMappingTest {

    @Test
    fun `sensor features round-trip through Firestore map`() {
        val journey = journey(sensorFeatures())

        val map = journey.toFirestoreMap()
        val storedFeatures = map["sensorFeatures"] as Map<*, *>
        val decoded = map.toJourneySummary(journey.journeyId)

        assertEquals(1L, (storedFeatures["featureVersion"] as Number).toLong())
        assertEquals(598L, (storedFeatures["gpsSampleCount"] as Number).toLong())
        assertEquals(journey, decoded)
    }

    @Test
    fun `legacy Firestore map without sensor features returns null`() {
        val map = journey(null).toFirestoreMap()

        assertFalse(map.containsKey("sensorFeatures"))
        assertNull(map.toJourneySummary("j1").sensorFeatures)
    }

    @Test
    fun `missing feature version defaults to v1`() {
        val map = journey(sensorFeatures()).toFirestoreMap().toMutableMap()
        val features = (map.getValue("sensorFeatures") as Map<*, *>).toMutableMap()
        features.remove("featureVersion")
        map["sensorFeatures"] = features

        assertEquals(1, map.toJourneySummary("j1").sensorFeatures?.featureVersion)
    }

    @Test
    fun `Firestore integer values decode through Number`() {
        val map = journey(sensorFeatures()).toFirestoreMap().toMutableMap()
        val features = (map.getValue("sensorFeatures") as Map<*, *>).toMutableMap()
        features["gpsSampleCount"] = 598L
        features["accelSampleCount"] = 59_210L
        features["gyroSampleCount"] = 59_188L
        map["sensorFeatures"] = features

        val decoded = map.toJourneySummary("j1").sensorFeatures

        assertEquals(598, decoded?.gpsSampleCount)
        assertEquals(59_210, decoded?.accelSampleCount)
        assertEquals(59_188, decoded?.gyroSampleCount)
        assertTrue(decoded != null)
    }

    private fun journey(sensorFeatures: SensorFeatures?) = JourneySummary(
        journeyId = "j1",
        userId = "u1",
        startLocation = GeoPoint(-37.80, 144.90),
        endLocation = GeoPoint(-37.81, 145.00),
        startTimeMillis = 0L,
        endTimeMillis = 900_000L,
        distanceMeters = 1500.0,
        transportMode = TransportMode.WALKING,
        sensorFeatures = sensorFeatures,
    )

    private fun sensorFeatures() = SensorFeatures(
        averageSpeedMps = 2.17,
        p95SpeedMps = 5.9,
        maxSpeedMps = 7.2,
        stopRatio = 0.18,
        averageGpsAccuracyMeters = 8.4,
        gpsSampleCount = 598,
        accelMagnitudeMean = 0.62,
        accelMagnitudeStd = 0.81,
        accelSampleCount = 59_210,
        gyroMagnitudeMean = 0.35,
        gyroMagnitudeStd = 0.29,
        gyroSampleCount = 59_188,
    )
}
