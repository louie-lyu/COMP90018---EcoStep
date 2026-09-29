package com.ecostep.app.algorithm

import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import java.time.DayOfWeek

data class RecurringJourneyPattern(
    val occurrenceCount: Int,
    val typicalDepartureMinuteOfDay: Int,
    val averageDurationMinutes: Long,
    val activeDays: Set<DayOfWeek>,
    val usualTransportMode: TransportMode,
)

interface RecurringJourneyDetector {

    fun detect(
        referenceJourney: JourneySummary,
        journeyHistory: List<JourneySummary>,
    ): RecurringJourneyPattern?
}