package com.ecostep.app.data.repository

import com.ecostep.app.data.model.JourneySummary
import kotlinx.coroutines.flow.Flow

/**
 * Owner: Zongcheng Jiang (Sensors, Journey Tracking, Auth & Database module).
 * Due: JourneySummary by 20 Sep, per docs/WORK_PLAN.md.
 *
 * Implemented against GPS/accelerometer/gyroscope sensor data, Firebase Auth and Firestore.
 * Other modules build against this interface, not the concrete implementation, so they can
 * start work with mock data before it's ready (see docs/DEPENDENCIES.md "Using Mock Data").
 */
interface JourneyRepository {
    fun observeJourneyHistory(userId: String): Flow<List<JourneySummary>>

    suspend fun getJourney(journeyId: String): JourneySummary?

    suspend fun saveJourney(journey: JourneySummary)
}
