package com.ecostep.app.evaluation

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Offline checks: preserve measurements and failures without grading performance. */
@RunWith(AndroidJUnit4::class)
class UiResponseMetricsTest {
    @Test fun noFramesIsUnknownRatherThanSmooth() {
        val report = summarizeFrames(emptyList(), 0, 1_000_000_000)
        assertEquals(0, report.getInt("frameCount"))
        assertTrue(report.isNull("overBudgetRatio"))
        assertTrue(report.getJSONObject("durationMs").isNull("p95"))
    }

    @Test fun onlyOverlappingFramesCountAndMissedCallbacksRemainVisible() {
        val frames = listOf(UiFrame(0, 5, 10, 0), UiFrame(90, 20, 16, 2),
            UiFrame(120, 8, 16, 0), UiFrame(201, 20, 16, 0))
        val report = summarizeFrames(frames, 100, 200)
        assertEquals(2, report.getInt("frameCount"))
        assertEquals(1, report.getInt("overBudgetFrames"))
        assertEquals(.5, report.getDouble("overBudgetRatio"), 0.0)
        assertEquals(2, report.getInt("droppedCallbacks"))
    }

    @Test fun frameDeadlineAndMissingBudgetRemainVisible() {
        val report = summarizeFrames(listOf(UiFrame(0, 20_000_000, 20_000_000, 0),
            UiFrame(1_000_000_000, 40_000_000, -1, 0)), 0, 2_000_000_000)
        assertFalse(report.has("frozenFrames"))
        assertEquals(1, report.getInt("framesWithBudget"))
        assertEquals(1, report.getInt("overBudgetFrames"))
    }

    @Test fun tailsUseNearestRankWithoutAveragingOutliers() {
        val report = percentiles((1..19).map(Int::toDouble) + 900.0)
        assertEquals(10.0, report.getDouble("p50"), 0.0)
        assertEquals(19.0, report.getDouble("p95"), 0.0)
        assertEquals(900.0, report.getDouble("max"), 0.0)
    }

    @Test fun slowSuccessfulFlowsRemainSuccessfulWithoutPerformanceVerdicts() {
        val report = UiResponseReport(Bundle(), 1)
        responseScenarios.forEach { scenario ->
            report.measure(1, scenario, System.nanoTime() - 60_000_000_000L) { }
        }
        report.finish()
        val json = report.toJson()
        assertTrue(json.getBoolean("passed"))
        assertFalse(json.has("performancePassed"))
        assertFalse(json.has("assertPerformance"))
        assertFalse(json.has("jankBudgetRatio"))
        report.records.forEach { row ->
            assertTrue(row.getDouble("durationMs") >= 60_000)
            assertFalse(row.has("budgetMs"))
            assertFalse(row.has("withinLatencyBudget"))
            assertFalse(row.has("withinFrameBudget"))
        }
        val summaries = json.getJSONArray("summaries")
        repeat(summaries.length()) { index ->
            val summary = summaries.getJSONObject(index)
            assertFalse(summary.has("budgetMs"))
            assertEquals(1, summary.getInt("success"))
        }
    }

    @Test fun timeoutIsKeptAndIncompleteRunFails() {
        val report = UiResponseReport(Bundle(), 1)
        try { report.measure(1, "route_query") { throw UiResponseTimeout("fixture") } }
        catch (_: UiResponseTimeout) { }
        report.finish()
        assertEquals("TIMEOUT", report.records.single().getString("outcome"))
        assertFalse(report.root.getBoolean("functionalPassed"))
        assertFalse(report.root.getBoolean("passed"))
    }
}
