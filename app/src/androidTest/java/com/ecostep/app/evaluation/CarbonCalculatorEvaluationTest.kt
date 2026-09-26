package com.ecostep.app.evaluation

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.algorithm.CarbonCalculator
import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: independent correctness evaluation of the current prototype factors. */
@RunWith(AndroidJUnit4::class)
class CarbonCalculatorEvaluationTest {
    private val calculator: CarbonCalculator = DefaultCarbonCalculator()
    private val records = mutableListOf<JSONObject>()

    @Test
    fun evaluateCarbonCalculator() {
        val startedAt = System.currentTimeMillis()
        val knownModes = TransportMode.entries.filter { it != TransportMode.UNKNOWN }

        // Hand-calculated references, independent of the calculator's private factor table.
        checkReference(TransportMode.WALKING, 10_000.0, 0.0, emptyMap())
        checkReference(TransportMode.CYCLING, 10_000.0, 0.0, emptyMap())
        checkReference(TransportMode.PUBLIC_TRANSPORT, 10_000.0, 890.0,
            mapOf(TransportMode.WALKING to 890.0, TransportMode.CYCLING to 890.0))
        checkReference(TransportMode.CAR, 10_000.0, 1920.0,
            mapOf(TransportMode.WALKING to 1920.0, TransportMode.CYCLING to 1920.0,
                TransportMode.PUBLIC_TRANSPORT to 1030.0))
        checkReference(TransportMode.UNKNOWN, 10_000.0, 0.0, emptyMap())
        checkReference(TransportMode.CAR, 1250.5, 240.096,
            mapOf(TransportMode.WALKING to 240.096, TransportMode.CYCLING to 240.096,
                TransportMode.PUBLIC_TRANSPORT to 128.8015))
        checkReference(TransportMode.PUBLIC_TRANSPORT, 1250.5, 111.2945,
            mapOf(TransportMode.WALKING to 111.2945, TransportMode.CYCLING to 111.2945))

        for (mode in TransportMode.entries) {
            evaluate("zero_distance", mode, 0.0, "Zero emissions and zero savings") { actual ->
                val result = calculate(mode, 0.0, actual)
                checkClose(0.0, result.emissionsGrams)
                result.lowerCarbonAlternatives.forEach { checkClose(0.0, it.savingsGrams) }
            }

            for (distance in listOf(-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
                evaluate("invalid_distance", mode, distance, "Reject with IllegalArgumentException") { actual ->
                    var rejected = false
                    try {
                        calculate(mode, distance, actual)
                    } catch (error: IllegalArgumentException) {
                        actual.put("exception", error.javaClass.simpleName)
                        rejected = true
                    }
                    check(rejected) { "Invalid distance was accepted" }
                }
            }
        }

        for (mode in knownModes) {
            evaluate("large_finite_distance", mode, Double.MAX_VALUE,
                "Finite nonnegative emissions; savings within emissions") { actual ->
                checkBounds(calculate(mode, Double.MAX_VALUE, actual))
            }

            evaluate("distance_scaling", mode, 1250.5,
                "Emissions and savings scale with distance by 2 and 10") { actual ->
                val base = calculate(mode, 1250.5, actual)
                for (scale in listOf(2.0, 10.0)) {
                    val scaled = calculate(mode, 1250.5 * scale, actual, "scaled_$scale")
                    checkClose(base.emissionsGrams * scale, scaled.emissionsGrams)
                    val baseSavings = base.lowerCarbonAlternatives.associate { it.mode to it.savingsGrams }
                    val scaledSavings = scaled.lowerCarbonAlternatives.associate { it.mode to it.savingsGrams }
                    check(baseSavings.keys == scaledSavings.keys) { "Alternative modes changed with distance" }
                    baseSavings.forEach { (alternative, savings) ->
                        checkClose(savings * scale, scaledSavings.getValue(alternative))
                    }
                }
            }

            for (distance in listOf(0.001, 1.0, 1250.5, 1_000_000_000.0)) {
                evaluate("savings_consistency", mode, distance,
                    "Unique alternatives; 0 <= savings <= emissions; savings = original - alternative") { actual ->
                    val result = calculate(mode, distance, actual)
                    checkBounds(result)
                    val alternatives = result.lowerCarbonAlternatives
                    check(alternatives.map { it.mode }.distinct().size == alternatives.size) {
                        "Duplicate alternative modes"
                    }
                    for (alternative in alternatives) {
                        check(alternative.mode != mode && alternative.mode != TransportMode.UNKNOWN) {
                            "Invalid alternative mode: ${alternative.mode}"
                        }
                        val alternativeResult = calculate(alternative.mode, distance, actual, alternative.mode.name)
                        check(alternativeResult.emissionsGrams < result.emissionsGrams) {
                            "Alternative does not reduce emissions"
                        }
                        checkClose(result.emissionsGrams - alternativeResult.emissionsGrams, alternative.savingsGrams)
                    }
                }
            }
        }

        val failed = records.count { !it.getBoolean("passed") }
        val report = JSONObject().apply {
            put("schemaVersion", 1)
            put("calculator", "DefaultCarbonCalculator")
            put("startedAtMillis", startedAt)
            put("finishedAtMillis", System.currentTimeMillis())
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}")
            put("scope", "Correctness with provisional factors: WALKING=0, CYCLING=0, PUBLIC_TRANSPORT=89, CAR=192 g/km. Not validation of real-world emissions or a latency benchmark.")
            put("nonFiniteEncoding", "NaN and infinities are strings because JSON numbers must be finite.")
            put("totalCases", records.size)
            put("passedCases", records.size - failed)
            put("failedCases", failed)
            put("passed", failed == 0)
            put("records", JSONArray(records))
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.filesDir, "evaluation")
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create $directory" }
        File(directory, "carbon-evaluation-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)

        // Export every case before failing the test, so defects remain inspectable.
        assertTrue("$failed/${records.size} carbon evaluation cases failed; see the JSON report", failed == 0)
    }

    private fun checkReference(mode: TransportMode, distance: Double, emissions: Double,
        savings: Map<TransportMode, Double>) {
        evaluate("reference", mode, distance, "Emissions=$emissions g; savings=$savings g") { actual ->
            val result = calculate(mode, distance, actual)
            checkClose(emissions, result.emissionsGrams)
            check(result.lowerCarbonAlternatives.size == savings.size) { "Wrong number of alternatives" }
            val actualSavings = result.lowerCarbonAlternatives.associate { it.mode to it.savingsGrams }
            check(actualSavings.keys == savings.keys) { "Wrong alternative modes" }
            savings.forEach { (alternative, expected) -> checkClose(expected, actualSavings.getValue(alternative)) }
        }
    }

    private fun evaluate(scenario: String, mode: TransportMode, distance: Double,
        expected: String, block: (JSONObject) -> Unit) {
        val actual = JSONObject()
        val record = JSONObject().apply {
            put("scenario", scenario)
            put("mode", mode.name)
            put("distanceMeters", jsonNumber(distance))
            put("expected", expected)
            put("actual", actual)
        }
        try {
            block(actual)
            record.put("passed", true)
        } catch (error: Exception) {
            record.put("passed", false)
            record.put("errorType", error.javaClass.simpleName)
            record.put("error", error.message ?: "No message")
        }
        records.add(record)
    }

    private fun calculate(mode: TransportMode, distance: Double, actual: JSONObject,
        key: String = "result"): CarbonResult {
        val journey = JourneySummary(
            journeyId = "carbon_evaluation", userId = "evaluation_user",
            startLocation = GeoPoint(-37.81, 144.96), endLocation = GeoPoint(-37.82, 144.97),
            startTimeMillis = 0L, endTimeMillis = 60_000L,
            distanceMeters = distance, transportMode = mode,
        )
        val result = calculator.calculate(journey)
        actual.put(key, JSONObject().apply {
            put("emissionsGrams", jsonNumber(result.emissionsGrams))
            put("alternatives", JSONArray().apply {
                result.lowerCarbonAlternatives.forEach { alternative ->
                    put(JSONObject().apply {
                        put("mode", alternative.mode.name)
                        put("savingsGrams", jsonNumber(alternative.savingsGrams))
                    })
                }
            })
        })
        return result
    }

    private fun checkBounds(result: CarbonResult) {
        check(result.emissionsGrams.isFinite() && result.emissionsGrams >= 0) { "Invalid emissions" }
        result.lowerCarbonAlternatives.forEach {
            check(it.savingsGrams.isFinite() && it.savingsGrams >= 0 && it.savingsGrams <= result.emissionsGrams) {
                "Savings outside [0, emissions]: ${it.mode}=${it.savingsGrams}"
            }
        }
    }

    private fun checkClose(expected: Double, actual: Double) {
        check(actual.isFinite() && abs(expected - actual) <= max(1e-9, abs(expected) * 1e-9)) {
            "Expected $expected, got $actual"
        }
    }

    private fun jsonNumber(value: Double): Any = if (value.isFinite()) value else value.toString()
}
