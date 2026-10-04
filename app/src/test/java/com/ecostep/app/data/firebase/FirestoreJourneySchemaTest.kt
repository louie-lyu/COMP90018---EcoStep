package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.requireValidForWrite
import com.ecostep.app.testing.testJourney
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreJourneySchemaTest {

    private val serverTimestamp = Any()

    /** A document exactly as schema v1 (pre-migration app versions) wrote it. */
    private fun schemaV1Document(transportMode: Any? = "CAR"): MutableMap<String, Any?> =
        mutableMapOf(
            "journeyId" to "legacy",
            "userId" to "user-a",
            "startLocation" to mapOf("latitude" to -37.79, "longitude" to 144.96),
            "endLocation" to mapOf("latitude" to -37.81, "longitude" to 144.97),
            "startTimeMillis" to 1_757_800_000_000L,
            "endTimeMillis" to 1_757_801_200_000L,
            "distanceMeters" to 2600L,
            "transportMode" to transportMode,
        )

    @Test
    fun `schema v1 document decodes with safe defaults`() {
        val journey = schemaV1Document().toJourneySummary("legacy")

        assertEquals(TransportMode.CAR, journey.transportMode)
        assertEquals(2600.0, journey.distanceMeters, 0.0)
        assertNull(journey.detectedTransportMode)
        assertNull(journey.confirmedTransportMode)
        assertEquals(JourneyConfirmationStatus.PENDING, journey.confirmationStatus)
        assertNull(journey.carbonSavedGrams)
        assertNull(journey.ecoPoints)
        assertNull(journey.linkedMissionId)
        assertNull(journey.routePolyline)
        assertNull(journey.createdAtMillis)
        assertNull(journey.sensorFeatures)
    }

    @Test
    fun `schema v2 round-trips every client field`() {
        val journey = testJourney(linkedMissionId = "m1").copy(
            detectedTransportMode = TransportMode.PUBLIC_TRANSPORT,
            confirmedTransportMode = TransportMode.CYCLING,
            transportMode = TransportMode.CYCLING,
            confirmationStatus = JourneyConfirmationStatus.CONFIRMED,
        )

        val map = journey.toFirestoreMap()
        val decoded = map.toJourneySummary(journey.journeyId)

        assertEquals(2, map["schemaVersion"])
        assertEquals("confirmed", map["confirmationStatus"])
        assertEquals(journey, decoded)
    }

    @Test
    fun `backend fields and server timestamps decode from a v2 document`() {
        val map = testJourney().toFirestoreMap().toMutableMap()
        map["carbonSavedGrams"] = 384L
        map["ecoPoints"] = 58L
        map["createdAt"] = Date(1_000L)
        map["updatedAt"] = 2_000L

        val decoded = map.toJourneySummary("j1")

        assertEquals(384.0, decoded.carbonSavedGrams!!, 0.0)
        assertEquals(58, decoded.ecoPoints)
        assertEquals(1_000L, decoded.createdAtMillis)
        assertEquals(2_000L, decoded.updatedAtMillis)
    }

    @Test
    fun `pending offline server timestamp reads as null`() {
        val map = testJourney().toFirestoreMap().toMutableMap()
        map["createdAt"] = null
        map["updatedAt"] = null

        val decoded = map.toJourneySummary("j1")

        assertNull(decoded.createdAtMillis)
        assertNull(decoded.updatedAtMillis)
    }

    @Test
    fun `mode read priority is confirmed then legacy then detected then UNKNOWN`() {
        val confirmed = schemaV1Document("CAR").apply {
            put("confirmedTransportMode", "WALKING")
            put("detectedTransportMode", "CAR")
        }
        val legacyOnly = schemaV1Document("PUBLIC_TRANSPORT")
        val detectedOnly = schemaV1Document(null).apply { put("detectedTransportMode", "CYCLING") }
        val nothing = schemaV1Document(null)

        assertEquals(TransportMode.WALKING, confirmed.toJourneySummary("a").transportMode)
        assertEquals(TransportMode.PUBLIC_TRANSPORT, legacyOnly.toJourneySummary("b").transportMode)
        assertEquals(TransportMode.CYCLING, detectedOnly.toJourneySummary("c").transportMode)
        assertEquals(TransportMode.UNKNOWN, nothing.toJourneySummary("d").transportMode)
    }

    @Test
    fun `illegal transport mode falls back to UNKNOWN`() {
        val map = schemaV1Document("HOVERBOARD").apply {
            put("confirmedTransportMode", 42)
            put("confirmationStatus", "archived")
        }

        val decoded = map.toJourneySummary("x")

        assertEquals(TransportMode.UNKNOWN, decoded.transportMode)
        assertNull(decoded.confirmedTransportMode)
        assertEquals(JourneyConfirmationStatus.PENDING, decoded.confirmationStatus)
    }

    @Test
    fun `NaN Infinity and negative distances are rejected on read`() {
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0)) {
            val map = schemaV1Document().apply { put("distanceMeters", bad) }
            assertThrows(IllegalStateException::class.java) { map.toJourneySummary("x") }
            assertNull(map.toJourneySummaryOrNull("x"))
        }
    }

    @Test
    fun `invalid backend values are ignored instead of shown`() {
        val map = schemaV1Document().apply {
            put("carbonSavedGrams", Double.NaN)
            put("ecoPoints", -5)
        }

        val decoded = map.toJourneySummary("x")

        assertNull(decoded.carbonSavedGrams)
        assertNull(decoded.ecoPoints)
    }

    @Test
    fun `missing journeyId falls back to the document ID`() {
        val map = schemaV1Document().apply { remove("journeyId") }

        assertEquals("doc-7", map.toJourneySummary("doc-7").journeyId)
    }

    @Test
    fun `client maps never carry backend-only fields`() {
        val journey = testJourney().copy(carbonSavedGrams = 999.0, ecoPoints = 500)

        val createMap = journey.toFirestoreCreateMap(serverTimestamp)

        assertFalse(createMap.containsKey("carbonSavedGrams"))
        assertFalse(createMap.containsKey("ecoPoints"))
        assertSame(serverTimestamp, createMap["createdAt"])
        assertSame(serverTimestamp, createMap["updatedAt"])
    }

    @Test
    fun `confirmation update touches only confirmation fields`() {
        val update = confirmationUpdate(TransportMode.WALKING, serverTimestamp)

        assertEquals(
            setOf("confirmedTransportMode", "transportMode", "confirmationStatus", "updatedAt"),
            update.keys,
        )
        assertEquals("WALKING", update["confirmedTransportMode"])
        assertEquals("confirmed", update["confirmationStatus"])
    }

    @Test
    fun `write validation rejects non-finite and negative values`() {
        val invalid = listOf(
            testJourney(distanceMeters = Double.NaN),
            testJourney(distanceMeters = Double.POSITIVE_INFINITY),
            testJourney(distanceMeters = -10.0),
            testJourney(distanceMeters = 2_000_000.0),
            testJourney().copy(ecoPoints = -1),
            testJourney().copy(carbonSavedGrams = Double.NaN),
            testJourney().copy(carbonSavedGrams = -0.5),
            testJourney().copy(journeyId = ""),
            testJourney().copy(journeyId = "a/b"),
            testJourney().copy(endTimeMillis = 1L),
        )

        invalid.forEach { journey ->
            assertThrows(IllegalArgumentException::class.java) { journey.requireValidForWrite() }
        }
        testJourney().requireValidForWrite()
        assertTrue(true)
    }
}
