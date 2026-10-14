package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultEcoPointsCalculatorTest {

    private val calculator =
        DefaultEcoPointsCalculator()

    @Test
    fun `completed cycling mission includes base points and bonus`() {
        val points =
            calculator.calculatePoints(
                createMissionResult(
                    carbonSavingGrams = 100.0,
                    transportMode = TransportMode.CYCLING,
                ),
            )

        // 100 g saved by cycling is about 603 m: 10 base + round(15 × 0.603) = 19.
        assertEquals(19, points)
    }

    @Test
    fun `the mode bonus is proportional to distance up to one kilometre`() {
        fun walkingPoints(grams: Double, meters: Double) = calculator.calculatePoints(
            createMissionResult(
                carbonSavingGrams = grams,
                transportMode = TransportMode.WALKING,
            ).copy(actualDistanceMeters = meters),
        )

        // The 26 m walk from the review screen: no longer the full 20-point bonus.
        assertEquals(1, walkingPoints(4.31366, 26.0))
        assertEquals(8 + 10, walkingPoints(82.955, 500.0))
        assertEquals(17 + 20, walkingPoints(165.91, 1_000.0))
        assertEquals(33 + 20, walkingPoints(331.82, 2_000.0))
    }

    @Test
    fun `negative distance is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculatePoints(createMissionResult().copy(actualDistanceMeters = -1.0))
        }
    }

    @Test
    fun `completed car mission receives no transport bonus`() {
        val points =
            calculator.calculatePoints(
                createMissionResult(
                    carbonSavingGrams = 100.0,
                    transportMode = TransportMode.CAR,
                ),
            )

        assertEquals(10, points)
    }

    @Test
    fun `unaccepted mission returns zero points`() {
        val points =
            calculator.calculatePoints(
                createMissionResult(
                    accepted = false,
                ),
            )

        assertEquals(0, points)
    }

    @Test
    fun `incomplete mission returns zero points`() {
        val points =
            calculator.calculatePoints(
                createMissionResult(
                    completed = false,
                ),
            )

        assertEquals(0, points)
    }

    @Test
    fun `missing carbon saving returns zero points`() {
        val points =
            calculator.calculatePoints(
                createMissionResult(
                    carbonSavingGrams = null,
                ),
            )

        assertEquals(0, points)
    }

    @Test
    fun `missing transport mode returns zero points`() {
        val points =
            calculator.calculatePoints(
                createMissionResult(
                    transportMode = null,
                ),
            )

        assertEquals(0, points)
    }

    @Test
    fun `points cannot exceed maximum limit`() {
        val points =
            calculator.calculatePoints(
                createMissionResult(
                    carbonSavingGrams = 10_000.0,
                    transportMode = TransportMode.WALKING,
                ),
            )

        assertEquals(500, points)
    }

    @Test
    fun `negative carbon saving is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculatePoints(
                createMissionResult(
                    carbonSavingGrams = -10.0,
                ),
            )
        }
    }

    @Test
    fun `infinite carbon saving is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculatePoints(
                createMissionResult(
                    carbonSavingGrams = Double.POSITIVE_INFINITY,
                ),
            )
        }
    }

    private fun createMissionResult(
        accepted: Boolean = true,
        completed: Boolean = true,
        transportMode: TransportMode? =
            TransportMode.CYCLING,
        carbonSavingGrams: Double? = 100.0,
    ): MissionResult {
        return MissionResult(
            missionId = "test_mission",
            accepted = accepted,
            completed = completed,
            actualTransportMode = transportMode,
            actualCarbonSavingGrams =
                carbonSavingGrams,
            timestampMillis = 1_000L,
        )
    }
}