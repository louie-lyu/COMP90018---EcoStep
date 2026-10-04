package com.ecostep.app.data.repository

import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The signed-in user's private profile (users/{uid}). */
interface ProfileRepository {

    /** Emits null when the profile document has not been created yet. */
    fun observeProfile(): Flow<UserProfile?>

    suspend fun getProfile(): UserProfile?

    /**
     * Creates the profile with defaults if it does not exist. Never overwrites an existing
     * profile. Returns true when this call created it.
     */
    suspend fun ensureProfile(defaultDisplayName: String): Boolean

    suspend fun updateDisplayName(displayName: String): WriteOutcome

    suspend fun updatePreference(update: PreferenceUpdate): WriteOutcome
}
