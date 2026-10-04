package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.UserPreferences
import com.ecostep.app.data.model.normalizeDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FirestoreProfileMapperTest {

    private val serverTimestamp = Any()

    @Test
    fun `empty document decodes to defaults`() {
        val profile = emptyMap<String, Any?>().toUserProfile("u1")

        assertEquals("u1", profile.uid)
        assertEquals(DEFAULT_DISPLAY_NAME, profile.displayName)
        assertEquals(UserPreferences(), profile.preferences)
        assertNull(profile.updatedAtMillis)
    }

    @Test
    fun `mistyped preference fields fall back per field`() {
        val profile = mapOf(
            "displayName" to "  Ada  ",
            "preferences" to mapOf(
                "routineLearningEnabled" to "yes",
                "missionNotificationsEnabled" to false,
                "defaultReminderMinutes" to 99_999L,
                "communityRankingEnabled" to false,
            ),
        ).toUserProfile("u1")

        assertEquals("Ada", profile.displayName)
        assertEquals(true, profile.preferences.routineLearningEnabled)
        assertEquals(false, profile.preferences.missionNotificationsEnabled)
        assertEquals(UserPreferences.MAX_REMINDER_MINUTES, profile.preferences.defaultReminderMinutes)
        assertEquals(false, profile.preferences.communityRankingEnabled)
    }

    @Test
    fun `new profile document has schema version defaults and timestamps without email`() {
        val document = newProfileDocument("Ada", serverTimestamp)

        assertEquals(1, document["schemaVersion"])
        assertEquals("Ada", document["displayName"])
        assertEquals(UserPreferences().toFirestoreMap(), document["preferences"])
        assertEquals(serverTimestamp, document["createdAt"])
        assertFalse(document.containsKey("email"))
    }

    @Test
    fun `preference merge document writes exactly one preference field`() {
        val document = preferenceMergeDocument(PreferenceUpdate.CommunityRanking(false), serverTimestamp)

        assertEquals(setOf("preferences", "updatedAt"), document.keys)
        assertEquals(mapOf("communityRankingEnabled" to false), document["preferences"])
    }

    @Test
    fun `reminder minutes outside 0 to 1440 are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PreferenceUpdate.DefaultReminderMinutes(-1).toFirestoreField()
        }
        assertThrows(IllegalArgumentException::class.java) {
            PreferenceUpdate.DefaultReminderMinutes(1_441).toFirestoreField()
        }
        assertEquals("defaultReminderMinutes" to 30, PreferenceUpdate.DefaultReminderMinutes(30).toFirestoreField())
    }

    @Test
    fun `display names are trimmed and bounded`() {
        assertEquals("Ada", normalizeDisplayName("  Ada "))
        assertThrows(IllegalArgumentException::class.java) { normalizeDisplayName("   ") }
        assertThrows(IllegalArgumentException::class.java) { normalizeDisplayName("x".repeat(51)) }
    }
}
