package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import java.util.Locale

class DefaultWeeklyCoach : WeeklyCoach {

    override fun generate(
        results: List<MissionResult>,
        weekStartMillis: Long,
        weekEndMillis: Long,
    ): WeeklyCoachReport {
        require(weekEndMillis >= weekStartMillis) {
            "Week end must not be before week start."
        }

        // Keep only missions within the selected week.
        val weeklyResults = results.filter {
            it.timestampMillis in weekStartMillis..weekEndMillis
        }

        val acceptedResults = weeklyResults.filter { it.accepted }
        val completedResults = acceptedResults.filter { it.completed }

        val acceptedMissions = acceptedResults.size
        val completedMissions = completedResults.size

        val completionRate = if (acceptedMissions == 0) {
            0.0
        } else {
            completedMissions.toDouble() / acceptedMissions * 100.0
        }

        // Count actual savings only from completed missions.
        val totalCarbonSavingGrams = completedResults.sumOf {
            validCarbonSaving(it.actualCarbonSavingGrams)
        }

        val mostUsedCompletedMode = findMostUsedMode(completedResults)

        return WeeklyCoachReport(
            acceptedMissions = acceptedMissions,
            completedMissions = completedMissions,
            completionRate = completionRate,
            totalCarbonSavingGrams = totalCarbonSavingGrams,
            mostUsedCompletedMode = mostUsedCompletedMode,
            summary = buildSummary(
                acceptedMissions,
                completedMissions,
                completionRate,
                totalCarbonSavingGrams,
            ),
            aiPrompt = buildAiPrompt(
                acceptedMissions,
                completedMissions,
                completionRate,
                totalCarbonSavingGrams,
                mostUsedCompletedMode,
            ),
            fallbackMessage = buildFallbackMessage(
                acceptedMissions,
                completedMissions,
                completionRate,
            ),
        )
    }

    private fun validCarbonSaving(value: Double?): Double {
        if (value == null || !value.isFinite() || value < 0.0) {
            return 0.0
        }

        return value
    }

    private fun findMostUsedMode(
        completedResults: List<MissionResult>,
    ): TransportMode? {
        return completedResults
            .mapNotNull { it.actualTransportMode }
            .filter { it != TransportMode.UNKNOWN }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun buildSummary(
        acceptedMissions: Int,
        completedMissions: Int,
        completionRate: Double,
        totalCarbonSavingGrams: Double,
    ): String {
        return "Completed $completedMissions of $acceptedMissions accepted missions " +
                "(${formatNumber(completionRate)}%) and saved approximately " +
                "${formatNumber(totalCarbonSavingGrams)} g of CO2."
    }

    private fun buildAiPrompt(
        acceptedMissions: Int,
        completedMissions: Int,
        completionRate: Double,
        totalCarbonSavingGrams: Double,
        mostUsedCompletedMode: TransportMode?,
    ): String {
        val mode = mostUsedCompletedMode?.name ?: "NONE"

        return """
            You are the EcoStep Weekly Coach.
            Write one short encouraging weekly insight and one realistic action for next week.

            Aggregated weekly information:
            - Accepted missions: $acceptedMissions
            - Completed missions: $completedMissions
            - Completion rate: ${formatNumber(completionRate)}%
            - Recorded estimated carbon saved: ${formatNumber(totalCarbonSavingGrams)} grams
            - Most used completed transport mode: $mode

            Use only the information provided.
            Do not invent routes, locations, carbon values or personal information.
            If there are no completed missions, encourage one achievable first step.
            Keep the response under 80 words.
        """.trimIndent()
    }

    private fun buildFallbackMessage(
        acceptedMissions: Int,
        completedMissions: Int,
        completionRate: Double,
    ): String {
        if (acceptedMissions == 0) {
            return "Try accepting one small low-carbon mission next week."
        }

        if (completedMissions == 0) {
            return "Start with one achievable mission and build from there."
        }

        return when {
            completionRate >= 80.0 ->
                "Great work completing your missions this week. Keep choosing achievable low-carbon goals."

            completionRate >= 50.0 ->
                "Good progress this week. Try completing one more achievable mission next week."

            else ->
                "You have made a start. Choose one simple mission next week and focus on completing it."
        }
    }

    private fun formatNumber(value: Double): String {
        return String.format(Locale.US, "%.1f", value)
    }
}