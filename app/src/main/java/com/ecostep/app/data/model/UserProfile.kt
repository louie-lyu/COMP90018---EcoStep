package com.ecostep.app.data.model

/**
 * Private profile stored at users/{uid}. Email is not part of it: Firebase Auth is the only
 * source of truth for the email address.
 */
data class UserProfile(
    val uid: String,
    val displayName: String,
    val preferences: UserPreferences = UserPreferences(),
    /** Server time of the last change (UTC millis); null while a change is pending offline. */
    val updatedAtMillis: Long? = null,
)

/**
 * Cross-device preferences, synced through Firestore with field-level merge and
 * last-write-wins. Device-only state (permissions, caches) stays in DataStore.
 */
data class UserPreferences(
    val routineLearningEnabled: Boolean = true,
    val missionNotificationsEnabled: Boolean = true,
    val defaultReminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
    val automaticJourneyDetectionEnabled: Boolean = true,
    val communityRankingEnabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_REMINDER_MINUTES = 60
        const val MAX_REMINDER_MINUTES = 1_440
    }
}

/** One preference change; each maps to a single Firestore field so devices merge per field. */
sealed interface PreferenceUpdate {
    data class RoutineLearning(val enabled: Boolean) : PreferenceUpdate
    data class MissionNotifications(val enabled: Boolean) : PreferenceUpdate
    data class DefaultReminderMinutes(val minutes: Int) : PreferenceUpdate
    data class AutomaticJourneyDetection(val enabled: Boolean) : PreferenceUpdate
    data class CommunityRanking(val enabled: Boolean) : PreferenceUpdate
}

const val MAX_DISPLAY_NAME_LENGTH = 50

/** Trims and validates a display name; throws [IllegalArgumentException] when unusable. */
fun normalizeDisplayName(displayName: String): String {
    val trimmed = displayName.trim()
    require(trimmed.isNotEmpty()) { "Display name cannot be empty." }
    require(trimmed.length <= MAX_DISPLAY_NAME_LENGTH) {
        "Display name must be at most $MAX_DISPLAY_NAME_LENGTH characters."
    }
    return trimmed
}
