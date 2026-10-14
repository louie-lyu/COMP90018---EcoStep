package com.ecostep.app.evaluation

import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil

internal data class UiFrame(val startNs: Long, val durationNs: Long, val budgetNs: Long, val dropped: Int)

/** MainActivity window only; dialog/system windows are outside this collector's scope. */
internal class UiFrameCollector(private val window: Window) : AutoCloseable {
    private val thread = HandlerThread("evaluation-frames").apply { start() }
    private val handler = Handler(thread.looper)
    private val frames = mutableListOf<UiFrame>()
    @Suppress("DEPRECATION")
    val refreshHz = window.windowManager.defaultDisplay.refreshRate.toDouble()
    val budgetSource = if (Build.VERSION.SDK_INT >= 31) "FrameMetrics.DEADLINE" else "display_refresh_period_estimate"
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
        val budget = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE)
            else (1_000_000_000.0 / refreshHz).toLong()
        val frame = UiFrame(metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
            metrics.getMetric(FrameMetrics.TOTAL_DURATION), budget, dropped)
        synchronized(frames) { frames += frame }
    }

    init { window.addOnFrameMetricsAvailableListener(listener, handler) }

    fun between(startNs: Long, endNs: Long): JSONObject {
        // Allow the final rendered frame callback to arrive, outside the response timer.
        SystemClock.sleep(150)
        val barrier = CountDownLatch(1)
        handler.post { barrier.countDown() }
        val drained = barrier.await(2, TimeUnit.SECONDS)
        val snapshot = synchronized(frames) { frames.toList() }
        return summarizeFrames(snapshot, startNs, endNs).put("callbackQueueDrained", drained)
            .put("budgetSource", budgetSource).put("refreshHz", refreshHz)
    }

    override fun close() {
        window.removeOnFrameMetricsAvailableListener(listener)
        thread.quitSafely()
        thread.join(2_000)
    }
}

internal fun summarizeFrames(frames: List<UiFrame>, startNs: Long, endNs: Long): JSONObject {
    val selected = frames.filter { it.startNs >= 0 && it.durationNs >= 0 &&
        it.startNs <= endNs && it.startNs + it.durationNs >= startNs }
    val valid = selected.filter { it.budgetNs > 0 }
    val overBudget = valid.count { it.durationNs >= it.budgetNs }
    return JSONObject().put("frameCount", selected.size).put("framesWithBudget", valid.size)
        .put("overBudgetFrames", overBudget)
        .put("overBudgetRatio", if (valid.isEmpty()) JSONObject.NULL else overBudget.toDouble() / valid.size)
        .put("droppedCallbacks", selected.sumOf { it.dropped })
        .put("durationMs", percentiles(selected.map { it.durationNs / 1_000_000.0 }))
        .put("frames", JSONArray(selected.map { frame -> JSONObject()
            .put("relativeStartMs", (frame.startNs - startNs) / 1_000_000.0)
            .put("durationMs", frame.durationNs / 1_000_000.0)
            .put("budgetMs", if (frame.budgetNs > 0) frame.budgetNs / 1_000_000.0 else JSONObject.NULL)
            .put("droppedCallbacks", frame.dropped) }))
}

internal fun percentiles(values: List<Double>): JSONObject {
    val sorted = values.sorted()
    fun rank(p: Double): Any = if (sorted.isEmpty()) JSONObject.NULL else sorted[ceil(sorted.size * p).toInt() - 1]
    return JSONObject().put("count", sorted.size).put("p50", rank(.5)).put("p95", rank(.95))
        .put("max", sorted.lastOrNull() ?: JSONObject.NULL)
}
