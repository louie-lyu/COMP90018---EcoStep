package com.ecostep.app.ui.mock

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import java.util.Calendar

interface WeeklyInsightDataSource {
    fun getMissionResults(): List<MissionResult>
}

/**
 * TODO(Missions/Firebase): Replace with real [MissionResult] history recorded
 */
class MockWeeklyInsightDataSource : WeeklyInsightDataSource {

    override fun getMissionResults(): List<MissionResult> {
        val now = System.currentTimeMillis()

        return listOf(
            MissionResult(
                missionId = "mock-weekly-1",
                accepted = true,
                completed = true,
                actualTransportMode = TransportMode.CYCLING,
                actualCarbonSavingGrams = 520.0,
                timestampMillis = daysAgo(now, 1),
            ),
            MissionResult(
                missionId = "mock-weekly-2",
                accepted = true,
                completed = true,
                actualTransportMode = TransportMode.WALKING,
                actualCarbonSavingGrams = 640.0,
                timestampMillis = daysAgo(now, 2),
            ),
            MissionResult(
                missionId = "mock-weekly-3",
                accepted = true,
                completed = true,
                actualTransportMode = TransportMode.CYCLING,
                actualCarbonSavingGrams = 480.0,
                timestampMillis = daysAgo(now, 3),
            ),
            MissionResult(
                missionId = "mock-weekly-4",
                accepted = true,
                completed = false,
                actualTransportMode = null,
                actualCarbonSavingGrams = null,
                timestampMillis = daysAgo(now, 4),
            ),
            MissionResult(
                missionId = "mock-weekly-5",
                accepted = true,
                completed = true,
                actualTransportMode = TransportMode.PUBLIC_TRANSPORT,
                actualCarbonSavingGrams = 310.0,
                timestampMillis = daysAgo(now, 5),
            ),
        )
    }

    private fun daysAgo(fromMillis: Long, days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = fromMillis
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return calendar.timeInMillis
    }
}
