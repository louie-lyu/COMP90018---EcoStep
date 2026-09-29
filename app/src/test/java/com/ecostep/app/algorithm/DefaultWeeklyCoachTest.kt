package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultWeeklyCoachTest {

    private val coach = DefaultWeeklyCoach()

    @Test
    fun `calculates weekly totals and most used mode`() {
        val results = listOf(
            mission(
                id = "1",
                mode = TransportMode.WALKING,
                saving = 100.0,
            ),
            mission(
                id = "2",
                mode = TransportMode.WALKING,
                saving = 150.0,
            ),
            mission(
                id = "3",
                mode = TransportMode.CYCLING,
                saving = 200.0,
            ),
            mission(
                id = "4",
                completed = false,
                saving = 500.0,
            ),
        )

        val report = coach.generate(results, 1_000L, 2_000L)

        assertEquals(4, report.acceptedMissions)
        assertEquals(3, report.completedMissions)
        assertEquals(75.0, report.completionRate, 0.001)
        assertEquals(450.0, report.totalCarbonSavingGrams, 0.001)
        assertEquals(TransportMode.WALKING, report.mostUsedCompletedMode)
    }

    @Test
    fun `includes boundary timestamps and excludes other dates`() {
        val results = listOf(
            mission(id = "start", timestamp = 1_000L),
            mission(id = "middle", timestamp = 1_500L),
            mission(id = "end", timestamp = 2_000L),
            mission(id = "before", timestamp = 999L),
            mission(id = "after", timestamp = 2_001L),
        )

        val report = coach.generate(results, 1_000L, 2_000L)

        assertEquals(3, report.acceptedMissions)
        assertEquals(3, report.completedMissions)
        assertEquals(300.0, report.totalCarbonSavingGrams, 0.001)
    }

    @Test
    fun `handles an empty week`() {
        val report = coach.generate(emptyList(), 1_000L, 2_000L)

        assertEquals(0, report.acceptedMissions)
        assertEquals(0, report.completedMissions)
        assertEquals(0.0, report.completionRate, 0.001)
        assertEquals(0.0, report.totalCarbonSavingGrams, 0.001)
        assertNull(report.mostUsedCompletedMode)
        assertTrue(report.summary.isNotBlank())
        assertTrue(report.fallbackMessage.isNotBlank())
    }

    @Test
    fun `does not count unaccepted missions`() {
        val results = listOf(
            mission(
                id = "not-accepted",
                accepted = false,
                completed = true,
                saving = 100.0,
            ),
        )

        val report = coach.generate(results, 1_000L, 2_000L)

        assertEquals(0, report.acceptedMissions)
        assertEquals(0, report.completedMissions)
        assertEquals(0.0, report.completionRate, 0.001)
        assertEquals(0.0, report.totalCarbonSavingGrams, 0.001)
        assertNull(report.mostUsedCompletedMode)
    }

    @Test
    fun `handles accepted missions with none completed`() {
        val results = listOf(
            mission(id = "1", completed = false),
            mission(id = "2", completed = false),
        )

        val report = coach.generate(results, 1_000L, 2_000L)

        assertEquals(2, report.acceptedMissions)
        assertEquals(0, report.completedMissions)
        assertEquals(0.0, report.completionRate, 0.001)
        assertEquals(0.0, report.totalCarbonSavingGrams, 0.001)
        assertNull(report.mostUsedCompletedMode)
        assertTrue(report.fallbackMessage.isNotBlank())
    }

    @Test
    fun `ignores missing and invalid carbon savings`() {
        val results = listOf(
            mission(id = "missing", saving = null),
            mission(id = "nan", saving = Double.NaN),
            mission(id = "positive-infinity", saving = Double.POSITIVE_INFINITY),
            mission(id = "negative-infinity", saving = Double.NEGATIVE_INFINITY),
            mission(id = "negative", saving = -50.0),
            mission(id = "valid", saving = 80.0),
        )

        val report = coach.generate(results, 1_000L, 2_000L)

        assertEquals(80.0, report.totalCarbonSavingGrams, 0.001)
    }

    @Test
    fun `ignores missing and unknown transport modes`() {
        val results = listOf(
            mission(id = "missing", mode = null),
            mission(id = "unknown", mode = TransportMode.UNKNOWN),
        )

        val report = coach.generate(results, 1_000L, 2_000L)

        assertEquals(2, report.completedMissions)
        assertNull(report.mostUsedCompletedMode)
    }

    @Test
    fun `AI prompt contains totals without mission identifiers`() {
        val results = listOf(
            mission(
                id = "private-mission-123",
                saving = 80.0,
            ),
        )

        val report = coach.generate(results, 1_000L, 2_000L)

        assertTrue(report.aiPrompt.contains("Accepted missions: 1"))
        assertTrue(report.aiPrompt.contains("Completed missions: 1"))
        assertTrue(report.aiPrompt.contains("80.0 grams"))
        assertFalse(report.aiPrompt.contains("private-mission-123"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects an end time before the start time`() {
        coach.generate(emptyList(), 2_000L, 1_000L)
    }

    private fun mission(
        id: String,
        accepted: Boolean = true,
        completed: Boolean = true,
        mode: TransportMode? = TransportMode.WALKING,
        saving: Double? = 100.0,
        timestamp: Long = 1_500L,
    ): MissionResult {
        return MissionResult(
            missionId = id,
            accepted = accepted,
            completed = completed,
            actualTransportMode = mode,
            actualCarbonSavingGrams = saving,
            timestampMillis = timestamp,
        )
    }
}