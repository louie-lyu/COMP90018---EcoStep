package com.ecostep.app.evaluation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ecostep.app.algorithm.DefaultWeeklyCoach
import com.ecostep.app.algorithm.WeeklyCoachReport
import com.ecostep.app.data.model.TransportMode
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: local weekly statistics, fallback text, prompt privacy and scaling. */
@RunWith(AndroidJUnit4::class)
class WeeklyCoachEvaluationTest {
    @Test
    fun evaluateWeeklyCoach() = runBlocking {
        val coach = DefaultWeeklyCoach()
        val cases = WeeklyCoachEvaluationCases.cases()
        LocalEvaluationRun("weekly-coach",
            "Fixed synthetic mission histories; local weekly aggregation, fallback and prompt checks. No Gemini, Firebase, UI or user-data accuracy evaluation.",
            cases.size, 6).evaluate {
            for (fixture in cases) {
                val expected = fixture.expected
                case(fixture.name, expected?.toJson() ?: JSONObject().put("error", "IllegalArgumentException")) { actual ->
                    if (expected == null) {
                        expectIllegalArgument { coach.generate(fixture.results, fixture.startMillis, fixture.endMillis) }
                        actual.put("error", "IllegalArgumentException")
                    } else {
                        val result = coach.generate(fixture.results, fixture.startMillis, fixture.endMillis)
                        actual.put("report", result.toJson())
                        verify(result, expected)
                        check(fixture.results.none { it.missionId in result.aiPrompt }) { "Mission ID entered prompt" }
                        actual.put("promptPrivacyPassed", true)
                    }
                }
            }
            for (size in listOf(0, 10, 100, 1000, 10000)) {
                val history = List(size) { WeeklyCoachEvaluationCases.mission("benchmark_$it", saving = 1.0) }
                val expected = ExpectedWeeklyReport(size, size, if (size == 0) 0.0 else 100.0,
                    size.toDouble(), if (size == 0) null else TransportMode.WALKING,
                    if (size == 0) WeeklyCoachEvaluationCases.EMPTY else WeeklyCoachEvaluationCases.HIGH)
                benchmark("all_in_week_$size", size, operation = { coach.generate(history, 1000L, 2000L) },
                    verify = { verify(it, expected) })
            }
            val sparseHistory = List(10000) { index -> WeeklyCoachEvaluationCases.mission("sparse_$index",
                saving = 1.0, timestamp = if (index % 10 == 0) 1500L else 999L) }
            val sparseExpected = ExpectedWeeklyReport(1000, 1000, 100.0, 1000.0, TransportMode.WALKING, WeeklyCoachEvaluationCases.HIGH)
            benchmark("ten_percent_in_week_10000", sparseHistory.size,
                operation = { coach.generate(sparseHistory, 1000L, 2000L) }, verify = { verify(it, sparseExpected) })
        }
    }

    private fun verify(actual: WeeklyCoachReport, expected: ExpectedWeeklyReport) {
        check(actual.acceptedMissions == expected.accepted) { "Unexpected accepted count" }
        check(actual.completedMissions == expected.completed) { "Unexpected completed count" }
        check(abs(actual.completionRate - expected.completionRate) < 1e-8) { "Unexpected completion rate" }
        check(abs(actual.totalCarbonSavingGrams - expected.savingsGrams) < 1e-8) { "Unexpected savings" }
        check(actual.mostUsedCompletedMode == expected.mode) { "Unexpected most-used mode" }
        check(actual.fallbackMessage == expected.fallbackMessage) { "Unexpected fallback message" }
        val rate = String.format(Locale.US, "%.1f", expected.completionRate)
        val savings = String.format(Locale.US, "%.1f", expected.savingsGrams)
        check(actual.summary == "Completed ${expected.completed} of ${expected.accepted} accepted missions ($rate%) and saved approximately $savings g of CO2.") {
            "Summary differs from verified statistics"
        }
        val promptFields = listOf("Accepted missions: ${expected.accepted}", "Completed missions: ${expected.completed}",
            "Completion rate: $rate%", "Recorded estimated carbon saved: $savings grams",
            "Most used completed transport mode: ${expected.mode?.name ?: "NONE"}")
        check(promptFields.all { it in actual.aiPrompt }) { "Verified statistics missing from prompt" }
    }

    private fun ExpectedWeeklyReport.toJson() = JSONObject().put("acceptedMissions", accepted)
        .put("completedMissions", completed).put("completionRate", completionRate)
        .put("savingsGrams", savingsGrams).put("mode", mode?.name ?: JSONObject.NULL).put("fallbackMessage", fallbackMessage)

    private fun WeeklyCoachReport.toJson() = JSONObject().put("acceptedMissions", acceptedMissions)
        .put("completedMissions", completedMissions).put("completionRate", completionRate)
        .put("savingsGrams", totalCarbonSavingGrams).put("mode", mostUsedCompletedMode?.name ?: JSONObject.NULL)
        .put("summary", summary).put("fallbackMessage", fallbackMessage).put("aiPrompt", aiPrompt)
}
