package com.ecostep.app.core.integration

import com.ecostep.app.algorithm.MissionTrigger
import com.ecostep.app.core.notifications.MissionReminderScheduler
import com.ecostep.app.core.notifications.MissionReminderSync
import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.RecurrenceType
import com.ecostep.app.testing.FakeAuthRepository
import com.ecostep.app.testing.FakeMissionRepository
import com.ecostep.app.testing.FakeProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferenceCoordinatorTest {

    private class FakeScheduler : MissionReminderScheduler {
        val scheduled = mutableSetOf<String>()
        override fun schedule(mission: Mission, trigger: MissionTrigger, title: String, message: String) {
            scheduled += mission.missionId
        }

        override fun cancel(missionId: String) {
            scheduled -= missionId
        }

        override fun cancelAll() = scheduled.clear()
        override fun scheduledMissionIds(): Set<String> = scheduled.toSet()
    }

    private val auth = FakeAuthRepository(currentUserId = "user-a")
    private val profiles = FakeProfileRepository(auth)
    private val missions = FakeMissionRepository()
    private val scheduler = FakeScheduler()
    private var detectionEnabled: Boolean? = null

    private val accepted = Mission(
        missionId = "m1",
        title = "Home → Uni",
        status = MissionStatus.ACCEPTED,
        recurrence = MissionRecurrence(type = RecurrenceType.DAILY, timezone = "UTC"),
        scheduledMinuteOfDay = 8 * 60,
    )

    private fun coordinator(scope: CoroutineScope) = UserPreferenceCoordinator(
        authRepository = auth,
        profileRepository = profiles,
        missionRepository = missions,
        reminderSync = MissionReminderSync(scheduler),
        setAutomaticDetection = { detectionEnabled = it },
        scope = scope,
    )

    @Test
    fun `sign-in restores reminders and detection from preferences`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        missions.createMission(accepted)

        coordinator(scope).onSignedIn()

        assertEquals(setOf("m1"), scheduler.scheduled)
        assertEquals(true, detectionEnabled)
        scope.cancelAndJoin()
    }

    @Test
    fun `preference switches take effect immediately`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        missions.createMission(accepted)
        coordinator(scope).onSignedIn()

        profiles.updatePreference(PreferenceUpdate.MissionNotifications(false))
        profiles.updatePreference(PreferenceUpdate.AutomaticJourneyDetection(false))

        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(false, detectionEnabled)
        scope.cancelAndJoin()
    }

    @Test
    fun `accepting a mission schedules it`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        missions.createMission(accepted.copy(status = MissionStatus.SUGGESTED))
        coordinator(scope).onSignedIn()
        assertTrue(scheduler.scheduled.isEmpty())

        missions.updateMission(accepted)

        assertEquals(setOf("m1"), scheduler.scheduled)
        scope.cancelAndJoin()
    }

    @Test
    fun `sign-out cancels reminders and detection`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        missions.createMission(accepted)
        val coordinator = coordinator(scope)
        coordinator.onSignedIn()

        coordinator.onSignedOut()
        missions.updateMission(accepted.copy(title = "Changed"))

        assertTrue(scheduler.scheduled.isEmpty())
        assertFalse(detectionEnabled!!)
        scope.cancelAndJoin()
    }

    private suspend fun CoroutineScope.cancelAndJoin() {
        coroutineContext[kotlinx.coroutines.Job]?.let { job ->
            job.children.forEach { it.cancel() }
            job.children.forEach { it.join() }
        }
    }
}
