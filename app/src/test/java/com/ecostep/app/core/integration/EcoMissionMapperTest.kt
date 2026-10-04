package com.ecostep.app.core.integration

import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.algorithm.RecurringJourneyPattern
import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RecurrenceType
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.testing.testJourney
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EcoMissionMapperTest {

    private val pattern = RecurringJourneyPattern(
        occurrenceCount = 3,
        typicalDepartureMinuteOfDay = 8 * 60 + 15,
        averageDurationMinutes = 15,
        activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        usualTransportMode = TransportMode.CAR,
    )

    private val alternatives = listOf(
        CarbonAlternative(TransportMode.PUBLIC_TRANSPORT, 206.0),
        CarbonAlternative(TransportMode.CYCLING, 384.0),
    )

    private val ecoMission = EcoMission(
        missionId = "ai-j1-cycling",
        recommendedMode = TransportMode.CYCLING,
        estimatedCarbonSavingGrams = 384.0,
        explanation = "  Ride instead of driving.  ",
        confidence = 80.0,
    )

    private fun map(today: LocalDate = LocalDate.of(2026, 10, 6)) = EcoMissionMapper.toMission(
        ecoMission = ecoMission,
        pattern = pattern,
        referenceJourney = testJourney(distanceMeters = 2_000.0, transportMode = TransportMode.CAR),
        startLabel = "Home",
        destinationLabel = "Uni",
        alternatives = alternatives,
        ecoPointsCalculator = DefaultEcoPointsCalculator(),
        zoneId = ZoneId.of("Australia/Melbourne"),
        today = today,
    )

    @Test
    fun `schedule follows the detected pattern`() {
        // 2026-10-06 is a Tuesday; the next active day is Wednesday.
        val mission = map()

        assertEquals("ai-j1-cycling", mission.missionId)
        assertEquals(MissionStatus.SUGGESTED, mission.status)
        assertEquals(RecurrenceType.WEEKLY, mission.recurrence.type)
        assertEquals(setOf(1, 3), mission.recurrence.daysOfWeek)
        assertEquals("Australia/Melbourne", mission.recurrence.timezone)
        assertEquals(495, mission.scheduledMinuteOfDay)
        assertEquals("2026-10-07", mission.nextOccurrenceDate)
        assertEquals("2026-10-05", map(LocalDate.of(2026, 10, 5)).nextOccurrenceDate)
    }

    @Test
    fun `mode, text and estimates come from verified values`() {
        val mission = map()

        assertEquals("Home → Uni", mission.title)
        assertEquals("Ride instead of driving.", mission.description)
        assertEquals(TransportMode.CYCLING, mission.targetTransportMode)
        assertEquals(2_000.0, mission.targetDistanceMeters!!, 1e-9)
        assertEquals(TransportMode.CYCLING, mission.estimates.first().mode)
        // 384 g -> 38 base + 15 cycling bonus.
        assertEquals(53, mission.estimates.first().estimatedEcoPoints)
        assertEquals(384.0, mission.estimates.first().estimatedCarbonSavedGrams, 1e-9)
        assertEquals(2, mission.estimates.size)
    }

    @Test
    fun `a mode outside the verified alternatives is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            EcoMissionMapper.toMission(
                ecoMission = ecoMission.copy(recommendedMode = TransportMode.WALKING),
                pattern = pattern,
                referenceJourney = testJourney(),
                startLabel = "Home",
                destinationLabel = "Uni",
                alternatives = alternatives,
                ecoPointsCalculator = DefaultEcoPointsCalculator(),
                zoneId = ZoneId.of("UTC"),
                today = LocalDate.of(2026, 10, 6),
            )
        }
    }
}
