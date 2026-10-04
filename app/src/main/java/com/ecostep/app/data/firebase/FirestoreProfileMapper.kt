package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.UserPreferences
import com.ecostep.app.data.model.UserProfile

internal const val PROFILE_SCHEMA_VERSION = 1
internal const val DEFAULT_DISPLAY_NAME = "EcoStep User"

/** Tolerant decoder: missing or mistyped fields fall back to defaults. */
internal fun Map<String, Any?>.toUserProfile(uid: String): UserProfile {
    val preferences = this["preferences"] as? Map<*, *> ?: emptyMap<String, Any?>()
    val defaults = UserPreferences()
    fun flag(name: String, default: Boolean) = preferences[name] as? Boolean ?: default
    return UserProfile(
        uid = uid,
        displayName = (this["displayName"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_DISPLAY_NAME,
        preferences = UserPreferences(
            routineLearningEnabled = flag("routineLearningEnabled", defaults.routineLearningEnabled),
            missionNotificationsEnabled =
                flag("missionNotificationsEnabled", defaults.missionNotificationsEnabled),
            defaultReminderMinutes = (preferences["defaultReminderMinutes"] as? Number)
                ?.toInt()
                ?.coerceIn(0, UserPreferences.MAX_REMINDER_MINUTES)
                ?: defaults.defaultReminderMinutes,
            automaticJourneyDetectionEnabled =
                flag("automaticJourneyDetectionEnabled", defaults.automaticJourneyDetectionEnabled),
            communityRankingEnabled =
                flag("communityRankingEnabled", defaults.communityRankingEnabled),
        ),
        updatedAtMillis = timestampMillis(this["updatedAt"]),
    )
}

internal fun UserPreferences.toFirestoreMap(): Map<String, Any> = mapOf(
    "routineLearningEnabled" to routineLearningEnabled,
    "missionNotificationsEnabled" to missionNotificationsEnabled,
    "defaultReminderMinutes" to defaultReminderMinutes,
    "automaticJourneyDetectionEnabled" to automaticJourneyDetectionEnabled,
    "communityRankingEnabled" to communityRankingEnabled,
)

internal fun newProfileDocument(displayName: String, serverTimestamp: Any): Map<String, Any> = mapOf(
    "schemaVersion" to PROFILE_SCHEMA_VERSION,
    "displayName" to displayName,
    "preferences" to UserPreferences().toFirestoreMap(),
    "createdAt" to serverTimestamp,
    "updatedAt" to serverTimestamp,
)

/** Field name and validated value inside `preferences` for one update. */
internal fun PreferenceUpdate.toFirestoreField(): Pair<String, Any> = when (this) {
    is PreferenceUpdate.RoutineLearning -> "routineLearningEnabled" to enabled
    is PreferenceUpdate.MissionNotifications -> "missionNotificationsEnabled" to enabled
    is PreferenceUpdate.DefaultReminderMinutes -> {
        require(minutes in 0..UserPreferences.MAX_REMINDER_MINUTES) {
            "Reminder time must be between 0 and ${UserPreferences.MAX_REMINDER_MINUTES} minutes."
        }
        "defaultReminderMinutes" to minutes
    }
    is PreferenceUpdate.AutomaticJourneyDetection -> "automaticJourneyDetectionEnabled" to enabled
    is PreferenceUpdate.CommunityRanking -> "communityRankingEnabled" to enabled
}

/** Merge-set body that changes one preference and nothing else (field-level last-write-wins). */
internal fun preferenceMergeDocument(update: PreferenceUpdate, serverTimestamp: Any): Map<String, Any> {
    val (field, value) = update.toFirestoreField()
    return mapOf(
        "preferences" to mapOf(field to value),
        "updatedAt" to serverTimestamp,
    )
}
