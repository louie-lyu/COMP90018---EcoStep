package com.ecostep.app.ui.adapters

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.repository.MissionResultRepository
import com.ecostep.app.ui.mock.WeeklyInsightDataSource
import java.time.Instant
import java.time.ZoneId

/**
 * Weekly Insight from the user's real mission occurrences. Skipped and completed occurrences
 * both count, so the coach sees acceptance as well as completion.
 */
class RepositoryWeeklyInsightDataSource(
    private val missionResultRepository: MissionResultRepository,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : WeeklyInsightDataSource {

    override suspend fun getMissionResults(fromMillis: Long): List<MissionResult> {
        val fromDate = Instant.ofEpochMilli(fromMillis).atZone(zoneId()).toLocalDate().toString()
        return missionResultRepository.getOccurrences(fromDate)
            .map { it.toMissionResult() }
            .filter { it.timestampMillis > 0L }
    }
}
