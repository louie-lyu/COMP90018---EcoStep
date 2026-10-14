package com.ecostep.app.evaluation

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.EcoStepApp
import com.ecostep.app.MainActivity
import com.ecostep.app.core.integration.MissionGenerationOutcome
import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.MissionTransitions
import com.ecostep.app.data.model.TransportMode
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run with the shared Evaluation - UI responsiveness configurations. Uses a test account. */
@RunWith(AndroidJUnit4::class)
class UiResponsivenessEvaluationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()
    private val container get() = (instrumentation.targetContext.applicationContext as EcoStepApp).appContainer
    private val timeoutMs = args.getString("timeoutMs", "90000").toLong().also { require(it in 1_000..300_000) }
    private val ui = EvaluationUi(instrumentation.uiAutomation, timeoutMs)
    private val fixture by lazy { UiJourneyFixture(container, timeoutMs) }

    @Test fun mainFeaturesRespondAndRender() {
        val samples = args.getString("samples", "10").toInt().also { require(it in 1..50) }
        val report = UiResponseReport(args, samples)
        val automation = instrumentation.uiAutomation
        val originalFlags = automation.serviceInfo.flags
        var activity: ActivityScenario<MainActivity>? = null
        var frames: UiFrameCollector? = null
        try {
            check(args.getString("live") == "true" && args.getString("testAccount") == "true") {
                "Use the UI responsiveness preset after signing into a dedicated test account."
            }
            check(container.authRepository.currentUserId != null) { "Sign into the debug app first." }
            check(!container.journeyTracker.state.value.isRecording) { "Finish the existing recording first." }
            check(fixture.io { container.profileRepository.getProfile()?.preferences?.routineLearningEnabled != false }) {
                "Enable routine learning in the test account preferences."
            }
            check(fixture.io { container.missionRepository.observeMissions().first().none {
                it.status == MissionStatus.ACTIVE || it.status == MissionStatus.SUGGESTED
            } }) { "Finish active missions and answer existing suggestions before this evaluation." }

            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            val permissions = buildList {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissions.forEach { automation.grantRuntimePermission(instrumentation.targetContext.packageName, it) }
            activity = ActivityScenario.launch(MainActivity::class.java)
            activity.onActivity { frames = UiFrameCollector(it.window) }
            ui.await("route.destination")
            val collector = checkNotNull(frames)

            for (sample in 1..samples) {
                runJourney(sample, report)
                val weekly = ui.button("weekly.open")
                report.measure(sample, "weekly_insight") {
                    ui.tap(weekly)
                    ui.waitFor("weekly result") {
                        check(!ui.visible("weekly.error")) { "Weekly data loading failed" }
                        ui.prefix("weekly.ready.") != null
                    }
                }.put("source", ui.prefix("weekly.ready.")!!.tag().substringAfterLast('.'))
                ui.click("Back to Missions")
                ui.click("Map")
                ui.await("route.destination")
                report.captureFrames(collector)
                report.save()
            }
            report.finish()
        } catch (error: Throwable) {
            report.root.put("runErrorType", error.javaClass.simpleName)
            report.root.put("failureTags", JSONArray(ui.nodes().map { it.tag() }.filter {
                it.startsWith("journey.") || it.startsWith("route.") || it.startsWith("weekly.") || it.startsWith("mission.")
            }))
            // Only fixed messages from our checks, never provider responses or screen/account text.
            if (error is IllegalStateException || error is UiResponseTimeout) report.root.put("runError", error.message)
            throw error
        } finally {
            try {
                frames?.let { report.captureFrames(it) }
                fixture.cleanup()
            } finally {
                runCatching { frames?.close() }
                runCatching { activity?.close() }
                automation.serviceInfo = automation.serviceInfo.apply { flags = originalFlags }
                report.root.put("createdHistoryIds", JSONArray(fixture.createdHistoryIds))
                    .put("finishedAtMillis", System.currentTimeMillis())
                report.save()
            }
        }
        assertTrue("Inspect ui-responsiveness.json", report.root.getBoolean("passed"))
    }

    private fun runJourney(sample: Int, report: UiResponseReport) {
        ui.await("home.location.ready") // GPS acquisition is setup, outside route-query timing.
        ui.setText("route.destination", args.getString("destination", "Flinders Street Station, Melbourne"))
        report.measure(sample, "route_query") {
            ui.search("route.destination")
            ui.waitFor("route results") {
                val error = ui.nodes().firstOrNull { it.tag() == "route.error" && it.isVisibleToUser }
                check(error == null) {
                    "Route query failed: " + when (error?.text?.toString()) {
                        "Your current location isn't available yet. Enter a starting point instead." -> "location unavailable"
                        "Could not find that destination." -> "geocoding failed"
                        "No route options were found." -> "no route options"
                        else -> "provider unavailable"
                    }
                }
                ui.visible("route.results") && !ui.visible("route.loading")
            }
        }
        ui.click("Show route")
        val start = ui.button("journey.start")
        fixture.ownsRecording = true
        report.measure(sample, "start_journey") {
            ui.tap(start)
            ui.waitFor("recording controls") {
                ui.nodes().any { it.tag() == "journey.end" && it.isVisibleToUser && it.isEnabled }
            }
        }

        fixture.replay() // Setup outside measured intervals; no need to physically drive for a minute.
        val end = ui.button("journey.end")
        val stop = report.measure(sample, "stop_to_review") {
            ui.tap(end)
            ui.waitFor("new Review") { ui.prefix("journey.review:") != null }
        }
        fixture.ownsRecording = false
        val journeyId = ui.prefix("journey.review:")!!.tag().substringAfter(':')
        check(report.records.none { it !== stop && it.optString("journeyId") == journeyId }) { "Stale Review" }
        stop.put("journeyId", journeyId)
        val journey = checkNotNull(fixture.io { container.journeyRepository.getJourney(journeyId) })
        check(journey.userId == container.authRepository.currentUserId) { "Unexpected journey owner" }
        fixture.seedRecurringHistory(journey)
        ui.scrollTo("Car")
        ui.click("Car")
        ui.scrollTo("journey.confirm")
        val confirm = ui.button("journey.confirm")
        val confirmed = report.measure(sample, "confirm_to_feedback") {
            ui.tap(confirm)
            ui.waitFor("saved dialog") { ui.nodes().any { it.isVisibleToUser && it.text?.toString() == "Journey saved!" } }
        }.put("journeyId", journeyId)

        // Overlapping windows intentionally share the same Confirm click. No file/frame work between them.
        val suggestion = report.measure(sample, "mission_suggestion", confirmed.getLong("startedNs")) {
            ui.click("Back to Map")
            ui.click("Missions")
            ui.waitFor("new mission suggestion") {
                val result = container.missionGenerationCoordinator.lastResult?.takeIf { it.first == journeyId }?.second
                check(result == null || result is MissionGenerationOutcome.Created) { "Mission generation did not create a suggestion: ${result?.javaClass?.simpleName}" }
                result is MissionGenerationOutcome.Created && ui.visible("mission.suggestion:${result.missionId}")
            }
        }
        val result = container.missionGenerationCoordinator.lastResult!!.second as MissionGenerationOutcome.Created
        suggestion.put("missionId", result.missionId).put("journeyId", journeyId)
            .put("weeklyFixtureOccurrenceDate", LocalDate.now().toString())
            .put("source", if (result.usedAi) "ai" else "fallback")
        val saved = fixture.io { container.journeyRepository.observeJourney(journeyId).first() }
        check(saved?.journey?.confirmationStatus == JourneyConfirmationStatus.CONFIRMED &&
            saved.journey.confirmedTransportMode == TransportMode.CAR) { "Saved dialog without confirmed local journey" }
        confirmed.put("localConfirmationVerified", true).put("pendingSync", saved.hasPendingWrites)
        // Prepare a real, accepted weekly occurrence outside timing so an empty account exercises AI.
        // Archive only our generated mission; no completion or reward is fabricated.
        fixture.io {
            val mission = container.missionRepository.observeMissions().first().first { it.missionId == result.missionId }
            val today = LocalDate.now()
            val accepted = MissionTransitions.accept(mission, today)
            container.missionRepository.updateMission(accepted)
            container.missionResultRepository.markStarted(result.missionId, today.toString(), System.currentTimeMillis())
            container.missionRepository.updateMission(accepted.copy(status = MissionStatus.ARCHIVED, nextOccurrenceDate = null))
        }
        ui.waitFor("suggestion archived") { !ui.visible("mission.suggestion:${result.missionId}") }
    }
}
