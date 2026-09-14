package com.ecostep.app.algorithm

import com.ecostep.app.data.model.EcoMission

/**
 * Owner: Duo Lyu. Due: 23 Sep, per docs/WORK_PLAN.md ("AI Mission Validator").
 *
 * Rejects AI responses that modify verified data (routes, weather, carbon values) rather than
 * just ranking/personalising them, and triggers non-AI fallback generation when validation fails.
 */
interface MissionValidator {
    fun isValid(mission: EcoMission): Boolean
}
