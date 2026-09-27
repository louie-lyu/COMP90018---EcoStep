package com.ecostep.app.ui.mock

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.JourneyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map


/**
 * Creates a JourneySummary when an active mission ends.
 *
 * TODO(Tracking):
 * Replace this interface with the journey-tracking module's verified
 * journey result when live sensor and location tracking are available.
 */
interface MissionJourneyRecorder {
    suspend fun createPartialJourney(
        mission: MissionPageItem,
    ): String
}

/**
 * Shared temporary in-memory repository used by the UI while the
 * Firebase-backed JourneyRepository is unavailable.
 *
 * It supplies mock journey history and temporarily stores journeys created
 * when an active mission ends, allowing JourneyReviewScreen to load them.
 *
 * TODO(Journeys): Replace this class with the production JourneyRepository
 * provided through AppContainer. The production implementation should save
 * completed or partially completed journeys to Firebase using the signed-in
 * user's ID and real sensor/location tracking data.
 */

class MockJourneyRepository :
    JourneyRepository,
    MissionJourneyRecorder {

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

    override suspend fun createPartialJourney(
        mission: MissionPageItem,
    ): String {
        val currentTimeMillis = System.currentTimeMillis()
        val journeyId = "partial_mission_$currentTimeMillis"

        /*
         * Temporary partial journey used only for the UI prototype.
         *
         * TODO(Tracking):
         * Replace the mock coordinates, duration and distance with verified
         * values from the sensor and location-tracking modules.
         */
        val partialJourney = JourneySummary(
            journeyId = journeyId,
            userId = "mock_user",
            startLocation = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
            endLocation = GeoPoint(
                latitude = -37.8200,
                longitude = 144.9700,
            ),
            startTimeMillis =
                currentTimeMillis - 10 * 60_000L,
            endTimeMillis = currentTimeMillis,
            distanceMeters = 1500.0,
            // The mission mode is the user's planned transport mode,
            // not a sensor-verified result.
            // TODO(Tracking): Replace it with the transport mode detected
            // by sensors and confirmed by the user during journey review.
            transportMode =
                mission.mission.transportLabel
                    .toTransportMode(),
        )

        saveJourney(partialJourney)

        return journeyId
    }
}

private fun String.toTransportMode(): TransportMode =
    when (trim().lowercase()) {
        "walking", "walk" ->
            TransportMode.WALKING

        "cycling", "cycle", "bike", "bicycle" ->
            TransportMode.CYCLING

        "public transport", "public_transport" ->
            TransportMode.PUBLIC_TRANSPORT

        "car", "driving" ->
            TransportMode.CAR

        else ->
            TransportMode.UNKNOWN
    }