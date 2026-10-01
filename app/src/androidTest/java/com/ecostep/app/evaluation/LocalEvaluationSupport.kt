package com.ecostep.app.evaluation

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue

/** Shared reporting and batch timing for local evaluations; no network or login. */
internal class LocalEvaluationRun(
    private val name: String,
    scope: String,
    private val plannedCases: Int,
    private val plannedBenchmarkGroups: Int,
) {
    private val startedAt = System.currentTimeMillis()
    private val arguments = InstrumentationRegistry.getArguments()
    private val cases = mutableListOf<JSONObject>()
    private val benchmarks = mutableListOf<JSONObject>()
    private val samples = argument("samples", 50, 1..1000)
    private val warmups = argument("warmups", 10, 1..100)
    private val batchSize = argument("batchSize", 10, 1..1000)
    private val report = JSONObject().apply {
        put("schemaVersion", 1)
        put("datasetVersion", 1)
        put("startedAtMillis", startedAt)
        put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
        put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("androidVersion", Build.VERSION.RELEASE)
        put("apiLevel", Build.VERSION.SDK_INT)
        put("buildType", BuildConfig.BUILD_TYPE)
        put("scope", scope)
        put("benchmarkMethod", "Sequential groups; System.nanoTime around batches. Local calls, coroutine/loop overhead and result-list writes included. Fixtures, warm-ups, verification and JSON excluded. Nearest-rank P50/P95 use successful batches. Per-call values are batch averages, not individual-call tail latency. No performance threshold; emulator JIT/GC and group order affect timings.")
    }

    suspend fun evaluate(block: suspend LocalEvaluationRun.() -> Unit) {
        var completed = false
        try {
            block()
            completed = true
        } catch (error: Exception) {
            report.put("runError", errorJson(error))
            throw error
        } finally {
            val passedCases = cases.count { it.getBoolean("passed") }
            report.put("plannedCases", plannedCases)
            report.put("totalCases", cases.size)
            report.put("passedCases", passedCases)
            report.put("failedCases", cases.size - passedCases)
            report.put("records", JSONArray(cases))
            report.put("plannedBenchmarkGroups", plannedBenchmarkGroups)
            report.put("benchmarks", JSONArray(benchmarks))
            report.put("passed", completed && cases.size == plannedCases && passedCases == plannedCases &&
                benchmarks.size == plannedBenchmarkGroups && benchmarks.all { it.getBoolean("passed") })
            report.put("finishedAtMillis", System.currentTimeMillis())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.filesDir, "evaluation")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create evaluation directory" }
            File(directory, "$name-evaluation-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)
        }
        assertTrue("$name evaluation failed; inspect the JSON report", report.getBoolean("passed"))
    }

    suspend fun case(name: String, expected: JSONObject, block: suspend (JSONObject) -> Unit) {
        val actual = JSONObject()
        val record = JSONObject().put("scenario", name).put("expected", expected).put("actual", actual).put("passed", false)
        try {
            block(actual)
            record.put("passed", true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            record.put("passed", false).put("error", errorJson(error))
        } finally {
            cases.add(record)
        }
    }

    suspend fun <T> benchmark(name: String, inputSize: Int,
        operation: suspend () -> T, verify: (T) -> Unit) {
        val results = ArrayList<T>(batchSize)
        val records = mutableListOf<JSONObject>()
        val times = mutableListOf<Double>()
        val group = JSONObject().put("scenario", name).put("inputSize", inputSize)
            .put("plannedSamples", samples).put("warmupBatches", warmups).put("batchSize", batchSize)
        suspend fun batch() {
            results.clear()
            repeat(batchSize) { results.add(operation()) }
        }
        var warmupPassed = false
        try {
            repeat(warmups) { batch(); results.forEach(verify) }
            warmupPassed = true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            group.put("warmupError", errorJson(error))
        }
        if (warmupPassed) repeat(samples) { index ->
            val record = JSONObject().put("sample", index + 1).put("passed", false)
            val start = System.nanoTime()
            var durationMs = 0.0
            try {
                try {
                    batch()
                } finally {
                    durationMs = (System.nanoTime() - start) / 1_000_000.0
                }
                results.forEach(verify)
                times.add(durationMs)
                record.put("passed", true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                record.put("error", errorJson(error))
            }
            record.put("batchDurationMs", durationMs).put("averagePerCallUs", durationMs * 1000 / batchSize)
            records.add(record)
        }
        val sorted = times.sorted()
        fun percentile(fraction: Double): Double? = sorted.takeIf { it.isNotEmpty() }
            ?.get(ceil(sorted.size * fraction).toInt() - 1)
        group.put("warmupPassed", warmupPassed)
            .put("recordedSamples", records.size).put("successfulSamples", times.size)
            .put("failedSamples", records.size - times.size).put("skippedSamples", samples - records.size)
            .put("p50BatchMs", percentile(0.50) ?: JSONObject.NULL)
            .put("p95BatchMs", percentile(0.95) ?: JSONObject.NULL)
            .put("p50AveragePerCallUs", percentile(0.50)?.let { it * 1000 / batchSize } ?: JSONObject.NULL)
            .put("p95AveragePerCallUs", percentile(0.95)?.let { it * 1000 / batchSize } ?: JSONObject.NULL)
            .put("passed", warmupPassed && times.size == samples).put("records", JSONArray(records))
        benchmarks.add(group)
    }

    private fun argument(name: String, default: Int, range: IntRange): Int {
        val value = arguments.getString(name)?.toInt() ?: default
        require(value in range) { "$name must be in $range" }
        return value
    }

    private fun errorJson(error: Exception) = JSONObject()
        .put("type", error.javaClass.simpleName).put("message", error.message ?: JSONObject.NULL)
}

internal fun expectIllegalArgument(block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalArgumentException) {
        return
    }
    error("Expected IllegalArgumentException")
}
