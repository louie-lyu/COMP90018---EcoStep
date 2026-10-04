package com.ecostep.app.algorithm

import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.testing.testJourney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EmissionFactorsTest {

    @Test
    fun `saving is car emissions minus mode emissions for the same distance`() {
        assertEquals(384.0, EmissionFactors.carbonSavedVersusCarGrams(2_000.0, TransportMode.WALKING), 1e-9)
        assertEquals(384.0, EmissionFactors.carbonSavedVersusCarGrams(2_000.0, TransportMode.CYCLING), 1e-9)
        assertEquals(206.0, EmissionFactors.carbonSavedVersusCarGrams(2_000.0, TransportMode.PUBLIC_TRANSPORT), 1e-9)
        assertEquals(0.0, EmissionFactors.carbonSavedVersusCarGrams(2_000.0, TransportMode.CAR), 1e-9)
    }

    @Test
    fun `unknown mode saves nothing`() {
        assertEquals(0.0, EmissionFactors.carbonSavedVersusCarGrams(5_000.0, TransportMode.UNKNOWN), 0.0)
    }

    @Test
    fun `invalid distances are rejected`() {
        for (bad in listOf(Double.NaN, Double.NEGATIVE_INFINITY, -1.0)) {
            assertThrows(IllegalArgumentException::class.java) {
                EmissionFactors.carbonSavedVersusCarGrams(bad, TransportMode.WALKING)
            }
        }
    }

    @Test
    fun `saving matches the carbon calculator's car-baseline alternative`() {
        val carJourney = testJourney(distanceMeters = 3_500.0, transportMode = TransportMode.CAR)
        val alternative = DefaultCarbonCalculator().calculate(carJourney)
            .lowerCarbonAlternatives
            .single { it.mode == TransportMode.PUBLIC_TRANSPORT }

        assertEquals(
            alternative.savingsGrams,
            EmissionFactors.carbonSavedVersusCarGrams(3_500.0, TransportMode.PUBLIC_TRANSPORT),
            1e-9,
        )
    }
}
