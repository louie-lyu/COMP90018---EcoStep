package com.ecostep.app.data.model

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the test infra (JUnit + kotlinx.serialization) and the mock-data fixture from
 * docs/DEPENDENCIES.md ("Using Mock Data") both work, so other module owners can rely on them.
 */
class JourneySummaryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `journey summary round-trips through serialization`() {
        val journey = JourneySummary(
            journeyId = "j1",
            userId = "u1",
            startLocation = GeoPoint(-37.80, 144.90),
            endLocation = GeoPoint(-37.81, 145.00),
            startTimeMillis = 0L,
            endTimeMillis = 900_000L,
            distanceMeters = 1500.0,
            transportMode = TransportMode.WALKING,
        )

        val encoded = json.encodeToString(JourneySummary.serializer(), journey)
        val decoded = json.decodeFromString(JourneySummary.serializer(), encoded)

        assertEquals(journey, decoded)
    }

    @Test
    fun `mock_journeys fixture parses into JourneySummary list`() {
        val file = File("src/test/assets/mock_journeys.json")
        assertTrue("expected ${file.absolutePath} to exist", file.exists())

        val journeys = json.decodeFromString<List<JourneySummary>>(file.readText())

        assertEquals(3, journeys.size)
        assertEquals("mock_1", journeys.first().journeyId)
        assertEquals(TransportMode.WALKING, journeys.first().transportMode)
    }
}
