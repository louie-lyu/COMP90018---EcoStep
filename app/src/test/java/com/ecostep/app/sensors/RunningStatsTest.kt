package com.ecostep.app.sensors

import com.ecostep.app.sensors.tracking.RunningStats
import org.junit.Assert.assertEquals
import org.junit.Test

class RunningStatsTest {
    @Test fun `Welford computes population mean and standard deviation`() {
        val stats = RunningStats()
        listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0).forEach(stats::add)
        assertEquals(8L, stats.count)
        assertEquals(5.0, stats.mean(), 1e-9)
        assertEquals(2.0, stats.std(), 1e-9)
    }

    @Test fun `empty input is zero`() {
        val stats = RunningStats()
        assertEquals(0.0, stats.mean(), 0.0)
        assertEquals(0.0, stats.std(), 0.0)
    }
}
