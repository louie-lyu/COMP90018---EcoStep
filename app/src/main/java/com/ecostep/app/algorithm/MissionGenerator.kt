package com.ecostep.app.algorithm

import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.MissionContext

/**
 * Owner: Duo Lyu. Due: 20 Sep, per docs/WORK_PLAN.md ("AI EcoMission Builder").
 *
 * Builds the AI prompt from a [MissionContext] and returns a validated [EcoMission],
 * or a non-AI fallback mission if the AI response fails [MissionValidator].
 */
interface MissionGenerator {
    suspend fun generateMission(context: MissionContext): EcoMission
}
