package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.*
import com.ecostep.app.data.model.*
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private val pointsCarbonCalculator = DefaultCarbonCalculator()

internal fun pointsInput(case: EcoPointsCase): MissionResult {
    val journey = case.journey ?: return case.input
    val savings = pointsCarbonCalculator.calculate(journey).lowerCarbonAlternatives
        .single { it.mode == case.input.actualTransportMode }.savingsGrams
    return case.input.copy(actualCarbonSavingGrams = savings)
}

internal fun verifyWeeklyReport(actual: WeeklyCoachReport, expected: ExpectedWeeklyReport) {
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


internal fun verifyTrigger(actual: MissionTrigger?, fixture: MissionTriggerCase) {
    check(actual == fixture.expected) { "Unexpected trigger: $actual; expected ${fixture.expected}" }
    if (actual != null) {
        check(actual.notificationTimeMillis >= fixture.currentTimeMillis) { "Notification is in the past" }
        check(actual.notificationTimeMillis <= actual.expectedDepartureTimeMillis) { "Notification is after departure" }
        val departureDay = Instant.ofEpochMilli(actual.expectedDepartureTimeMillis).atZone(ZoneId.of(fixture.zone)).dayOfWeek
        check(departureDay in fixture.activeDays) { "Departure is on an inactive day" }
    }
}

