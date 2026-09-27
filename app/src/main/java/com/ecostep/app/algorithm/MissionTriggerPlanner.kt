package com.ecostep.app.algorithm

data class MissionTrigger(
    val expectedDepartureTimeMillis: Long,
    val notificationTimeMillis: Long,
)

interface MissionTriggerPlanner {

    fun planNextTrigger(
        pattern: RecurringJourneyPattern,
        currentTimeMillis: Long,
    ): MissionTrigger?
}