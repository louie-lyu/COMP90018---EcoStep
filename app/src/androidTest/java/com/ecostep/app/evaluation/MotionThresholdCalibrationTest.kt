package com.ecostep.app.evaluation

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.MotionHint
import com.ecostep.app.algorithm.MotionThresholds
import com.ecostep.app.algorithm.TransitStop
import com.ecostep.app.algorithm.TransportEvidence
import com.ecostep.app.algorithm.TransportTrackPoint
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.SensorFeatures
import com.ecostep.app.data.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Offline fixtures: no SHL archive, account or transit request needed. */
@RunWith(AndroidJUnit4::class)
class MotionThresholdCalibrationTest {
    private val calibration = MotionThresholdCalibration(listOf(MotionThresholds(0.8, 500)))

    @Test fun masksConfidentActivityForBaselineAndMotionCandidate() = runBlocking {
        val evidence = TransportEvidence(activity = ActivityHint(MotionHint.WALKING, 100))
        val predictions = calibration.classify(journey(), evidence, TransportMode.CAR)
        assertEquals("CYCLING", predictions.getJSONObject(0).getString("predictedMode"))
        assertEquals("CAR", predictions.getJSONObject(1).getString("predictedMode"))
        assertEquals(MotionHint.WALKING, evidence.activity?.type)
    }

    @Test fun preservesTransitEvidenceWhenMaskingActivity() = runBlocking {
        val a = TransitStop(-37.8136, 144.9631)
        val b = TransitStop(-37.8150, 144.9690)
        fun point(stop: TransitStop, time: Long) = TransportTrackPoint(stop.latitude, stop.longitude, time, 0.0, 10.0)
        val predictions = calibration.classify(journey(), TransportEvidence(
            activity = ActivityHint(MotionHint.WALKING, 100),
            track = listOf(point(a, 0), point(a, 11_000), point(b, 30_000), point(b, 42_000)),
            transitRoutes = listOf(listOf(a, b)),
        ), TransportMode.PUBLIC_TRANSPORT)
        assertEquals("PUBLIC_TRANSPORT", predictions.getJSONObject(1).getString("predictedMode"))
    }

    @Test fun selectsOverallAccuracyAndIncludesTimeoutsInDenominator() = runBlocking {
        val predictions = calibration.classify(journey(), TransportEvidence(), TransportMode.CAR)
        val report = calibration.report(listOf(
            record(TransportMode.CAR, predictions, completed = true),
            record(TransportMode.WALKING, calibration.pendingPredictions(), completed = false),
        ), completed = true)
        assertEquals("motion_1", report.getString("bestCandidateId"))
        assertEquals(0.5, report.getDouble("bestAccuracy"), 0.0)
        assertEquals(0.8, report.getJSONObject("bestThresholds").getDouble("steadyAccelStd"), 0.0)
    }

    @Test fun accuracyTiePrefersDisabledAndListsAllWinners() = runBlocking {
        val predictions = calibration.classify(journey(accelStd = 2.0), TransportEvidence(), TransportMode.CYCLING)
        val report = calibration.report(listOf(record(TransportMode.CYCLING, predictions, true)), true)
        assertEquals("speed_only", report.getString("bestCandidateId"))
        assertTrue(report.isNull("bestThresholds"))
        assertEquals(2, report.getJSONArray("tiedBestCandidateIds").length())
    }

    @Test fun incompleteFailedAndAllTimeoutRunsCannotSelectThreshold() {
        val pending = record(TransportMode.CAR, calibration.pendingPredictions(), false)
        assertFalse(calibration.report(listOf(pending), false).getBoolean("selectionAvailable"))
        assertFalse(calibration.report(listOf(pending), true).getBoolean("selectionAvailable"))
        val failed = record(TransportMode.CAR, calibration.pendingPredictions(), true).put("runtimeSuccess", false)
        assertFalse(calibration.report(listOf(failed), true).getBoolean("selectionAvailable"))
        assertTrue(calibration.report(emptyList(), false).isNull("bestCandidateId"))
    }

    @Test fun configurationIsOptInAndBuildsDeduplicatedGrid() {
        assertNull(MotionThresholdCalibration.from(Bundle()))
        val configured = MotionThresholdCalibration.from(Bundle().apply {
            putString("calibrateMotion", "true")
            putString("motionStdCandidates", "0.8,0.4,0.8")
            putString("motionSampleCandidates", "500,1000")
        })!!
        val candidates = configured.report(emptyList(), false).getJSONArray("candidates")
        assertEquals(5, candidates.length())
        assertEquals(0.4, candidates.getJSONObject(1).getJSONObject("thresholds").getDouble("steadyAccelStd"), 0.0)
        assertEquals(1000, candidates.getJSONObject(1).getJSONObject("thresholds").getInt("minAccelSamples"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidThreshold() {
        MotionThresholdCalibration.from(Bundle().apply {
            putString("calibrateMotion", "true")
            putString("motionStdCandidates", "NaN")
        })
    }

    private fun record(mode: TransportMode, predictions: JSONArray, completed: Boolean) = JSONObject()
        .put("trueMode", mode.name).put("runtimeSuccess", true).put("recordingAvailable", true)
        .put("durationMs", JSONObject.NULL).put("sensorCalibrationCompleted", completed)
        .put("sensorCalibrationPredictions", predictions)
        .put("sensorFeatures", JSONObject().put("accelSampleCount", 3000))

    private fun journey(accelStd: Double = 0.3) = JourneySummary(
        journeyId = "calibration-fixture", userId = "fixture",
        startLocation = GeoPoint(-37.8136, 144.9631), endLocation = GeoPoint(-37.8150, 144.9690),
        startTimeMillis = 0, endTimeMillis = 60_000, distanceMeters = 240.0,
        transportMode = TransportMode.UNKNOWN,
        sensorFeatures = SensorFeatures(
            averageSpeedMps = 4.0, p95SpeedMps = 8.0, maxSpeedMps = 12.0,
            stopRatio = 0.1, averageGpsAccuracyMeters = 10.0, gpsSampleCount = 60,
            accelMagnitudeMean = 0.0, accelMagnitudeStd = accelStd, accelSampleCount = 3000,
            gyroMagnitudeMean = 0.0, gyroMagnitudeStd = 0.0, gyroSampleCount = 3000,
        ),
    )
}
