package com.ecostep.app.core.integration

import com.ecostep.app.algorithm.EcoPointsCalculator
import com.ecostep.app.algorithm.RecurringJourneyPattern
import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionModeEstimate
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.MissionStatus
import java.time.LocalDate
import java.time.ZoneId

/**
 * Turns a generated [EcoMission] into the persisted [Mission] the Missions screen shows as a
 * suggestion. Only the mode and explanation come from the generator; schedule, distance and
 * estimates come from the detected pattern and the shared calculators.
 */
object EcoMissionMapper {

    private const val MAX_TITLE_LENGTH = 120
    private const val MAX_DESCRIPTION_LENGTH = 500

    fun toMission(
        ecoMission: EcoMission,
        pattern: RecurringJourneyPattern,
        referenceJourney: JourneySummary,
        startLabel: String,
        destinationLabel: String,
        /** Verified lower-carbon options; the recommended mode must be among them. */
        alternatives: List<CarbonAlternative>,
        ecoPointsCalculator: EcoPointsCalculator,
        zoneId: ZoneId,
        today: LocalDate,
    ): Mission {
        require(alternatives.any { it.mode == ecoMission.recommendedMode }) {
            "The recommended mode is not a verified alternative."
        }
        val recurrence = MissionRecurrence.weekly(pattern.activeDays, zoneId.id)
        val estimates = alternatives
            .sortedByDescending { it.mode == ecoMission.recommendedMode }
            .map { alternative ->
                MissionModeEstimate(
                    mode = alternative.mode,
                    estimatedEcoPoints = ecoPointsCalculator.calculatePoints(
                        MissionResult(
                            missionId = ecoMission.missionId,
                            accepted = true,
                            completed = true,
                            actualTransportMode = alternative.mode,
                            actualCarbonSavingGrams = alternative.savingsGrams,
                            timestampMillis = referenceJourney.endTimeMillis,
                            actualDistanceMeters = referenceJourney.distanceMeters,
                        ),
                    ),
                    estimatedCarbonSavedGrams = alternative.savingsGrams,
                )
            }

        return Mission(
            missionId = ecoMission.missionId,
            title = "$startLabel → $destinationLabel".take(MAX_TITLE_LENGTH),
            description = ecoMission.explanation.trim().take(MAX_DESCRIPTION_LENGTH),
            startLabel = startLabel,
            destinationLabel = destinationLabel,
            targetTransportMode = ecoMission.recommendedMode,
            targetDistanceMeters = referenceJourney.distanceMeters,
            status = MissionStatus.SUGGESTED,
            recurrence = recurrence,
            scheduledMinuteOfDay = pattern.typicalDepartureMinuteOfDay,
            nextOccurrenceDate = firstOccurrenceOnOrAfter(recurrence, today).toString(),
            estimates = estimates,
        )
    }

    private fun firstOccurrenceOnOrAfter(recurrence: MissionRecurrence, date: LocalDate): LocalDate =
        if (recurrence.occursOn(date)) date else recurrence.nextOccurrenceAfter(date) ?: date
}
