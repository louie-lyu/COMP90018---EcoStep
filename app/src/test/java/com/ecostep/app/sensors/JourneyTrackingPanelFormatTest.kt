package com.ecostep.app.sensors

import com.ecostep.app.sensors.ui.formatTrackingDistance
import com.ecostep.app.sensors.ui.formatTrackingDuration
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class JourneyTrackingPanelFormatTest {

    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `distance shows metres below 1 km and kilometres above`() {
        assertEquals("0 m", formatTrackingDistance(0.0))
        assertEquals("999 m", formatTrackingDistance(999.4))
        assertEquals("1.00 km", formatTrackingDistance(1_000.0))
        assertEquals("12.35 km", formatTrackingDistance(12_345.0))
    }

    @Test
    fun `duration shows mm ss and adds hours when needed`() {
        assertEquals("00:00", formatTrackingDuration(0))
        assertEquals("01:05", formatTrackingDuration(65))
        assertEquals("1:01:01", formatTrackingDuration(3_661))
        assertEquals("00:00", formatTrackingDuration(-5))
    }
}
