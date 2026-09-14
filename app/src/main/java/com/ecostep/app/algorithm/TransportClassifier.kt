package com.ecostep.app.algorithm

import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportResult

/**
 * Owner: Duo Lyu (Algorithms, Carbon Calculation, EcoPoints and AI Personalisation module).
 * Due: 20 Sep, per docs/WORK_PLAN.md ("Transport Classification").
 *
 * Classifies transport mode from a journey's sensor-derived data (speed, motion pattern, etc).
 */
interface TransportClassifier {
    suspend fun classify(journey: JourneySummary): TransportResult
}
