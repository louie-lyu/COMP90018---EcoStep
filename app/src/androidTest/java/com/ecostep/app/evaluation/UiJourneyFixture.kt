package com.ecostep.app.evaluation

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.MotionHint
import com.ecostep.app.core.di.AppContainer
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.sensors.service.JourneyTrackingService
import com.ecostep.app.sensors.tracking.LocationSample
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Only sensor input/history are synthetic. UI, repositories, classifier and AI stay real. */
internal class UiJourneyFixture(private val container: AppContainer, private val timeoutMs: Long) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val createdHistoryIds = mutableListOf<String>()
    var ownsRecording = false

    fun <T> io(block: suspend () -> T): T = runBlocking { withTimeout(timeoutMs) { block() } }

    fun replay() {
        val existing = io { container.missionRepository.observeMissions().first() }
        // Do not reuse a route: production intentionally suppresses a second suggestion for it.
        val start = io {
            (0..60).map { GeoPoint(-37.82 + (existing.size + it) * .003, 144.9631) }.first { point ->
                val end = point.copy(latitude = point.latitude + .009)
                val startLabel = container.placeNameResolver.resolve(point)
                val endLabel = container.placeNameResolver.resolve(end)
                existing.none { it.startLabel == startLabel && it.destinationLabel == endLabel }
            }
        }
        // Stop live GPS before replacing the recording, so an emulator fix cannot contaminate it.
        instrumentation.targetContext.stopService(Intent(instrumentation.targetContext, JourneyTrackingService::class.java))
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            container.journeyTracker.discard()
            val began = System.currentTimeMillis() - 60_000
            check(container.journeyTracker.begin(began))
            repeat(13) { index ->
                container.journeyTracker.onLocation(LocationSample(
                    latitude = start.latitude + index * .00075, longitude = start.longitude,
                    accuracyMeters = 5f, speedMps = 16.7f, timeMillis = began + index * 5_000L,
                ))
            }
            container.journeyTracker.onActivityHint(ActivityHint(MotionHint.IN_VEHICLE, 100))
            container.journeyTracker.tick(System.currentTimeMillis())
        }
    }

    fun seedRecurringHistory(journey: JourneySummary) {
        check(journey.linkedMissionId == null && journey.distanceMeters > 500) { "Unexpected replayed journey" }
        io {
            for (daysAgo in listOf(7L, 14L)) {
                val id = "ui-eval-${UUID.randomUUID()}"
                val shift = daysAgo * 86_400_000
                val history = journey.copy(journeyId = id,
                    startTimeMillis = journey.startTimeMillis - shift, endTimeMillis = journey.endTimeMillis - shift,
                    transportMode = TransportMode.CAR, detectedTransportMode = TransportMode.CAR,
                    confirmedTransportMode = null, confirmationStatus = JourneyConfirmationStatus.PENDING,
                    carbonSavedGrams = null, ecoPoints = null, createdAtMillis = null, updatedAtMillis = null)
                container.journeyRepository.createJourney(history)
                createdHistoryIds += id
                container.journeyRepository.confirmTransportMode(id, TransportMode.CAR)
            }
        }
    }

    fun cleanup() {
        if (!ownsRecording) return
        instrumentation.targetContext.stopService(Intent(instrumentation.targetContext, JourneyTrackingService::class.java))
        instrumentation.runOnMainSync { container.journeyTracker.discard() }
        ownsRecording = false
    }
}
