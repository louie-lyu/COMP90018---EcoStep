package com.ecostep.app.sensors

import com.ecostep.app.sensors.tracking.FeatureAccumulator
import com.ecostep.app.sensors.tracking.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Test

class JourneyTraceRetentionTest {

    @Test
    fun keepsAboutOnePointEveryFiveSeconds() {
        val accumulator = FeatureAccumulator()

        listOf(0L, 2_000L, 5_000L, 11_000L).forEach { time ->
            accumulator.onLocation(sample(time))
        }

        assertEquals(
            listOf(0L, 5_000L, 11_000L),
            accumulator.snapshotTrace().map { it.timeMillis },
        )
    }

    @Test
    fun keepsAtMost720RecentPoints() {
        val accumulator = FeatureAccumulator()

        for (index in 0..730) {
            accumulator.onLocation(sample(index * 5_000L))
        }

        assertEquals(720, accumulator.snapshotTrace().size)
    }

    private fun sample(time: Long) = LocationSample(
        latitude = -37.8136,
        longitude = 144.9631,
        accuracyMeters = 5f,
        speedMps = 0f,
        timeMillis = time,
    )
}