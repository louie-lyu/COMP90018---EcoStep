package com.ecostep.app.ui.mock

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.JourneyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Temporary repository used by the UI module while the Firebase-backed
 * JourneyRepository implementation is still being developed.
 *
 * Replace this class with the repository provided through AppContainer
 * when the production implementation is available.
 */
class MockJourneyRepository : JourneyRepository {

    private val journeys = MutableStateFlow(
        listOf(
            JourneySummary(
                journeyId = "mock_1",
                userId = "mock_user",
                startLocation = GeoPoint(
                    latitude = -37.80,
                    longitude = 144.90,
                ),
                endLocation = GeoPoint(
                    latitude = -37.81,
                    longitude = 145.00,
                ),
                startTimeMillis = 1757800000000,
                endTimeMillis = 1757800900000,
                distanceMeters = 1500.0,
                transportMode = TransportMode.WALKING,
            ),
            JourneySummary(
                journeyId = "mock_2",
                userId = "mock_user",
                startLocation = GeoPoint(
                    latitude = -37.81,
                    longitude = 144.96,
                ),
                endLocation = GeoPoint(
                    latitude = -37.87,
                    longitude = 145.09,
                ),
                startTimeMillis = 1757803600000,
                endTimeMillis = 1757805400000,
                distanceMeters = 12800.0,
                transportMode = TransportMode.CAR,
            ),
            JourneySummary(
                journeyId = "mock_3",
                userId = "mock_user",
                startLocation = GeoPoint(
                    latitude = -37.82,
                    longitude = 144.95,
                ),
                endLocation = GeoPoint(
                    latitude = -37.80,
                    longitude = 144.97,
                ),
                startTimeMillis = 1757807200000,
                endTimeMillis = 1757807800000,
                distanceMeters = 3200.0,
                transportMode = TransportMode.CYCLING,
            ),
        ),
    )

    override fun observeJourneyHistory(
        userId: String,
    ): Flow<List<JourneySummary>> {
        return journeys.map { journeyList ->
            journeyList.filter { journey ->
                journey.userId == userId
            }
        }
    }

    override suspend fun getJourney(
        journeyId: String,
    ): JourneySummary? {
        return journeys.value.firstOrNull { journey ->
            journey.journeyId == journeyId
        }
    }

    override suspend fun saveJourney(
        journey: JourneySummary,
    ) {
        val currentJourneys = journeys.value.toMutableList()
        val existingIndex = currentJourneys.indexOfFirst {
            it.journeyId == journey.journeyId
        }

        if (existingIndex >= 0) {
            currentJourneys[existingIndex] = journey
        } else {
            currentJourneys.add(journey)
        }

        journeys.value = currentJourneys
    }
}