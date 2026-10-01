package com.ecostep.app.algorithm

import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.TransportResult
import com.ecostep.app.data.model.WeatherData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DefaultMissionPromptBuilderTest {

    private val builder = DefaultMissionPromptBuilder()

    @Test
    fun `includes verified journey information and alternatives`() {
        val prompt = builder.buildPrompt(
            createContext(),
        )

        assertTrue(prompt.contains("CAR"))
        assertTrue(prompt.contains("5.0 km"))
        assertTrue(prompt.contains("15 minutes"))
        assertTrue(prompt.contains("Clear"))
        assertTrue(prompt.contains("Route 96"))
        assertTrue(prompt.contains("CYCLING: 900 g CO2 saving"))
        assertTrue(
            prompt.contains(
                "PUBLIC_TRANSPORT: 500 g CO2 saving",
            ),
        )
    }

    @Test
    fun `requests a structured JSON response`() {
        val prompt = builder.buildPrompt(
            createContext(),
        )

        assertTrue(prompt.contains("\"recommendedMode\""))
        assertTrue(prompt.contains("\"explanation\""))
        assertTrue(prompt.contains("\"confidence\""))
        assertTrue(prompt.contains("\"notificationTitle\""))
        assertTrue(prompt.contains("\"notificationMessage\""))
        assertTrue(prompt.contains("Return JSON only"))
    }

    @Test
    fun `does not include private identifiers or coordinates`() {
        val prompt = builder.buildPrompt(
            createContext(),
        )

        assertFalse(prompt.contains("user-secret"))
        assertFalse(prompt.contains("journey-secret"))
        assertFalse(prompt.contains("-37.8136"))
        assertFalse(prompt.contains("144.9631"))
        assertFalse(prompt.contains("-37.8100"))
        assertFalse(prompt.contains("144.9700"))
    }

    @Test
    fun `rejects a context without valid alternatives`() {
        val context = createContext(
            alternatives = listOf(
                CarbonAlternative(
                    mode = TransportMode.UNKNOWN,
                    savingsGrams = 900.0,
                ),
                CarbonAlternative(
                    mode = TransportMode.CYCLING,
                    savingsGrams = Double.NaN,
                ),
                CarbonAlternative(
                    mode = TransportMode.WALKING,
                    savingsGrams = 0.0,
                ),
            ),
        )

        try {
            builder.buildPrompt(context)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected result.
        }
    }

    private fun createContext(
        alternatives: List<CarbonAlternative> = listOf(
            CarbonAlternative(
                mode = TransportMode.PUBLIC_TRANSPORT,
                savingsGrams = 500.0,
            ),
            CarbonAlternative(
                mode = TransportMode.CYCLING,
                savingsGrams = 900.0,
            ),
        ),
    ): MissionContext {
        val journey = JourneySummary(
            journeyId = "journey-secret",
            userId = "user-secret",
            startLocation = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
            endLocation = GeoPoint(
                latitude = -37.8100,
                longitude = 144.9700,
            ),
            startTimeMillis = 1_000L,
            endTimeMillis = 901_000L,
            distanceMeters = 5_000.0,
            transportMode = TransportMode.CAR,
        )

        val recentJourney = journey.copy(
            journeyId = "recent-secret",
            transportMode = TransportMode.CAR,
        )

        return MissionContext(
            journey = journey,
            transportResult = TransportResult(
                mode = TransportMode.CAR,
                confidence = 90.0,
                alternativesConsidered = listOf(
                    TransportMode.PUBLIC_TRANSPORT,
                    TransportMode.CYCLING,
                ),
            ),
            carbonResult = CarbonResult(
                emissionsGrams = 1_200.0,
                lowerCarbonAlternatives = alternatives,
            ),
            route = RouteInfo(
                mode = TransportMode.CAR,
                distanceMeters = 5_000.0,
                durationSeconds = 900L,
            ),
            weather = WeatherData(
                temperatureCelsius = 20.0,
                conditions = "Clear",
            ),
            publicTransportOptions = listOf(
                PublicTransportInfo(
                    line = "Route 96",
                    departureTimeMillis = 2_000L,
                    estimatedDurationSeconds = 1_080L,
                ),
            ),
            recentJourneyHistory = listOf(recentJourney),
        )
    }
}