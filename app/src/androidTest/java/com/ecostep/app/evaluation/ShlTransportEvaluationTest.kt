package com.ecostep.app.evaluation

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.FileNotFoundException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A Studio asset-copy failure must still export the final diagnostic report. */
@RunWith(AndroidJUnit4::class)
class ShlTransportEvaluationTest {
    @Test fun archivePreparationFailureExportsSchemaAndUnavailableCalibration() {
        val arguments = Bundle().apply {
            putString("live", "true")
            putString("gitCommit", "fixture-commit")
            putString("workingTreeDirty", "true")
            putString("calibrateMotion", "true")
            putString("motionStdCandidates", "0.8")
            putString("motionSampleCandidates", "500")
            putString("motionGyroMeanCandidates", "0.5")
            putString("motionSteadyAccelStd", "0.8")
            putString("motionMinAccelSamples", "500")
        }
        var exported: JSONObject? = null
        var exports = 0
        assertThrows(FileNotFoundException::class.java) {
            ShlTransportEvaluation(arguments).run(
                prepareArchive = { throw FileNotFoundException("fixture: missing packaged archive") },
                writeReport = { name, report ->
                    assertTrue(name.startsWith("activity-hint-transport-evaluation-"))
                    exports++
                    exported = JSONObject(report.toString())
                },
            )
        }
        val report = checkNotNull(exported)
        assertEquals(1, exports)
        assertEquals(6, report.getInt("schemaVersion"))
        assertEquals("fixture-commit", report.getString("gitCommit"))
        assertTrue(report.getBoolean("workingTreeDirty"))
        assertFalse(report.getBoolean("passed"))
        assertEquals("FileNotFoundException", report.getJSONObject("runError").getString("type"))
        assertEquals(0, report.getJSONArray("records").length())
        assertEquals(0.8, report.getJSONObject("motionVariantThresholds").getDouble("steadyAccelStd"), 0.0)
        assertTrue(report.has("productionMotionThresholds"))
        assertTrue(report.has("sensorFallbackSummary"))
        assertTrue(report.has("motionVariant"))
        val calibration = report.getJSONObject("sensorCalibration")
        assertFalse(calibration.getBoolean("selectionAvailable"))
        assertTrue(calibration.isNull("bestCandidateId"))
        // speed only, accel only, and accel + gyro
        assertEquals(3, calibration.getJSONArray("candidates").length())
    }
}
