package com.ecostep.app.algorithm

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCarbonCalculatorTest {

    private val calculator = DefaultCarbonCalculator()

    @Test
    fun `car journey calculates emissions correctly`() {
        val result = calculator.calculate(
            createJourney(
                distanceMeters = 10_000.0,
                mode = TransportMode.CAR,
            ),
        )

        assertEquals(1920.0, result.emissionsGrams, 0.001)
    }

    @Test
    fun `car journey provides lower carbon alternatives`() {
        val result = calculator.calculate(
            createJourney(
                distanceMeters = 10_000.0,
                mode = TransportMode.CAR,
            ),
        )

        val publicTransport =
            result.lowerCarbonAlternatives.first {
                it.mode == TransportMode.PUBLIC_TRANSPORT
            }

        assertEquals(1030.0, publicTransport.savingsGrams, 0.001)

        assertTrue(
            result.lowerCarbonAlternatives.all {
                it.savingsGrams >= 0.0
            },
        )
    }

    @Test
    fun `zero distance returns zero emissions`() {
        val result = calculator.calculate(
            createJourney(
                distanceMeters = 0.0,
                mode = TransportMode.CAR,
            ),
        )

        assertEquals(0.0, result.emissionsGrams, 0.001)
    }

    @Test
    fun `negative distance is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(
                createJourney(
                    distanceMeters = -100.0,
                    mode = TransportMode.CAR,
                ),
            )
        }
    }

    @Test
    fun `positive infinity distance is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            calculator.calculate(
                createJourney(
                    distanceMeters = Double.POSITIVE_INFINITY,
                    mode = TransportMode.CAR,
                ),
            )
        }
    }

    @Test
    fun `unknown transport mode returns safe empty result`() {
        val result = calculator.calculate(
            createJourney(
                distanceMeters = 5_000.0,
                mode = TransportMode.UNKNOWN,
            ),
        )

        assertEquals(0.0, result.emissionsGrams, 0.001)
        assertTrue(result.lowerCarbonAlternatives.isEmpty())
    }

    private fun createJourney(
        distanceMeters: Double,
        mode: TransportMode,
    ): JourneySummary {
        return JourneySummary(
            journeyId = "test_journey",
            userId = "test_user",
            startLocation = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
            endLocation = GeoPoint(
                latitude = -37.8200,
                longitude = 144.9700,
            ),
            startTimeMillis = 1_000L,
            endTimeMillis = 2_000L,
            distanceMeters = distanceMeters,
            transportMode = mode,
        )
    }
}
