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

    @Test fun `slow walking with frequent fixes still accumulates distance`() {
        val acc = FeatureAccumulator()
        // About 1.3 m between fixes, one per second, for 60 s: each step is under the 3 m
        // jitter threshold, but the walk covers roughly 80 m.
        for (second in 0..60) {
            assertTrue(acc.onLocation(point(-37.0 - second * 0.0000117, second * 1000L)))
        }
        assertTrue(acc.distanceMeters in 75.0..82.0)
    }

    @Test fun `a sustained GPS jump moves the track without adding its distance`() {
        val acc = FeatureAccumulator()
        assertTrue(acc.onLocation(point(-37.0, 0)))
        // Jump about 600 m (e.g. a restarted simulated route), then keep walking from there.
        assertFalse(acc.onLocation(point(-37.0054, 1_000)))
        for (second in 2..20) {
            acc.onLocation(point(-37.0054 - (second - 1) * 0.0000117, second * 1_000L))
        }
        // Only the ~25 m walked after the jump counts, never the 600 m jump itself.
        assertTrue(acc.distanceMeters in 20.0..30.0)
        // Nothing was travelled before the jump, so the old fix was a wrong position: the
        // journey starts where walking actually began and the jump is not drawn.
        assertEquals(-37.0054, acc.first!!.latitude, 0.00002)
        assertTrue(acc.displayPath().none { it.latitude == -37.0 })
    }

    @Test fun `a jump after real movement keeps the original start`() {
        val acc = FeatureAccumulator()
        for (second in 0..20) {
            acc.onLocation(point(-37.0 - second * 0.0000117, second * 1_000L))
        }
        val walkedBeforeJump = acc.distanceMeters
        assertFalse(acc.onLocation(point(-37.0054, 21_000)))
        acc.onLocation(point(-37.0054117, 22_000))

        assertEquals(-37.0, acc.first!!.latitude, 0.0)
        assertEquals(walkedBeforeJump, acc.distanceMeters, 0.5)
    }

    @Test fun `a single GPS outlier is dropped and walking continues from the real track`() {
        val acc = FeatureAccumulator()
        assertTrue(acc.onLocation(point(-37.0, 0)))
        assertFalse(acc.onLocation(point(-37.01, 1_000)))
        assertTrue(acc.onLocation(point(-37.0000234, 2_000)))
        assertEquals(-37.0000234, acc.last!!.latitude, 0.0)
        assertTrue(acc.distanceMeters < 5.0)
    }

    @Test fun `display path keeps a point every five seconds plus the latest fix`() {
        val acc = FeatureAccumulator()
        for (second in 0..12) {
            acc.onLocation(point(-37.0 - second * 0.0000117, second * 1_000L))
        }
        val path = acc.displayPath()
        // Retained at 0 s, 5 s and 10 s, then the latest fix at 12 s.
        assertEquals(4, path.size)
        assertEquals(-37.0, path.first().latitude, 0.0)
        assertEquals(acc.last!!.latitude, path.last().latitude, 0.0)
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
