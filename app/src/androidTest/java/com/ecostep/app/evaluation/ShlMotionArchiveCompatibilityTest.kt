package com.ecostep.app.evaluation

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.algorithm.MotionThresholds
import com.ecostep.app.algorithm.TransportEvidence
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Full local-archive regression check; no network, Firebase writes or user journeys. */
@RunWith(AndroidJUnit4::class)
class ShlMotionArchiveCompatibilityTest {
    @Test fun motionArchivePreservesExistingRecordingsAndFeedsBothSensors() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyShlMotionArchive") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = checkNotNull(context.getExternalFilesDir("evaluation"))
        val referenceFile = File(directory, "shl-motion-full-reference.json")
        val reference = if (referenceFile.isFile) JSONObject(referenceFile.readText()).getJSONArray("recordings") else null
        val referenceById = reference?.let { array ->
            (0 until array.length()).associate { index ->
                val row = array.getJSONObject(index)
                row.getString("sampleId") to row
            }
        }
        val before = ShlEvaluationDataset.load(File(directory, "shl-preview-gps-only.zip"), 10000, 42)
        val after = ShlEvaluationDataset.load(File(directory, "shl-preview.zip"), 10000, 42)
        assertEquals(before.samples.map { it.id }, after.samples.map { it.id })
        referenceById?.let { assertEquals(it.keys, after.samples.map { sample -> sample.id }.toSet()) }
        assertEquals(before.metadata.getInt("excludedRecordingSegments"), after.metadata.getInt("excludedRecordingSegments"))
        val motion = after.metadata.getJSONObject("motionReplay")
        assertEquals(after.metadata.getInt("labelFiles"), motion.getInt("filesFound"))
        assertEquals(0, motion.getInt("missingFiles"))
        assertEquals(0, motion.getInt("outOfOrderRows"))
        assertTrue(motion.getLong("samplesReplayed") > 0)
        val calibration = MotionThresholdCalibration(listOf(MotionThresholds(0.8, 500)))
        val counts = JSONArray()
        for ((old, new) in before.samples.zip(after.samples)) {
            assertEquals(old.mode, new.mode)
            assertEquals(old.source, new.source)
            assertEquals(old.gpsPoints, new.gpsPoints)
            assertEquals(old.activityPoints, new.activityPoints)
            assertEquals(old.acceptedHints, new.acceptedHints)
            val original = checkNotNull(old.recording)
            val updated = checkNotNull(new.recording)
            assertEquals(original, updated.copy(features = original.features))
            val f = updated.features
            val originalFeatures = original.features
            assertEquals(originalFeatures, f.copy(
                accelMagnitudeMean = originalFeatures.accelMagnitudeMean,
                accelMagnitudeStd = originalFeatures.accelMagnitudeStd,
                accelSampleCount = originalFeatures.accelSampleCount,
                gyroMagnitudeMean = originalFeatures.gyroMagnitudeMean,
                gyroMagnitudeStd = originalFeatures.gyroMagnitudeStd,
                gyroSampleCount = originalFeatures.gyroSampleCount,
            ))
            assertTrue("Missing motion for ${new.id}", f.accelSampleCount > 0)
            assertEquals(f.accelSampleCount, f.gyroSampleCount)
            assertTrue(f.accelMagnitudeMean.isFinite() && f.accelMagnitudeStd.isFinite())
            assertTrue(f.gyroMagnitudeMean.isFinite() && f.gyroMagnitudeStd.isFinite())
            referenceById?.get(new.id)?.let {
                assertEquals(it.getInt("accelSamples"), f.accelSampleCount)
                assertEquals(it.getInt("gyroSamples"), f.gyroSampleCount)
                assertEquals(it.getDouble("accelMean"), f.accelMagnitudeMean, 0.0)
                assertEquals(it.getDouble("accelStd"), f.accelMagnitudeStd, 0.0)
                assertEquals(it.getDouble("gyroMean"), f.gyroMagnitudeMean, 0.0)
                assertEquals(it.getDouble("gyroStd"), f.gyroMagnitudeStd, 0.0)
            }
            // Exercise the real sensor-only calibration path with local empty transit evidence.
            // This verifies compatibility, not real-world transport accuracy or threshold quality.
            val predictions = calibration.classify(checkNotNull(new.journey), TransportEvidence(), new.mode)
            assertEquals(2, predictions.length())
            counts.put(JSONObject().put("sampleId", new.id).put("accelSamples", f.accelSampleCount)
                .put("gyroSamples", f.gyroSampleCount).put("accelMean", f.accelMagnitudeMean)
                .put("accelStd", f.accelMagnitudeStd).put("gyroMean", f.gyroMagnitudeMean)
                .put("gyroStd", f.gyroMagnitudeStd))
        }
        val report = JSONObject().put("passed", true).put("comparedRecordings", after.samples.size)
            .put("motionStatisticsMatchFullArchive", referenceById != null)
            .put("excludedRecordings", after.metadata.getInt("excludedRecordingSegments"))
            .put("motionReplay", motion).put("recordings", counts)
            .put("scope", "Offline archive compatibility; original recording fields preserved, both motion sensors and calibration path verified. No accuracy calibration.")
        File(context.filesDir, "evaluation").apply { mkdirs() }
            .resolve("shl-motion-archive-compatibility.json").writeText(report.toString(2))
        println("SHL motion compatibility: ${after.samples.size} recordings preserved; $motion")
    }
}
