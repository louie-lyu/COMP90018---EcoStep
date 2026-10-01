package com.ecostep.app.sensors

import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.sensors.tracking.JourneySummaryBuilder
import com.ecostep.app.sensors.tracking.JourneyTracker
import com.ecostep.app.sensors.tracking.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JourneySummaryBuilderTest {
    @Test fun `short journey is not saved`() {
        val tracker = JourneyTracker()
        tracker.begin(1000)
        tracker.onLocation(LocationSample(-37.0, 144.0, 5f, 0f, 1000))
        tracker.onLocation(LocationSample(-37.0001, 144.0, 5f, 1f, 3000))
        assertNull(tracker.finish(20_000))
    }

    @Test fun `summary uses valid endpoints and contains aggregated features`() {
        val tracker = JourneyTracker()
        tracker.begin(1000)
        tracker.onLocation(LocationSample(-37.0, 144.0, 5f, 0f, 1000))
        tracker.onLocation(LocationSample(-37.0001, 144.0, 5f, 1f, 3000))
        val result = tracker.finish(32_000)!!
        val summary = JourneySummaryBuilder().build(result, "user-1")
        assertEquals(-37.0, summary.startLocation.latitude, 0.0)
        assertEquals(-37.0001, summary.endLocation.latitude, 0.0)
        assertEquals(TransportMode.UNKNOWN, summary.transportMode)
        assertNotNull(summary.sensorFeatures)
        assertEquals(2, summary.sensorFeatures!!.gpsSampleCount)
    }
}
