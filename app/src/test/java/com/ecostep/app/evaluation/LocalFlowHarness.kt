package com.ecostep.app.evaluation

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

/** Same local stages and assertions as LocalFlowEvaluationTest; shared by JUnit and benchmarks. */
internal class LocalFlowHarness {
    private val carbon = DefaultCarbonCalculator()
    private val fallback = FallbackMissionGenerator()
    private val validator = DefaultMissionValidator()
    private val points = DefaultEcoPointsCalculator()
    private val weekly = DefaultWeeklyCoach()

    suspend fun runFlow(fixtures: List<LocalFlowCase>, onStage: (String) -> Unit = {}): FlowResult {
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

    fun verifySingle(flow: FlowResult, expected: ExpectedLocalFlow) {
        verifyJourney(flow.journeys.single(), expected)
        check(flow.totalPoints == expected.points) { "Unexpected total points" }
        verifyWeekly(flow.weekly, expected.weeklyAccepted, expected.weeklyCompleted,
            expected.weeklyRate, expected.weeklySavingGrams, expected.weeklyMode)
        verifyPrivacy(flow)
    }

    fun verifyMixed(flow: FlowResult, fixtures: List<LocalFlowCase>) {
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

    data class JourneyResult(val carbon: CarbonResult, val mission: EcoMission, val completion: MissionResult, val points: Int)
    data class FlowResult(val journeys: List<JourneyResult>, val totalPoints: Int, val weekly: WeeklyCoachReport)

}
