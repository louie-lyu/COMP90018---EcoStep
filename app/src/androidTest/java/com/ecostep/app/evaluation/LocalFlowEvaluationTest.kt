package com.ecostep.app.evaluation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.algorithm.DefaultMissionValidator
import com.ecostep.app.algorithm.DefaultWeeklyCoach
import com.ecostep.app.algorithm.FallbackMissionGenerator
import com.ecostep.app.algorithm.WeeklyCoachReport
import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.TransportResult
import com.ecostep.app.data.model.WeatherData
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: real local algorithms connected by synthetic candidates and completion events. */
@RunWith(AndroidJUnit4::class)
class LocalFlowEvaluationTest {
    private val carbon = DefaultCarbonCalculator()
    private val fallback = FallbackMissionGenerator()
    private val validator = DefaultMissionValidator()
    private val points = DefaultEcoPointsCalculator()
    private val weekly = DefaultWeeklyCoach()

    @Test
    fun evaluateLocalFlow() = runBlocking {
        val cases = LocalFlowEvaluationCases.cases()
        val mixedWeek = LocalFlowEvaluationCases.mixedWeek()
        LocalEvaluationRun("local-flow",
            "Synthetic journey -> real carbon calculator -> filtered candidate context -> real fallback/validator -> synthetic completion -> real EcoPoints -> real weekly summary. Candidate availability and completion are supplied by the harness. No UI, sensors, Firebase, network or AI. Prototype carbon factors only. Timing includes context/result construction and all local stages.",
            cases.size + 1, 4).evaluate {
            for (fixture in cases) {
                case(fixture.name, fixture.expected.toJson()) { actual ->
                    actual.put("distanceMeters", if (fixture.distanceMeters.isFinite()) fixture.distanceMeters else fixture.distanceMeters.toString())
                        .put("originalMode", fixture.originalMode.name).put("actualMode", fixture.actualMode.name)
                        .put("accepted", fixture.accepted).put("completed", fixture.completed).put("timestampMillis", fixture.timestampMillis)
                        .put("availableModes", JSONArray(fixture.availableModes.map { it.name }))
                    try {
                        val flow = runFlow(listOf(fixture)) { actual.put("stage", it) }
                        actual.put("flow", flow.toJson())
                        check(fixture.expected.errorStage == null) { "Expected a rejected input" }
                        verifySingle(flow, fixture.expected)
                    } catch (error: IllegalArgumentException) {
                        check(fixture.expected.errorStage != null && actual.getString("stage") == fixture.expected.errorStage) {
                            "Unexpected rejection at ${actual.optString("stage")}: ${error.message}"
                        }
                        actual.put("error", "IllegalArgumentException")
                    }
                }
            }
            case("mixed_week_totals", JSONObject().put("points", 266).put("weeklyAccepted", 3)
                .put("weeklyCompleted", 3).put("weeklySavingGrams", 2112.0).put("weeklyMode", "WALKING")) { actual ->
                val flow = runFlow(mixedWeek)
                actual.put("flow", flow.toJson())
                verifyMixed(flow, mixedWeek)
            }
            for (fixture in cases.take(3)) {
                val input = listOf(fixture)
                benchmark(fixture.name, 1, operation = { runFlow(input) }, verify = { verifySingle(it, fixture.expected) })
            }
            benchmark("mixed_week_totals", mixedWeek.size, operation = { runFlow(mixedWeek) }, verify = { verifyMixed(it, mixedWeek) })
        }
    }

    private suspend fun runFlow(fixtures: List<LocalFlowCase>, onStage: (String) -> Unit = {}): FlowResult {
        val journeys = fixtures.map { fixture ->
            val journey = JourneySummary("private_flow_${fixture.name}", "private_flow_user",
                GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.02), 0L, 900_000L, fixture.distanceMeters, fixture.originalMode)
            onStage("carbon")
            val calculated = carbon.calculate(journey)
            val candidates = calculated.copy(lowerCarbonAlternatives = calculated.lowerCarbonAlternatives.filter { it.mode in fixture.availableModes })
            val context = MissionContext(journey, TransportResult(fixture.originalMode, 100.0, emptyList()), candidates,
                RouteInfo(fixture.originalMode, fixture.distanceMeters, 900L), WeatherData(20.0, "Clear"),
                if (TransportMode.PUBLIC_TRANSPORT in fixture.availableModes) listOf(PublicTransportInfo("Synthetic service", 0L, 1200L)) else emptyList(),
                emptyList())
            onStage("fallback")
            val mission = fallback.generateMission(context)
            check(validator.isValid(mission)) { "Invalid fallback mission" }
            onStage("completion")
            // Corrected modes use the verified carbon alternatives, never the recommended saving blindly.
            val actualSaving = calculated.lowerCarbonAlternatives.firstOrNull { it.mode == fixture.actualMode }?.savingsGrams ?: 0.0
            val completion = MissionResult(mission.missionId, fixture.accepted, fixture.completed,
                fixture.actualMode, actualSaving, fixture.timestampMillis)
            onStage("points")
            JourneyResult(calculated, mission, completion, points.calculatePoints(completion))
        }
        onStage("weekly")
        return FlowResult(journeys, journeys.sumOf { it.points }, weekly.generate(journeys.map { it.completion }, 1000L, 2000L))
    }

    private fun verifySingle(flow: FlowResult, expected: ExpectedLocalFlow) {
        verifyJourney(flow.journeys.single(), expected)
        check(flow.totalPoints == expected.points) { "Unexpected total points" }
        verifyWeekly(flow.weekly, expected.weeklyAccepted, expected.weeklyCompleted,
            expected.weeklyRate, expected.weeklySavingGrams, expected.weeklyMode)
        verifyPrivacy(flow)
    }

    private fun verifyMixed(flow: FlowResult, fixtures: List<LocalFlowCase>) {
        check(flow.journeys.size == 3) { "Missing journey results" }
        flow.journeys.zip(fixtures).forEach { (actual, fixture) -> verifyJourney(actual, fixture.expected) }
        check(flow.totalPoints == 266) { "Unexpected accumulated points" }
        verifyWeekly(flow.weekly, 3, 3, 100.0, 2112.0, TransportMode.WALKING)
        verifyPrivacy(flow)
    }

    private fun verifyJourney(actual: JourneyResult, expected: ExpectedLocalFlow) {
        checkClose(requireNotNull(expected.emissionsGrams), actual.carbon.emissionsGrams)
        check(actual.mission.recommendedMode == expected.missionMode) { "Unexpected fallback mode" }
        checkClose(expected.missionSavingGrams, actual.mission.estimatedCarbonSavingGrams)
        check(actual.completion.missionId == actual.mission.missionId) { "Mission identity lost" }
        checkClose(expected.actualSavingGrams, requireNotNull(actual.completion.actualCarbonSavingGrams))
        check(actual.points == expected.points) { "Unexpected points" }
    }

    private fun verifyWeekly(actual: WeeklyCoachReport, accepted: Int, completed: Int,
        rate: Double, savings: Double, mode: TransportMode?) {
        check(actual.acceptedMissions == accepted && actual.completedMissions == completed) { "Unexpected weekly counts" }
        checkClose(rate, actual.completionRate)
        checkClose(savings, actual.totalCarbonSavingGrams)
        check(actual.mostUsedCompletedMode == mode) { "Unexpected weekly mode" }
    }

    private fun verifyPrivacy(flow: FlowResult) {
        check("private_flow_" !in flow.weekly.aiPrompt) { "Journey/user/mission identifiers entered weekly prompt" }
    }

    private fun checkClose(expected: Double, actual: Double) {
        check(actual.isFinite() && abs(expected - actual) < 1e-8) { "Expected $expected, got $actual" }
    }

    private data class JourneyResult(val carbon: CarbonResult, val mission: EcoMission, val completion: MissionResult, val points: Int)
    private data class FlowResult(val journeys: List<JourneyResult>, val totalPoints: Int, val weekly: WeeklyCoachReport)

    private fun ExpectedLocalFlow.toJson(): JSONObject {
        if (errorStage != null) return JSONObject().put("error", "IllegalArgumentException").put("errorStage", errorStage)
        return JSONObject().put("emissionsGrams", emissionsGrams ?: JSONObject.NULL)
        .put("missionMode", missionMode?.name ?: JSONObject.NULL).put("missionSavingGrams", missionSavingGrams)
        .put("actualSavingGrams", actualSavingGrams).put("points", points)
        .put("weeklyAccepted", weeklyAccepted).put("weeklyCompleted", weeklyCompleted).put("weeklyRate", weeklyRate)
        .put("weeklySavingGrams", weeklySavingGrams).put("weeklyMode", weeklyMode?.name ?: JSONObject.NULL)
    }

    private fun FlowResult.toJson() = JSONObject().put("journeys", JSONArray(journeys.map { journey -> JSONObject()
        .put("emissionsGrams", journey.carbon.emissionsGrams).put("missionId", journey.mission.missionId)
        .put("missionMode", journey.mission.recommendedMode.name).put("missionSavingGrams", journey.mission.estimatedCarbonSavingGrams)
        .put("actualMode", journey.completion.actualTransportMode?.name ?: JSONObject.NULL)
        .put("actualSavingGrams", journey.completion.actualCarbonSavingGrams ?: JSONObject.NULL).put("points", journey.points)
    })).put("totalPoints", totalPoints).put("weekly", JSONObject()
        .put("acceptedMissions", weekly.acceptedMissions).put("completedMissions", weekly.completedMissions)
        .put("completionRate", weekly.completionRate).put("savingsGrams", weekly.totalCarbonSavingGrams)
        .put("mode", weekly.mostUsedCompletedMode?.name ?: JSONObject.NULL).put("summary", weekly.summary).put("aiPrompt", weekly.aiPrompt))
}
