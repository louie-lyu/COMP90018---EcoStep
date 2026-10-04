package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionModeEstimate
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RecurrenceType
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FirestoreMissionMapperTest {

    private val mission = Mission(
        missionId = "m1",
        title = "Home → Uni",
        description = "Frequent route",
        startLabel = "Home",
        destinationLabel = "Uni",
        targetTransportMode = TransportMode.CYCLING,
        targetDistanceMeters = 4_200.0,
        status = MissionStatus.ACCEPTED,
        recurrence = MissionRecurrence(RecurrenceType.WEEKLY, 1, setOf(1, 3, 5), "Australia/Melbourne"),
        scheduledMinuteOfDay = 8 * 60 + 30,
        nextOccurrenceDate = "2026-10-05",
        estimates = listOf(MissionModeEstimate(TransportMode.CYCLING, 100, 520.0)),
    )

    @Test
    fun `mission round-trips through Firestore map`() {
        val map = mission.toFirestoreMap()

        assertEquals(1, map["schemaVersion"])
        assertEquals("accepted", map["status"])
        assertFalse(map.containsKey("createdAt"))
        assertEquals(mission, map.toMission("m1"))
    }

    @Test
    fun `unknown status or missing title drops the document`() {
        val map = mission.toFirestoreMap().toMutableMap()

        assertNull((map + ("status" to "paused")).toMission("m1"))
        assertNull((map - "title").toMission("m1"))
    }

    @Test
    fun `corrupt optional fields decode to safe defaults`() {
        val map = mission.toFirestoreMap().toMutableMap().apply {
            put("recurrence", mapOf("type" to "fortnightly", "daysOfWeek" to listOf(0, 3, 9, "x")))
            put("targetTransportMode", "JETPACK")
            put("targetDistanceMeters", -5.0)
            put("nextOccurrenceDate", "next monday")
        }

        val decoded = map.toMission("m1")!!

        assertEquals(RecurrenceType.NONE, decoded.recurrence.type)
        assertEquals(setOf(3), decoded.recurrence.daysOfWeek)
        assertEquals(TransportMode.UNKNOWN, decoded.targetTransportMode)
        assertNull(decoded.targetDistanceMeters)
        assertNull(decoded.nextOccurrenceDate)
    }

    @Test
    fun `invalid missions are rejected before writing`() {
        listOf(
            mission.copy(title = ""),
            mission.copy(missionId = "a/b"),
            mission.copy(scheduledMinuteOfDay = 24 * 60),
            mission.copy(targetDistanceMeters = Double.NaN),
            mission.copy(estimates = listOf(MissionModeEstimate(TransportMode.WALKING, -1, 0.0))),
            mission.copy(nextOccurrenceDate = "05/10/2026"),
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { invalid.requireValidForWrite() }
        }
    }

    @Test
    fun `occurrence merge never carries backend result fields`() {
        val document = occurrenceMergeDocument(
            missionId = "m1",
            occurrenceDate = "2026-10-05",
            fields = mapOf("completed" to true, "linkedJourneyId" to "j1"),
            atMillis = 5L,
            serverTimestamp = Any(),
        )

        assertFalse(document.containsKey("ecoPointsAwarded"))
        assertFalse(document.containsKey("actualCarbonSavingGrams"))
        assertFalse(document.containsKey("actualTransportMode"))
        assertEquals("m1", document["missionId"])
        assertThrows(IllegalArgumentException::class.java) {
            occurrenceMergeDocument("m1", "today", emptyMap(), 5L, Any())
        }
    }

    @Test
    fun `occurrence decodes backend award fields`() {
        val occurrence = mapOf(
            "missionId" to "m1",
            "occurrenceDate" to "2026-10-05",
            "accepted" to true,
            "completed" to true,
            "linkedJourneyId" to "j1",
            "actualTransportMode" to "WALKING",
            "actualCarbonSavingGrams" to 384L,
            "ecoPointsAwarded" to 58L,
            "completedAtMillis" to 7L,
        ).toMissionOccurrence("m1_2026-10-05")!!

        assertEquals(58, occurrence.ecoPointsAwarded)
        assertEquals(TransportMode.WALKING, occurrence.actualTransportMode)
        assertEquals(384.0, occurrence.actualCarbonSavingGrams!!, 0.0)
    }
}
