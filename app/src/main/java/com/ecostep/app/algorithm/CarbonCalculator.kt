package com.ecostep.app.algorithm

import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.JourneySummary

/**
 * Owner: Duo Lyu. Due: 14 Sep, per docs/WORK_PLAN.md ("Carbon Calculator") —
 * this module's first deliverable.
 *
 * Calculates emissions for the journey's transport mode and lower-carbon alternatives.
 */
interface CarbonCalculator {
    fun calculate(journey: JourneySummary): CarbonResult
}
