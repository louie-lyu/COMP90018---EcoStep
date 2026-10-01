package com.ecostep.app.evaluation

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.algorithm.DefaultRecurringJourneyDetector
import com.ecostep.app.algorithm.RecurringJourneyPattern
import com.ecostep.app.data.model.TransportMode
import java.io.File
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: synthetic rule validation and device timing, not real-world detection accuracy. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RecurringJourneyEvaluationTest {
    private val detector = DefaultRecurringJourneyDetector(zoneId = RecurringJourneyEvaluationCases.zone)

    @Test
    fun evaluateRecurringJourneys() {
        val startedAt = System.currentTimeMillis()
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String, default: Int, range: IntRange): Int {
            val value = arguments.getString(name)?.toInt() ?: default
            require(value in range) { "$name must be in $range" }
            return value
        }
        val samples = argument("samples", 50, 1..1000)
        val warmups = argument("warmups", 10, 1..100)
        val cases = RecurringJourneyEvaluationCases.correctnessCases()
        val records = mutableListOf<JSONObject>()
        val benchmarks = mutableListOf<JSONObject>()
        val report = JSONObject().apply {
            put("schemaVersion", 1)
            put("datasetVersion", 1)
            put("detector", "DefaultRecurringJourneyDetector")
            put("startedAtMillis", startedAt)
            put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
            put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("apiLevel", Build.VERSION.SDK_INT)
            put("buildType", BuildConfig.BUILD_TYPE)
            put("timeZone", RecurringJourneyEvaluationCases.zone.id)
            put("scope", "Synthetic single-user rule validation with manually specified expected patterns; not real-world accuracy.")
            put("rules", JSONObject().apply {
                put("locationToleranceMeters", 200)
                put("departureToleranceMinutes", 60)
                put("minimumOccurrences", 3)
                put("referenceCountsAsOccurrence", true)
                put("duplicatePolicy", "Identical copies of a journey ID count once")
            })
            put("benchmarkMethod", "Sequential calls using System.nanoTime; warm-ups, fixture generation, assertions and JSON writes excluded. Allocation and GC inside detect are included. Nearest-rank percentiles use successful, validated samples only.")
        }
        var completed = false
        try {
            cases.forEach { records.add(evaluateCase(it)) }
            for (size in listOf(10, 100, 1000, 10000)) {
                for (allMatching in listOf(true, false)) {
                    benchmarks.add(benchmark(size, allMatching, samples, warmups))
                }
            }
            completed = true
        } catch (error: Exception) {
            report.put("runError", errorJson(error))
            throw error
        } finally {
            val passedCases = records.count { it.getBoolean("passed") }
            report.put("plannedCases", cases.size)
            report.put("totalCases", records.size)
            report.put("passedCases", passedCases)
            report.put("failedCases", records.size - passedCases)
            report.put("casePassRate", ratio(passedCases, records.size))
            report.put("classification", classificationSummary(records))
            report.put("records", JSONArray(records))
            report.put("plannedBenchmarkGroups", 8)
            report.put("benchmarks", JSONArray(benchmarks))
            report.put("passed", completed && passedCases == cases.size && benchmarks.all { it.getBoolean("passed") })
            report.put("finishedAtMillis", System.currentTimeMillis())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.filesDir, "evaluation")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create evaluation directory" }
            File(directory, "recurring-evaluation-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)
        }
        assertTrue("Recurring journey evaluation failed; inspect the JSON report", report.getBoolean("passed"))
    }

    private fun evaluateCase(case: RecurringJourneyCase): JSONObject = JSONObject().apply {
        put("scenario", case.name)
        put("description", case.description)
        put("historySize", case.history.size)
        put("expectedDetected", case.expected != null)
        put("expected", patternJson(case.expected))
        put("passed", false)
        try {
            val actual = detector.detect(case.reference, case.history)
            put("actualDetected", actual != null)
            put("actual", patternJson(actual))
            // Data-class equality checks every output field, including the active-day set.
            put("passed", actual == case.expected)
        } catch (error: Exception) {
            put("actualDetected", JSONObject.NULL)
            put("error", errorJson(error))
        }
    }

    private fun benchmark(size: Int, allMatching: Boolean, samples: Int, warmups: Int): JSONObject {
        val reference = RecurringJourneyEvaluationCases.journey("benchmark_reference")
        val history = RecurringJourneyEvaluationCases.benchmarkHistory(size, allMatching)
        val expectedCount = 1 + if (allMatching) size else size / 10
        val records = mutableListOf<JSONObject>()
        val successfulTimes = mutableListOf<Double>()
        val report = JSONObject().apply {
            put("historySize", size)
            put("distribution", if (allMatching) "all_matching" else "ten_percent_matching")
            put("expectedOccurrences", expectedCount)
            put("plannedSamples", samples)
            put("warmupIterations", warmups)
        }
        fun validate(pattern: RecurringJourneyPattern?) {
            // Ten-percent matching with a history of ten has only two occurrences, including reference.
            if (expectedCount < 3) {
                check(pattern == null) { "Expected no recurring pattern" }
            } else {
                checkNotNull(pattern) { "Expected a recurring pattern" }
                check(pattern.occurrenceCount == expectedCount) { "Wrong occurrence count" }
                check(pattern.typicalDepartureMinuteOfDay == 480) { "Wrong departure minute" }
                check(pattern.averageDurationMinutes == 30L) { "Wrong duration" }
                check(pattern.usualTransportMode == TransportMode.CAR) { "Wrong transport mode" }
            }
        }

        var warmupCompleted = false
        try {
            repeat(warmups) { validate(detector.detect(reference, history)) }
            warmupCompleted = true
        } catch (error: Exception) {
            report.put("warmupError", errorJson(error))
        }
        if (warmupCompleted) {
            repeat(samples) { index ->
                val record = JSONObject().put("sample", index + 1).put("passed", false)
                var durationMs = 0.0
                try {
                    val startedAt = System.nanoTime()
                    val actual = try {
                        detector.detect(reference, history)
                    } finally {
                        durationMs = (System.nanoTime() - startedAt) / 1_000_000.0
                    }
                    validate(actual)
                    record.put("passed", true)
                    successfulTimes.add(durationMs)
                } catch (error: Exception) {
                    record.put("error", errorJson(error))
                }
                record.put("durationMs", durationMs)
                records.add(record)
            }
        }
        val sortedTimes = successfulTimes.sorted()
        fun percentile(fraction: Double): Any = if (sortedTimes.isEmpty()) JSONObject.NULL else
            sortedTimes[ceil(sortedTimes.size * fraction).toInt() - 1]
        return report.apply {
            put("warmupPassed", warmupCompleted)
            put("recordedSamples", records.size)
            put("skippedSamples", samples - records.size)
            put("successfulSamples", successfulTimes.size)
            put("failedSamples", records.size - successfulTimes.size)
            put("p50Ms", percentile(0.50))
            put("p95Ms", percentile(0.95))
            put("passed", warmupCompleted && successfulTimes.size == samples)
            put("records", JSONArray(records))
        }
    }

    private fun classificationSummary(records: List<JSONObject>): JSONObject {
        val classified = records.filter { !it.isNull("actualDetected") }
        fun count(expected: Boolean, actual: Boolean) = classified.count {
            it.getBoolean("expectedDetected") == expected && it.getBoolean("actualDetected") == actual
        }
        val truePositives = count(true, true)
        val trueNegatives = count(false, false)
        val falsePositives = count(false, true)
        val falseNegatives = count(true, false)
        return JSONObject().apply {
            put("unit", "One synthetic scenario; positive means detect returns a pattern")
            put("truePositives", truePositives)
            put("trueNegatives", trueNegatives)
            put("falsePositives", falsePositives)
            put("falseNegatives", falseNegatives)
            put("unclassifiedCases", records.size - classified.size)
            put("falsePositiveRate", ratio(falsePositives, falsePositives + trueNegatives))
            put("falseNegativeRate", ratio(falseNegatives, falseNegatives + truePositives))
        }
    }

    private fun patternJson(pattern: RecurringJourneyPattern?): Any = pattern?.let {
        JSONObject().apply {
            put("occurrenceCount", it.occurrenceCount)
            put("typicalDepartureMinuteOfDay", it.typicalDepartureMinuteOfDay)
            put("averageDurationMinutes", it.averageDurationMinutes)
            put("activeDays", JSONArray(it.activeDays.sortedBy { day -> day.value }.map { day -> day.name }))
            put("usualTransportMode", it.usualTransportMode.name)
        }
    } ?: JSONObject.NULL

    private fun errorJson(error: Exception) = JSONObject().apply {
        put("type", error.javaClass.simpleName)
        put("message", error.message ?: JSONObject.NULL)
    }

    private fun ratio(numerator: Int, denominator: Int): Any =
        if (denominator == 0) JSONObject.NULL else numerator.toDouble() / denominator
}
