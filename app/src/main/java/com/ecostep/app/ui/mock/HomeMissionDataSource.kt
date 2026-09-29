package com.ecostep.app.ui.mock

import com.ecostep.app.ui.viewmodels.UpcomingMissionUi

/**
 * Supplies the next scheduled mission needed by HomeScreen.
 *
 * TODO(Missions): Replace this interface with the mission module's
 * scheduled mission data source when it becomes available.
 */
interface HomeMissionDataSource {
    suspend fun getUpcomingMission(
        currentTimeMillis: Long,
    ): UpcomingMissionUi?
}

/**
 * Temporary mission data used only while the production mission
 * scheduling implementation is unavailable.
 */
class MockHomeMissionDataSource : HomeMissionDataSource {

    override suspend fun getUpcomingMission(
        currentTimeMillis: Long,
    ): UpcomingMissionUi {
        val startTimeMillis =
            currentTimeMillis + 10 * 60_000L

        return UpcomingMissionUi(
            missionId = "mock_upcoming_mission",
            routeTitle = "University → Home",
            transportLabel = "Public transport + walk",
            startTimeMillis = startTimeMillis,
            endTimeMillis =
                startTimeMillis + 60 * 60_000L,
            estimatedCarbonSavedKg = 1.4,
            estimatedEcoPoints = 120,
        )
    }
}