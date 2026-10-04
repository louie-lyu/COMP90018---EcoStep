package com.ecostep.app.sensors.autodetect

import com.ecostep.app.sensors.tracking.RecordingStartResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoJourneyDetectionTest {

    private class FakeActivityUpdates(var permitted: Boolean = true) : ActivityUpdates {
        var requests = 0
        var removals = 0
        val active get() = requests > removals

        override fun hasPermissions() = permitted
        override fun request() {
            requests++
        }

        override fun remove() {
            removals++
        }
    }

    private val updates = FakeActivityUpdates()
    private var recording = false
    private var starts = 0

    private val coordinator = AutoJourneyDetectionCoordinator(
        activityUpdates = updates,
        isRecording = { recording },
        startRecording = {
            starts++
            recording = true
            RecordingStartResult.Started
        },
    )

    private fun enableInForeground() {
        coordinator.setEnabled(true)
        coordinator.setAppInForeground(true)
    }

    @Test
    fun `trigger needs consecutive confident movement over twenty seconds`() {
        val trigger = MovementTrigger()

        assertFalse(trigger.onActivity(DetectedMovement.WALKING, 90, 0))
        assertFalse(trigger.onActivity(DetectedMovement.WALKING, 90, 10_000))
        assertTrue(trigger.onActivity(DetectedMovement.CYCLING, 80, 20_000))
    }

    @Test
    fun `a single reading or an interrupted run never triggers`() {
        val trigger = MovementTrigger()

        assertFalse(trigger.onActivity(DetectedMovement.IN_VEHICLE, 95, 0))
        assertFalse(trigger.onActivity(DetectedMovement.STILL_OR_OTHER, 95, 15_000))
        assertFalse(trigger.onActivity(DetectedMovement.IN_VEHICLE, 95, 30_000))
        assertFalse(trigger.onActivity(DetectedMovement.IN_VEHICLE, 40, 45_000))
        assertFalse(trigger.onActivity(DetectedMovement.IN_VEHICLE, 95, 60_000))
    }

    @Test
    fun `switched off means no activity updates and no start`() {
        coordinator.setAppInForeground(true)

        coordinator.onActivity(DetectedMovement.WALKING, 90, 0)
        coordinator.onActivity(DetectedMovement.WALKING, 90, 30_000)

        assertEquals(0, updates.requests)
        assertEquals(0, starts)
    }

    @Test
    fun `sustained movement starts one recording`() {
        enableInForeground()

        coordinator.onActivity(DetectedMovement.WALKING, 90, 0)
        coordinator.onActivity(DetectedMovement.WALKING, 90, 25_000)
        coordinator.onActivity(DetectedMovement.WALKING, 90, 50_000)
        coordinator.onActivity(DetectedMovement.WALKING, 90, 75_000)

        assertTrue(updates.active)
        assertEquals(1, starts)
    }

    @Test
    fun `missing permissions keep detection off until refreshed`() {
        updates.permitted = false
        enableInForeground()
        assertFalse(updates.active)

        updates.permitted = true
        coordinator.refresh()

        assertTrue(updates.active)
    }

    @Test
    fun `background, sign-out or switching off removes the subscription`() {
        enableInForeground()
        coordinator.setAppInForeground(false)
        assertFalse(updates.active)

        coordinator.setAppInForeground(true)
        coordinator.setEnabled(false)
        assertFalse(updates.active)

        coordinator.onActivity(DetectedMovement.WALKING, 90, 0)
        coordinator.onActivity(DetectedMovement.WALKING, 90, 30_000)
        assertEquals(0, starts)
    }
}
