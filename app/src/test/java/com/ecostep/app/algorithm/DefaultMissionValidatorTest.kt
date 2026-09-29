package com.ecostep.app.algorithm

import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultMissionValidatorTest {

    private val validator = DefaultMissionValidator()

    private fun validMission() = EcoMission(
        missionId = "mission-1",
        recommendedMode = TransportMode.CYCLING,
        estimatedCarbonSavingGrams = 500.0,
        explanation = "Cycling can reduce carbon emissions.",
        confidence = 80.0,
    )

    @Test
    fun `accepts a valid mission`() {
        assertTrue(validator.isValid(validMission()))
    }

    @Test
    fun `rejects a blank mission id`() {
        val mission = validMission().copy(missionId = " ")
        assertFalse(validator.isValid(mission))
    }

    @Test
    fun `rejects an unknown transport mode`() {
        val mission = validMission().copy(
            recommendedMode = TransportMode.UNKNOWN,
        )
        assertFalse(validator.isValid(mission))
    }

    @Test
    fun `rejects an invalid carbon saving`() {
        val invalidValues = listOf(
            0.0,
            -1.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
        )

        invalidValues.forEach { value ->
            val mission = validMission().copy(
                estimatedCarbonSavingGrams = value,
            )
            assertFalse(validator.isValid(mission))
        }
    }

    @Test
    fun `rejects a blank explanation`() {
        val mission = validMission().copy(explanation = " ")
        assertFalse(validator.isValid(mission))
    }

    @Test
    fun `rejects an invalid confidence`() {
        val invalidValues = listOf(
            -1.0,
            101.0,
            Double.NaN,
            Double.POSITIVE_INFINITY,
        )

        invalidValues.forEach { value ->
            val mission = validMission().copy(confidence = value)
            assertFalse(validator.isValid(mission))
        }
    }
}