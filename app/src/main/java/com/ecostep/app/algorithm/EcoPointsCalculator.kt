package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult

/**
 * Owner: Duo Lyu. Due: 17 Sep, per docs/WORK_PLAN.md ("EcoPoints Calculator").
 *
 * Converts a completed mission's carbon savings into EcoPoints, including bonuses,
 * incomplete-mission handling, and max-limit rules.
 */
interface EcoPointsCalculator {
    fun calculatePoints(missionResult: MissionResult): Int
}
