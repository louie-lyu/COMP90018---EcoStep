package com.ecostep.app.evaluation

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.algorithm.DefaultAiMissionGenerator
import com.ecostep.app.algorithm.DefaultMissionValidator
import com.ecostep.app.algorithm.FallbackMissionGenerator
import com.ecostep.app.algorithm.MissionRecommendation
import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.network.ai.AiGateway
import com.ecostep.app.network.ai.AiMissionSuggestion
import com.ecostep.app.network.ai.AiWeeklyAdvice
import java.io.File
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: real local mission pipeline with controlled AI replies; no network or credentials. */
@RunWith(AndroidJUnit4::class)
class AiMissionEvaluationTest {
    @Test
    fun evaluateAiMissions() = runBlocking {
        val startedAt = System.currentTimeMillis()
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String, default: Int, range: IntRange): Int {
            val value = arguments.getString(name)?.toInt() ?: default
            require(value in range) { "$name must be in $range" }
            return value
        }
        val samples = argument("samples", 50, 1..1000)
        val warmups = argument("warmups", 10, 1..100)
        val batchSize = argument("batchSize", 100, 1..1000)
        val cases = AiMissionEvaluationCases.cases()
        val records = mutableListOf<JSONObject>()
        val benchmarks = mutableListOf<JSONObject>()
        val report = JSONObject().apply {
            put("schemaVersion", 1)
            put("datasetVersion", 2)
            put("startedAtMillis", startedAt)
            put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
            put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("apiLevel", Build.VERSION.SDK_INT)
            put("buildType", BuildConfig.BUILD_TYPE)
            put("scope", "Synthetic local mission validation, retries, fallback and prompt checks with controlled AiGateway replies. No live Gemini, Firebase, Worker/HTTP integration or AI text-quality evaluation.")
            put("benchmarkMethod", "Sequential validator/fallback groups; System.nanoTime around batches. Includes local calls, coroutine/loop overhead and result-list writes. Setup, warm-ups, output validation and JSON excluded. Nearest-rank P50/P95 use successful batches only. Per-call values are batch averages, not individual-call tail latency or real AI request latency. JIT/GC and group order affect comparisons; no latency threshold.")
        }
        var completed = false
        try {
            cases.forEach { records.add(evaluateCase(it)) }
            val context = AiMissionEvaluationCases.context()
            val validMission = EcoMission("evaluation", TransportMode.WALKING, 500.0, "Try walking.", 80.0)
            val validator = DefaultMissionValidator()
            val fallback = FallbackMissionGenerator()
            benchmarks.add(benchmark("validator", samples, warmups, batchSize,
                operation = { validator.isValid(validMission) }, validate = { check(it) { "Valid mission rejected" } }))
            benchmarks.add(benchmark("fallback", samples, warmups, batchSize,
                operation = { fallback.generateMission(context) }, validate = { validateMission(it, ExpectedRecommendation()) }))
            completed = true
        } catch (error: Exception) {
            report.put("runError", errorJson(error))
            throw error
        } finally {
            val passedCases = records.count { it.getBoolean("passed") }
            val fallbackCases = cases.count { it.expected.error == null && !it.expected.usedAi }
            val passedFallbacks = records.count {
                it.getBoolean("passed") && !it.isNull("actual") && !it.getJSONObject("actual").getBoolean("usedAi")
            }
            report.put("plannedCases", cases.size)
            report.put("totalCases", records.size)
            report.put("passedCases", passedCases)
            report.put("failedCases", records.size - passedCases)
            report.put("expectedFallbackCases", fallbackCases)
            report.put("passedFallbackCases", passedFallbacks)
            report.put("fallbackCasePassRate", if (fallbackCases == 0) JSONObject.NULL else passedFallbacks.toDouble() / fallbackCases)
            report.put("records", JSONArray(records))
            report.put("plannedBenchmarkGroups", 2)
            report.put("benchmarks", JSONArray(benchmarks))
            report.put("passed", completed && passedCases == cases.size && benchmarks.all { it.getBoolean("passed") })
            report.put("finishedAtMillis", System.currentTimeMillis())
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val directory = File(context.filesDir, "evaluation")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create evaluation directory" }
            File(directory, "ai-mission-evaluation-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)
        }
        assertTrue("AI mission evaluation failed; inspect the JSON report", report.getBoolean("passed"))
    }

    private suspend fun evaluateCase(case: AiMissionCase): JSONObject {
        val ai = ControlledAi(case.reply)
        val record = JSONObject().apply {
            put("scenario", case.name)
            put("expected", JSONObject().apply {
                put("mode", if (case.expected.error == null) case.expected.mode.name else JSONObject.NULL)
                put("savingsGrams", if (case.expected.error == null) case.expected.savingsGrams else JSONObject.NULL)
                put("usedAi", if (case.expected.error == null) case.expected.usedAi else JSONObject.NULL)
                put("providerCalls", case.expected.calls)
                put("error", case.expected.error ?: JSONObject.NULL)
            })
            put("alternatives", JSONArray(case.context.carbonResult.lowerCarbonAlternatives.map {
                JSONObject().put("mode", it.mode.name).put("savingsGrams", it.savingsGrams)
            }))
            put("publicTransportServices", case.context.publicTransportOptions.size)
            put("passed", false)
        }
        try {
            val result = DefaultAiMissionGenerator(ai).generateRecommendation(case.context)
            record.put("actual", recommendationJson(result))
            check(case.expected.error == null) { "Expected an exception" }
            validateRecommendation(result, case.expected)
            if (result.usedAi) {
                val reply = ai.replies.last()
                check(result.mission.explanation == reply.explanation.trim()) { "Explanation changed" }
                check(result.mission.confidence == reply.confidence) { "Confidence changed" }
                check(result.notificationTitle == reply.notificationTitle.trim()) { "Title changed" }
                check(result.notificationMessage == reply.notificationMessage.trim()) { "Message changed" }
            }
            record.put("passed", true)
        } catch (error: Exception) {
            record.put("error", errorJson(error))
            record.put("passed", error.javaClass.simpleName == case.expected.error)
            if (error is CancellationException && case.expected.error != "CancellationException") throw error
        } finally {
            record.put("providerCalls", ai.calls)
            record.put("providerErrors", JSONArray(ai.errors))
            record.put("replies", JSONArray(ai.replies.map { JSONObject().apply {
                put("mode", it.recommendedMode.name)
                put("explanation", it.explanation)
                put("confidence", it.confidence)
                put("notificationTitle", it.notificationTitle)
                put("notificationMessage", it.notificationMessage)
            } }))
            try {
                check(ai.calls == case.expected.calls) { "Expected ${case.expected.calls} calls, got ${ai.calls}" }
                for (prompt in ai.prompts) {
                    val journeys = case.context.recentJourneyHistory + case.context.journey
                    for (journey in journeys) {
                        val privateValues = listOf(journey.journeyId, journey.userId,
                            journey.startLocation.latitude.toString(), journey.startLocation.longitude.toString(),
                            journey.endLocation.latitude.toString(), journey.endLocation.longitude.toString())
                        check(privateValues.filter { it.isNotBlank() }.none { it in prompt }) { "Private fixture data entered prompt" }
                    }
                    check("5.0 km" in prompt && "15 minutes" in prompt && "Clear, 20 C" in prompt) { "Verified context missing" }
                    if (case.context.publicTransportOptions.isEmpty()) {
                        check("- PUBLIC_TRANSPORT:" !in prompt) { "Unavailable public transport entered prompt" }
                    }
                }
                if (ai.calls == 2) {
                    check(ai.prompts.last().startsWith(ai.prompts.first()) && "Retry:" in ai.prompts.last()) { "Retry guidance missing" }
                }
                record.put("promptChecksPassed", true)
            } catch (error: Exception) {
                record.put("passed", false)
                record.put("verificationError", errorJson(error))
            }
        }
        return record
    }

    private fun validateMission(mission: EcoMission, expected: ExpectedRecommendation) {
        check(mission.missionId.isNotBlank()) { "Missing mission ID" }
        check(mission.recommendedMode == expected.mode) { "Unexpected transport mode" }
        check(mission.estimatedCarbonSavingGrams == expected.savingsGrams) { "Verified savings changed" }
        check(mission.explanation.isNotBlank()) { "Missing explanation" }
        check(mission.confidence in 0.0..100.0) { "Invalid confidence" }
        if (!expected.usedAi) {
            val mode = when (expected.mode) {
                TransportMode.WALKING -> "walking"
                TransportMode.CYCLING -> "cycling"
                TransportMode.PUBLIC_TRANSPORT -> "public transport"
                else -> error("Unexpected fallback fixture mode")
            }
            check(mission.explanation == "Try $mode for this journey to save about ${expected.savingsGrams.toInt()} g of CO2.") { "Unexpected fallback explanation" }
            check(mission.confidence == 100.0) { "Unexpected fallback confidence" }
        }
    }

    private fun validateRecommendation(result: MissionRecommendation, expected: ExpectedRecommendation) {
        validateMission(result.mission, expected)
        check(result.usedAi == expected.usedAi) { "Unexpected recommendation source" }
        check(result.notificationTitle.isNotBlank() && result.notificationMessage.isNotBlank()) { "Missing notification text" }
        if (!expected.usedAi) {
            check(result.notificationTitle == "Your EcoStep mission" && result.notificationMessage == result.mission.explanation) {
                "Unexpected fallback notification"
            }
        }
    }

    private suspend fun <T> benchmark(name: String, samples: Int, warmups: Int, batchSize: Int,
        operation: suspend () -> T, validate: (T) -> Unit): JSONObject {
        val results = ArrayList<T>(batchSize)
        val records = mutableListOf<JSONObject>()
        val times = mutableListOf<Double>()
        suspend fun batch() {
            results.clear()
            repeat(batchSize) { results.add(operation()) }
        }
        val report = JSONObject().put("scenario", name).put("plannedSamples", samples)
            .put("warmupBatches", warmups).put("batchSize", batchSize)
        var warmupPassed = false
        try {
            repeat(warmups) { batch(); results.forEach(validate) }
            warmupPassed = true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
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
                results.forEach(validate)
                times.add(durationMs)
                record.put("passed", true)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
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

    private fun recommendationJson(result: MissionRecommendation) = JSONObject().apply {
        put("usedAi", result.usedAi)
        put("missionId", result.mission.missionId)
        put("mode", result.mission.recommendedMode.name)
        put("savingsGrams", result.mission.estimatedCarbonSavingGrams)
        put("explanation", result.mission.explanation)
        put("confidence", result.mission.confidence)
        put("notificationTitle", result.notificationTitle)
        put("notificationMessage", result.notificationMessage)
    }

    private fun errorJson(error: Exception) = JSONObject().apply {
        put("type", error.javaClass.simpleName)
        put("message", error.message ?: JSONObject.NULL)
    }

    private class ControlledAi(private val reply: (Int) -> AiMissionSuggestion) : AiGateway {
        var calls = 0
            private set
        val prompts = mutableListOf<String>()
        val replies = mutableListOf<AiMissionSuggestion>()
        val errors = mutableListOf<String>()

        override suspend fun generateMission(prompt: String): AiMissionSuggestion {
            calls++
            prompts.add(prompt)
            return try {
                reply(calls).also { replies.add(it) }
            } catch (error: Exception) {
                errors.add(error.javaClass.simpleName)
                throw error
            }
        }

        override suspend fun generateWeeklyAdvice(prompt: String): AiWeeklyAdvice = error("Weekly coaching is outside this evaluation")
    }
}
