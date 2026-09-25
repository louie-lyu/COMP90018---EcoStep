package com.ecostep.app.evaluation

import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class LatencyTrackerTest {
    @Test
    fun `measures elapsed time and preserves return value`() = runTest {
        var now = 0L
        val tracker = LatencyTracker(nanoTime = { now })
        val result = tracker.measure("weather.fetch") {
            now += 12_500_000L
            "weather"
        }
        assertEquals("weather", result)
        assertEquals(12.5, tracker.snapshot().single().durationMs, 0.0)
        assertEquals(Outcome.SUCCESS, tracker.snapshot().single().outcome)
    }

    @Test
    fun `records failure cancellation and timeout without swallowing exceptions`() = runTest {
        val tracker = LatencyTracker()
        val errors = listOf(IllegalStateException("failure"), CancellationException("cancel"))
        for (expected in errors) {
            try {
                tracker.measure("request") { throw expected }
                fail("Expected exception")
            } catch (actual: Exception) {
                assertSame(expected, actual)
            }
        }
        try {
            tracker.measure("request") { withTimeout(1) { delay(10) } }
            fail("Expected timeout")
        } catch (_: CancellationException) {
            // The timeout must reach the caller as well as the evaluation record.
        }
        assertEquals(
            listOf(Outcome.FAILURE, Outcome.CANCELLED, Outcome.TIMEOUT),
            tracker.snapshot().map { it.outcome },
        )
    }

    @Test
    fun `overlapping operations keep independent timings and finish only once`() {
        var now = 0L
        val tracker = LatencyTracker(nanoTime = { now })
        val first = tracker.start("save")
        now = 1_000_000L
        val second = tracker.start("save")
        now = 3_000_000L
        assertTrue(second.finish())
        now = 5_000_000L
        assertTrue(first.finish())
        assertFalse(first.finish())
        assertEquals(listOf(2.0, 5.0), tracker.snapshot().map { it.durationMs })
    }

    @Test
    fun `concurrent callbacks cannot duplicate a record`() {
        val tracker = LatencyTracker()
        val measurement = tracker.start("save")
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = (1..20).map { executor.submit<Boolean> { measurement.finish() } }
            assertEquals(1, results.count { it.get() })
            assertEquals(1, tracker.snapshot().size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `percentiles separate outcomes and snapshots survive clear`() {
        var now = 0L
        val tracker = LatencyTracker(nanoTime = { now })
        assertTrue(tracker.summaries().isEmpty())
        for (milliseconds in 1..20) {
            val measurement = tracker.start("fetch")
            now += milliseconds * 1_000_000L
            measurement.finish()
        }
        tracker.start("fetch").finish(Outcome.FAILURE)
        val summary = tracker.summaries().first { it.outcome == Outcome.SUCCESS }
        assertEquals(20, summary.count)
        assertEquals(10.0, summary.p50Ms, 0.0)
        assertEquals(19.0, summary.p95Ms, 0.0)
        assertEquals(2, tracker.summaries().size)
        val snapshot = tracker.snapshot()
        tracker.clear()
        assertEquals(21, snapshot.size)
        assertTrue(tracker.snapshot().isEmpty())
    }

    @Test
    fun `csv escapes commas quotes and newlines`() {
        val tracker = LatencyTracker(context = "device,\"wifi\"\nversion", nanoTime = { 0L })
        tracker.start("fetch").finish()
        assertEquals(
            "context,scenario,duration_ms,outcome,error_type\n" +
                "\"device,\"\"wifi\"\"\nversion\",\"fetch\",\"0.0\",\"SUCCESS\",\"\"\n",
            tracker.toCsv(),
        )
    }
}
