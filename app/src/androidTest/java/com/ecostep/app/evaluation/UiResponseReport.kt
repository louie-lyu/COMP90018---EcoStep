package com.ecostep.app.evaluation

import android.os.Build
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

internal val responseScenarios = listOf("route_query", "start_journey", "stop_to_review",
    "confirm_to_feedback", "mission_suggestion", "weekly_insight")

internal class UiResponseReport(args: Bundle, val samples: Int) {
    val records = mutableListOf<JSONObject>()
    val root = JSONObject().put("schemaVersion", 2).put("passed", false)
        .put("functionalPassed", false)
        .put("startedAtMillis", System.currentTimeMillis()).put("samples", samples)
        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("api", Build.VERSION.SDK_INT)
        .put("buildType", "debug").put("pollIntervalMs", 20)
        .put("gitCommit", args.getString("gitCommit", "unknown"))
        .put("workingTreeDirty", args.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
        .put("frameScope", "MainActivity window; excludes dialog, keyboard, system and SurfaceView/map rendering")
        .put("cachePolicy", "Production caches retained; first sample and subsequent samples reported separately")
        .put("sensorInput", "Synthetic 60-second drive; not a GPS acquisition or sensor-accuracy test")
        .put("timing", "Monotonic input submission to observed UI semantics; includes polling overhead, not physical display latency")
        .put("missionTiming", "Starts at the same Confirm click; includes saved-dialog and navigation to Missions")

    fun save() = writeDeviceReport("ui-responsiveness", toJson(), preserveSourceMetadata = true)

    fun toJson(): JSONObject {
        root.put("records", JSONArray(records))
        root.put("summaries", JSONArray(responseScenarios.map { scenario ->
            val attempted = records.filter { it.getString("scenario") == scenario }
            val success = attempted.filter { it.getString("outcome") == "SUCCESS" }
            JSONObject().put("scenario", scenario).put("planned", samples).put("attempted", attempted.size)
                .put("success", success.size).put("timeouts", attempted.count { it.getString("outcome") == "TIMEOUT" })
                .put("failed", attempted.count { it.getString("outcome") == "FAILURE" })
                .put("skipped", samples - attempted.size)
                .put("latencyMs", percentiles(success.map { it.getDouble("durationMs") }))
                .put("firstSampleMs", success.firstOrNull { it.getInt("sample") == 1 }?.getDouble("durationMs") ?: JSONObject.NULL)
                .put("subsequentLatencyMs", percentiles(success.filter { it.getInt("sample") > 1 }.map { it.getDouble("durationMs") }))
        }))
        return root
    }

    fun measure(sample: Int, scenario: String, startNs: Long = System.nanoTime(),
        action: () -> Unit): JSONObject {
        val row = JSONObject().put("sample", sample).put("scenario", scenario).put("startedNs", startNs)
        try {
            action()
            row.put("outcome", "SUCCESS")
        } catch (error: Throwable) {
            row.put("outcome", if (error is UiResponseTimeout) "TIMEOUT" else "FAILURE")
                .put("errorType", error.javaClass.simpleName)
            throw error
        } finally {
            val endNs = System.nanoTime()
            val duration = (endNs - startNs) / 1_000_000.0
            row.put("durationMs", duration).put("endedNs", endNs)
            records += row
            android.util.Log.i("UiEvaluation", "$sample $scenario: ${row.getString("outcome")} ($duration ms)")
        }
        return row
    }

    /** Collect after all response timers stop, so callback settling and export cannot inflate them. */
    fun captureFrames(frames: UiFrameCollector) {
        records.filter { !it.has("frames") }.forEach { row ->
            val frameReport = frames.between(row.getLong("startedNs"), row.getLong("endedNs"))
            val complete = frameReport.getInt("framesWithBudget") > 0 &&
                frameReport.getInt("framesWithBudget") == frameReport.getInt("frameCount") &&
                frameReport.getBoolean("callbackQueueDrained") && frameReport.getInt("droppedCallbacks") == 0
            row.put("frames", frameReport).put("frameEvidenceComplete", complete)
        }
    }

    fun finish() {
        val functional = records.size == samples * responseScenarios.size && records.all { it.getString("outcome") == "SUCCESS" }
        // Success means the flows completed; timing and frame metrics have no pass/fail targets.
        root.put("functionalPassed", functional).put("passed", functional)
    }
}
