package com.ecostep.app.evaluation

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.EcoStepApp
import com.ecostep.app.MainActivity
import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.MotionHint
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.sensors.tracking.LocationSample
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real production UI/navigation/classifier/Firestore; only pre-click GPS input is replayed. */
@RunWith(AndroidJUnit4::class)
class JourneyUiLatencyEvaluationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.uiAutomation
    private val app get() = instrumentation.targetContext.applicationContext as EcoStepApp
    private val container get() = app.appContainer
    private val records = mutableListOf<JSONObject>()
    private val latencies = LatencyTracker()
    private var operationTimeoutMs = 30_000L

    @Test
    fun evaluateJourneyUiLatency() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Use run-journey-ui-latency-evaluation.ps1 -TestAccount", arguments.getString("live") == "true")
        check(arguments.getString("testAccount") == "true") { "A dedicated signed-in test account is required" }
        val samples = arguments.getString("samples")?.toInt() ?: 20
        operationTimeoutMs = arguments.getString("timeoutMs")?.toLong() ?: 30_000L
        require(samples in 1..100 && operationTimeoutMs in 5_000..120_000)
        val startedAt = System.currentTimeMillis()
        val report = JSONObject().apply {
            put("schemaVersion", 1)
            put("startedAtMillis", startedAt)
            put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
            put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("buildType", BuildConfig.BUILD_TYPE)
            put("networkConditions", arguments.getString("networkConditions") ?: "unspecified")
            put("plannedSamplesPerScenario", samples)
            put("operationTimeoutMs", operationTimeoutMs)
            put("pollIntervalMs", POLL_MS)
            put("scope", "Real MainActivity, navigation, live transit classifier and signed-in Firestore. Fixed 60-second walking GPS/Activity fixture is replayed before clicking End. No physical sensor acquisition, AI mission-generation latency or server-ack latency measured.")
            put("timing", "System.nanoTime from injected touch DOWN to accessibility-observed visible UI readiness. Includes input dispatch, production work, rendering/accessibility propagation and polling overhead. No Compose virtual clock. Warm app; first sample retained and numbered. Setup, scrolling, storage checks and pauses excluded.")
            put("stopBoundary", "End touch -> new journey-specific Review layout and visible Journey complete heading. Mode selector availability is checked before confirmation; reaching a button below the fold is not timed.")
            put("confirmBoundary", "Confirm touch -> visible Journey saved dialog with enabled Back to Map button. Local Firestore confirmation is verified outside timing; pending sync is reported separately.")
            put("fixture", "13 GPS points at 5-second intervals; 5m reported accuracy; Melbourne northbound ~67m walking; dominant WALKING hint 100. Unique journey ID per run. No active mission permitted.")
            put("dataRetention", "Created/confirmed journeys remain in the test account; client security rules prohibit deletion. Walking journeys have no lower-carbon alternative, so no AI recommendation is expected.")
            put("passed", false)
        }
        var activity: ActivityScenario<MainActivity>? = null
        var replayInProgress = false
        val originalInfo = automation.serviceInfo
        val originalFlags = originalInfo.flags
        try {
            val uid = checkNotNull(container.authRepository.currentUserId) { "Sign in to a test account in EcoStep before running" }
            check(!container.journeyTracker.state.value.isRecording) { "An existing recording is active; finish it first" }
            val activeMission = runBlocking {
                withTimeout(operationTimeoutMs) { container.missionRepository.observeMissions().first() }
            }.any { it.status == com.ecostep.app.data.model.MissionStatus.ACTIVE }
            check(!activeMission) { "End the active mission before running this free-journey evaluation" }
            automation.serviceInfo = originalInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            }
            activity = ActivityScenario.launch(MainActivity::class.java)
            waitFor("home") { visibleText("Where are you going?") }
            for (index in 1..samples) {
                check(container.authRepository.currentUserId == uid) { "Account changed during evaluation" }
                check(!container.journeyTracker.state.value.isRecording) { "Unexpected recording is active" }
                instrumentation.runOnMainSync {
                    val start = System.currentTimeMillis() - 60_000L
                    check(container.journeyTracker.begin(start))
                    replayInProgress = true
                    repeat(13) { point ->
                        container.journeyTracker.onLocation(LocationSample(
                            latitude = -37.8136 + point * 0.00005,
                            longitude = 144.9631,
                            accuracyMeters = 5f,
                            speedMps = 1.1f,
                            timeMillis = start + point * 5_000L,
                        ))
                    }
                    container.journeyTracker.onActivityHint(ActivityHint(MotionHint.WALKING, 100))
                    container.journeyTracker.tick(System.currentTimeMillis())
                }
                val end = stableNode { it.tag() == "journey.end" && it.readyToClick() }
                val stopRecord = measured(index, "stop_to_review", end) {
                    waitFor("new Review content") {
                        nodes().any { it.tag().startsWith("journey.review:") && it.isVisibleToUser } &&
                            visibleText("Journey complete")
                    }
                }
                replayInProgress = false
                val review = nodes().first { it.tag().startsWith("journey.review:") }
                val journeyId = review.tag().removePrefix("journey.review:")
                check(journeyId.isNotBlank()) { "Missing new journey identity" }
                check(records.none { it !== stopRecord && it.optString("journeyId") == journeyId }) { "Stale journey shown" }
                stopRecord.put("journeyId", journeyId)
                val initial = runBlocking {
                    withTimeout(operationTimeoutMs) { container.journeyRepository.getJourney(journeyId) }
                }
                check(initial != null && initial.userId == uid && initial.linkedMissionId == null) { "Unexpected journey ownership or mission link" }
                stopRecord.put("detectedMode", initial.detectedTransportMode?.name ?: initial.transportMode.name)
                // Exercise the actual mode selector; explicitly choose walking even after network fallback.
                val walking = scrollTo { it.text?.toString() == "Walk" && it.isVisibleToUser }
                click(clickableAncestor(walking))
                scrollTo { it.tag() == "journey.confirm" && it.readyToClick() }
                val confirm = stableNode { it.tag() == "journey.confirm" && it.readyToClick() }
                val confirmRecord = measured(index, "confirm_to_feedback", confirm) {
                    waitFor("saved dialog") {
                        visibleText("Journey saved!") && nodes().any {
                            (it.tag() == "journey.backToMap" || it.text?.toString() == "Back to Map") && it.isVisibleToUser
                        }
                    }
                }
                confirmRecord.put("journeyId", journeyId)
                // Local storage verification does not inflate the user-visible response time.
                val snapshot = Tasks.await(FirebaseFirestore.getInstance().collection("users").document(uid)
                    .collection("journeys").document(journeyId).get(Source.CACHE), operationTimeoutMs, TimeUnit.MILLISECONDS)
                check(snapshot.getString("confirmationStatus") == "confirmed" && snapshot.getString("confirmedTransportMode") == TransportMode.WALKING.name) {
                    "Saved dialog did not correspond to a locally confirmed walking journey"
                }
                confirmRecord.put("localConfirmationVerified", true)
                    .put("pendingSyncAtVerification", snapshot.metadata.hasPendingWrites())
                stopRecord.put("localJourneyVerified", true)
                click(clickableAncestor(waitForNode("Back to Map") { it.text?.toString() == "Back to Map" && it.isVisibleToUser }))
                waitFor("return to home") { visibleText("Where are you going?") }
                writeReport(report, startedAt, samples)
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("stream", "UI latency: $index/$samples journeys complete\n")
                })
                if (index < samples) SystemClock.sleep(2_000)
            }
            report.put("passed", records.size == samples * 2 && records.all { it.getString("outcome") == "SUCCESS" })
        } catch (error: Throwable) {
            report.put("runErrorType", error.javaClass.simpleName)
            // Test-authored assertion messages contain no credentials or user data.
            if (error is IllegalStateException || error is UiWaitTimeout) report.put("runError", error.message)
            throw error
        } finally {
            if (replayInProgress) instrumentation.runOnMainSync { container.journeyTracker.discard() }
            try {
                activity?.close()
            } finally {
                automation.serviceInfo = originalInfo.apply { flags = originalFlags }
                report.put("finishedAtMillis", System.currentTimeMillis())
                writeReport(report, startedAt, samples)
            }
        }
        assertTrue("Inspect UI latency JSON", report.getBoolean("passed"))
    }

    private fun measured(index: Int, scenario: String, button: AccessibilityNodeInfo, finish: () -> Unit): JSONObject {
        val bounds = Rect().also(button::getBoundsInScreen)
        val record = JSONObject().put("sample", index).put("scenario", scenario)
        val timer = latencies.start(scenario)
        try {
            tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            finish()
            timer.finish()
            record.put("outcome", "SUCCESS")
        } catch (error: Throwable) {
            val outcome = if (error is UiWaitTimeout) Outcome.TIMEOUT else Outcome.FAILURE
            timer.finish(outcome, error.javaClass.simpleName)
            record.put("outcome", outcome.name).put("errorType", error.javaClass.simpleName)
            throw error
        } finally {
            record.put("durationMs", latencies.snapshot().last().durationMs)
            records += record
        }
        return record
    }

    private fun writeReport(report: JSONObject, startedAt: Long, samples: Int) {
        report.put("records", JSONArray(records))
        report.put("summaries", JSONArray(listOf("stop_to_review", "confirm_to_feedback").map { scenario ->
            val attempted = records.filter { it.getString("scenario") == scenario }
            val times = attempted.filter { it.getString("outcome") == "SUCCESS" }.map { it.getDouble("durationMs") }.sorted()
            fun percentile(p: Double): Any = if (times.isEmpty()) JSONObject.NULL else times[kotlin.math.ceil(times.size * p).toInt() - 1]
            JSONObject().put("scenario", scenario).put("planned", samples).put("attempted", attempted.size)
                .put("successful", times.size).put("failed", attempted.count { it.getString("outcome") == "FAILURE" })
                .put("timeouts", attempted.count { it.getString("outcome") == "TIMEOUT" }).put("skipped", samples - attempted.size)
                .put("successRate", if (attempted.isEmpty()) JSONObject.NULL else times.size.toDouble() / attempted.size)
                .put("p50Ms", percentile(.50)).put("p95Ms", percentile(.95))
                .put("maxMs", times.lastOrNull() ?: JSONObject.NULL)
        }))
        val directory = File(instrumentation.targetContext.filesDir, "evaluation").apply { mkdirs() }
        File(directory, "journey-ui-latency-$startedAt.json").writeText(report.toString(2))
        File(directory, "journey-ui-latency-$startedAt.csv").writeText(latencies.toCsv())
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(::visit)
        }
        automation.rootInActiveWindow?.let(::visit)
        return result
    }

    private fun AccessibilityNodeInfo.tag(): String = viewIdResourceName?.takeIf { it.startsWith("journey.") }
        ?: extras.getString("androidx.compose.ui.semantics.testTag").orEmpty()
    private fun AccessibilityNodeInfo.readyToClick() = isVisibleToUser && isEnabled && isClickable
    private fun visibleText(text: String) = nodes().any { it.text?.toString() == text && it.isVisibleToUser }
    private fun waitFor(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + operationTimeoutMs
        do {
            if (condition()) return
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw UiWaitTimeout("Timed out waiting for $label")
    }
    private fun waitForNode(label: String, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        waitFor(label) { found = nodes().firstOrNull(predicate); found != null }
        return checkNotNull(found)
    }
    /** Scrolling can expose a button before its animation stops; never tap stale coordinates. */
    private fun stableNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var previous: Rect? = null
        var stableSince = SystemClock.elapsedRealtime()
        var found: AccessibilityNodeInfo? = null
        waitFor("stable button bounds") {
            found = nodes().firstOrNull(predicate)
            val bounds = found?.let { Rect().also(it::getBoundsInScreen) }
            if (bounds == null || bounds != previous) {
                previous = bounds
                stableSince = SystemClock.elapsedRealtime()
            }
            bounds != null && !bounds.isEmpty && SystemClock.elapsedRealtime() - stableSince >= 400
        }
        return checkNotNull(found)
    }
    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var candidate = node
        repeat(8) {
            if (candidate.readyToClick()) return candidate
            candidate = candidate.parent ?: throw IllegalStateException("No enabled click target")
        }
        error("No enabled click target")
    }
    private fun click(node: AccessibilityNodeInfo) {
        val bounds = Rect().also(node::getBoundsInScreen)
        tap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
    }
    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(automation.injectInputEvent(event, true)) { "Touch injection failed" } } finally { event.recycle() }
        }
    }
    private fun scrollTo(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        repeat(12) {
            nodes().firstOrNull(predicate)?.let { return it }
            val list = nodes().firstOrNull { it.isScrollable && it.isVisibleToUser }
                ?: throw IllegalStateException("No scrollable Review content")
            if (!list.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                SystemClock.sleep(300)
                nodes().firstOrNull(predicate)?.let { return it }
                error("Review control not found")
            }
            SystemClock.sleep(300)
        }
        error("Review control not found after scrolling")
    }
    private class UiWaitTimeout(message: String) : RuntimeException(message)
    private companion object { const val POLL_MS = 50L }
}
