package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode

/**
 * Aggregated weekly coaching result.
 *
 * It contains only summary information and does not expose locations,
 * raw sensor readings or individual journey routes.
 */
data class WeeklyCoachReport(
    val acceptedMissions: Int,
    val completedMissions: Int,
    val completionRate: Double,
    val totalCarbonSavingGrams: Double,
    val mostUsedCompletedMode: TransportMode?,
    val summary: String,
    val aiPrompt: String,
    val fallbackMessage: String,
)

interface WeeklyCoach {

    /**
     * Generates a report for missions whose timestamps are inside the
     * supplied weekly period, including both boundary timestamps.
     */
    fun generate(
        results: List<MissionResult>,
        weekStartMillis: Long,
        weekEndMillis: Long,
    ): WeeklyCoachReport
}