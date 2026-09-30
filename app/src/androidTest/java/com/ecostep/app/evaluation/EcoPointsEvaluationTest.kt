package com.ecostep.app.evaluation

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: independent rule checks and amortized emulator timings. */
@RunWith(AndroidJUnit4::class)
class EcoPointsEvaluationTest {
    private val calculator = DefaultEcoPointsCalculator()
    private val carbonCalculator = DefaultCarbonCalculator()

    @Test
    fun evaluateEcoPoints() {
        val startedAt = System.currentTimeMillis()
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String, default: Int, range: IntRange): Int {
            val value = arguments.getString(name)?.toInt() ?: default
            require(value in range) { "$name must be in $range" }
            return value
        }
        val samples = argument("samples", 50, 1..1000)
        val warmups = argument("warmups", 10, 1..100)
        val batchSize = argument("batchSize", 1000, 1..10000)
        val cases = EcoPointsEvaluationCases.cases()
        val records = mutableListOf<JSONObject>()
        val benchmarks = mutableListOf<JSONObject>()
        val groups = linkedMapOf<String, List<EcoPointsCase>>()
        for (mode in TransportMode.entries) {
            groups["normal_${mode.name}"] = cases.filter {
                it.input.actualTransportMode == mode && it.group in listOf("reference", "rounding")
            }
        }
        groups["early_return"] = cases.filter { it.group in listOf("state", "missing", "zero") && it.expectedPoints == 0 }
        groups["cap"] = cases.filter { it.group == "cap" }
        groups["carbon_to_points"] = cases.filter { it.group == "carbon_to_points" }
        val report = JSONObject().apply {
            put("schemaVersion", 1)
            put("datasetVersion", 2)
            put("calculator", "DefaultEcoPointsCalculator")
            put("startedAtMillis", startedAt)
            put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
            put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("apiLevel", Build.VERSION.SDK_INT)
            put("buildType", BuildConfig.BUILD_TYPE)
            put("scope", "Synthetic correctness and amortized call timings; no sensors, network, account balance or UI integration.")
            put("rules", "Accepted and completed; round(savingsGrams / 10) plus WALKING=20, CYCLING=15, PUBLIC_TRANSPORT=5, others=0; cap=500. Missing data or zero savings returns zero.")
            put("carbonFactorsGramsPerKm", "Provisional: CAR=192, PUBLIC_TRANSPORT=89, WALKING=0, CYCLING=0")
            put("benchmarkMethod", "Sequential groups; System.nanoTime around batches. Includes case selection, loop and result-array writes; carbon_to_points also includes carbon calculation and MissionResult copying. Fixtures, warm-ups, validation and JSON excluded. Nearest-rank P50/P95 use successful batches only; per-call values are batch averages, not individual-call tail latency. JIT/GC and group order can affect comparisons. No latency pass/fail threshold.")
        }
        var completed = false
        try {
            cases.forEach { records.add(evaluateCase(it)) }
            for (mode in TransportMode.entries) {
                records.add(evaluateConsistency(mode.name, cases.filter {
                    it.input.actualTransportMode == mode && it.group in listOf("reference", "rounding", "zero", "cap")
                }))
            }
            groups.forEach { (name, inputs) -> benchmarks.add(benchmark(name, inputs, samples, warmups, batchSize)) }
            completed = true
        } catch (error: Exception) {
            report.put("runError", errorJson(error))
            throw error
        } finally {
            val passedCases = records.count { it.getBoolean("passed") }
            report.put("plannedCases", cases.size + TransportMode.entries.size)
            report.put("totalCases", records.size)
            report.put("passedCases", passedCases)
            report.put("failedCases", records.size - passedCases)
            report.put("records", JSONArray(records))
            report.put("plannedBenchmarkGroups", groups.size)
            report.put("benchmarks", JSONArray(benchmarks))
            report.put("passed", completed && passedCases == cases.size + TransportMode.entries.size &&
                benchmarks.all { it.getBoolean("passed") })
            report.put("finishedAtMillis", System.currentTimeMillis())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.filesDir, "evaluation")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create evaluation directory" }
            File(directory, "ecopoints-evaluation-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)
        }
        assertTrue("EcoPoints evaluation failed; inspect the JSON report", report.getBoolean("passed"))
    }

    private fun mission(case: EcoPointsCase): MissionResult {
        val journey = case.journey ?: return case.input
        val savings = carbonCalculator.calculate(journey).lowerCarbonAlternatives
            .single { it.mode == case.input.actualTransportMode }.savingsGrams
        return case.input.copy(actualCarbonSavingGrams = savings)
    }

    private fun evaluateCase(case: EcoPointsCase): JSONObject = JSONObject().apply {
        put("scenario", case.scenario)
        put("group", case.group)
        put("input", inputJson(case.input))
        put("distanceMeters", case.journey?.distanceMeters ?: JSONObject.NULL)
        put("expectedPoints", case.expectedPoints ?: JSONObject.NULL)
        put("expectedError", case.expectedError ?: JSONObject.NULL)
        put("passed", false)
        try {
            val input = mission(case)
            put("actualInput", inputJson(input))
            if (case.journey != null) {
                check(abs(input.actualCarbonSavingGrams!! - case.input.actualCarbonSavingGrams!!) <= 1e-9) {
                    "Carbon savings differ from the hand-calculated reference"
                }
            }
            val actual = calculator.calculatePoints(input)
            put("actualPoints", actual)
            put("passed", case.expectedError == null && actual == case.expectedPoints)
        } catch (error: Exception) {
            put("error", errorJson(error))
            put("passed", error.javaClass.simpleName == case.expectedError)
        }
    }

    private fun evaluateConsistency(mode: String, cases: List<EcoPointsCase>): JSONObject = JSONObject().apply {
        put("scenario", "consistency_$mode")
        put("expected", "Repeatable, nondecreasing points in [0, 500] as savings increase")
        put("passed", false)
        val values = JSONArray()
        try {
            var previous = 0
            for (case in cases.sortedBy { it.input.actualCarbonSavingGrams }) {
                val points = calculator.calculatePoints(case.input)
                values.put(JSONObject().put("savingsGrams", number(case.input.actualCarbonSavingGrams)).put("points", points))
                check(points in previous..500) { "Points decreased or exceeded bounds" }
                check(points == calculator.calculatePoints(case.input)) { "Repeated calculation changed points" }
                previous = points
            }
            put("passed", true)
        } catch (error: Exception) {
            put("error", errorJson(error))
        }
        put("values", values)
    }

    private fun benchmark(name: String, cases: List<EcoPointsCase>, samples: Int, warmups: Int, batchSize: Int): JSONObject {
        val results = IntArray(batchSize)
        val expected = IntArray(batchSize) { cases[it % cases.size].expectedPoints!! }
        val records = mutableListOf<JSONObject>()
        val times = mutableListOf<Double>()
        fun batch() {
            for (index in results.indices) {
                results[index] = calculator.calculatePoints(mission(cases[index % cases.size]))
            }
        }
        fun validate() = check(results.contentEquals(expected)) { "Benchmark output differs from expected points" }
        var warmupPassed = false
        val report = JSONObject().put("scenario", name).put("plannedSamples", samples)
            .put("warmupBatches", warmups).put("batchSize", batchSize)
            .put("inputScenarios", JSONArray(cases.map { it.scenario }))
        try {
            repeat(warmups) { batch(); validate() }
            warmupPassed = true
        } catch (error: Exception) {
            report.put("warmupError", errorJson(error))
        }
        if (warmupPassed) repeat(samples) { index ->
            val record = JSONObject().put("sample", index + 1).put("passed", false)
            var durationMs = 0.0
            try {
                val start = System.nanoTime()
                try {
                    batch()
                } finally {
                    durationMs = (System.nanoTime() - start) / 1_000_000.0
                }
                validate()
                times.add(durationMs)
                record.put("passed", true)
            } catch (error: Exception) {
                record.put("error", errorJson(error))
            }
            record.put("batchDurationMs", durationMs)
            record.put("averagePerCallUs", durationMs * 1000 / batchSize)
            records.add(record)
        }
        val sorted = times.sorted()
        fun percentile(fraction: Double): Double? = sorted.takeIf { it.isNotEmpty() }
            ?.get(ceil(sorted.size * fraction).toInt() - 1)
        return report.apply {
            put("warmupPassed", warmupPassed)
            put("recordedSamples", records.size)
            put("successfulSamples", times.size)
            put("failedSamples", records.size - times.size)
            put("skippedSamples", samples - records.size)
            put("p50BatchMs", percentile(0.50) ?: JSONObject.NULL)
            put("p95BatchMs", percentile(0.95) ?: JSONObject.NULL)
            put("p50AveragePerCallUs", percentile(0.50)?.let { it * 1000 / batchSize } ?: JSONObject.NULL)
            put("p95AveragePerCallUs", percentile(0.95)?.let { it * 1000 / batchSize } ?: JSONObject.NULL)
            put("passed", warmupPassed && times.size == samples)
            put("records", JSONArray(records))
        }
    }

    private fun inputJson(input: MissionResult) = JSONObject().apply {
        put("accepted", input.accepted)
        put("completed", input.completed)
        put("actualTransportMode", input.actualTransportMode?.name ?: JSONObject.NULL)
        put("actualCarbonSavingGrams", number(input.actualCarbonSavingGrams))
    }

    private fun number(value: Double?): Any = when {
        value == null -> JSONObject.NULL
        value.isFinite() -> value
        else -> value.toString()
    }

    private fun errorJson(error: Exception) = JSONObject().apply {
        put("type", error.javaClass.simpleName)
        put("message", error.message ?: JSONObject.NULL)
    }
}
