package com.ecostep.app.core.integration

import com.ecostep.app.core.notifications.MissionReminderSync
import com.ecostep.app.data.model.UserPreferences
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.MissionRepository
import com.ecostep.app.data.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Makes the profile switches take effect for the signed-in user:
 * - missionNotificationsEnabled / defaultReminderMinutes -> mission reminders
 * - automaticJourneyDetectionEnabled -> automatic journey detection
 * routineLearningEnabled is read by [MissionGenerationCoordinator] before each generation;
 * communityRankingEnabled keeps its existing Profile/leaderboard behaviour.
 */
class UserPreferenceCoordinator(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val missionRepository: MissionRepository,
    private val reminderSync: MissionReminderSync,
    private val setAutomaticDetection: (Boolean) -> Unit,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** Starts following the user's preferences and missions; restores their reminders. */
    fun onSignedIn() {
        job?.cancel()
        job = scope.launch {
            combine(profileRepository.observeProfile(), missionRepository.observeMissions()) { profile, missions ->
                (profile?.preferences ?: UserPreferences()) to missions
            }
                .catch { /* Listener failed (e.g. offline before first sync): keep current state. */ }
                .collect { (preferences, missions) ->
                    setAutomaticDetection(preferences.automaticJourneyDetectionEnabled)
                    reminderSync.sync(missions, preferences)
                }
        }
    }

    /** Stops everything tied to the previous user. */
    fun onSignedOut() {
        job?.cancel()
        job = null
        setAutomaticDetection(false)
        reminderSync.cancelAll()
    }

    /** One-off reschedule, e.g. right after a reminder fired while the app was closed. */
    suspend fun resync() {
        if (authRepository.currentUserId == null) return
        try {
            val preferences = profileRepository.getProfile()?.preferences ?: UserPreferences()
            reminderSync.sync(missionRepository.observeMissions().first(), preferences)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The next app start or preference change syncs again.
        }
    }
}
