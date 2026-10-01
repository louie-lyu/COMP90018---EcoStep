package com.ecostep.app.sensors

import com.ecostep.app.sensors.tracking.FeatureAccumulator
import com.ecostep.app.sensors.tracking.LocationSample
import com.ecostep.app.sensors.tracking.MotionSample
import com.ecostep.app.sensors.tracking.MotionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAccumulatorTest {
    private fun point(lat: Double, time: Long, accuracy: Float = 5f, speed: Float? = null) =
        LocationSample(lat, 144.0, accuracy, speed, time)

    @Test fun `rejects inaccurate and teleport points without changing endpoints`() {
        val acc = FeatureAccumulator()
        assertTrue(acc.onLocation(point(-37.0, 0)))
        assertFalse(acc.onLocation(point(-37.0001, 2000, accuracy = 31f)))
        assertFalse(acc.onLocation(point(-38.0, 4000)))
        assertEquals(1, acc.gpsCount)
        assertEquals(-37.0, acc.last!!.latitude, 0.0)
        assertEquals(0.0, acc.distanceMeters, 0.0)
    }

    @Test fun `small GPS drift does not add distance and speed distribution is summarized`() {
        val acc = FeatureAccumulator()
        assertTrue(acc.onLocation(point(-37.0, 0, speed = 0f)))
        assertTrue(acc.onLocation(point(-37.00001, 2000, speed = 0f)))
        assertTrue(acc.onLocation(point(-37.00002, 4000, speed = 2f)))
        val f = acc.toFeatures(4000)
        assertEquals(0.0, acc.distanceMeters, 0.0)
        assertEquals(2.0 / 3.0, f.stopRatio, 1e-9)
        assertEquals(0.0, f.p95SpeedMps, 0.0)
        assertEquals(2.0, f.maxSpeedMps, 0.0)
        assertEquals(3, f.gpsSampleCount)
    }

    @Test fun `missing gyroscope leaves its feature count zero`() {
        val acc = FeatureAccumulator()
        acc.onMotion(MotionSample(MotionType.ACCELEROMETER, 0f, 0f, 9.80665f))
        val f = acc.toFeatures(1000)
        assertEquals(1, f.accelSampleCount)
        assertEquals(0, f.gyroSampleCount)
        assertEquals(0.0, f.gyroMagnitudeMean, 0.0)
    }
}
