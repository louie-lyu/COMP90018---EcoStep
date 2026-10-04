package com.ecostep.app.sensors.tracking

import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.data.repository.WriteOutcome

data class SavedJourney(
    val journeyId: String,
    val outcome: WriteOutcome,
)

/**
 * First persistence step for a recorded journey: stores the classifier's mode as the detected
 * mode, pending user confirmation, and links the mission that was active while recording.
 */
class RecordedJourneySaver(
    private val journeyRepository: JourneyRepository,
    private val activeMissionIdProvider: () -> String? = { null },
) {
    suspend fun save(classifiedSummary: JourneySummary): SavedJourney {
        val journey = classifiedSummary.copy(
            detectedTransportMode = classifiedSummary.transportMode,
            confirmedTransportMode = null,
            confirmationStatus = JourneyConfirmationStatus.PENDING,
            linkedMissionId = activeMissionIdProvider(),
        )
        return SavedJourney(
            journeyId = journey.journeyId,
            outcome = journeyRepository.createJourney(journey),
        )
    }
}
