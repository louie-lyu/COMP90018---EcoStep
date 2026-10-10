package com.ecostep.app.evaluation

import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.EcoStepApp
import com.ecostep.app.algorithm.CALIBRATED_MOTION_THRESHOLDS
import com.ecostep.app.algorithm.DefaultTransportClassifier
import com.ecostep.app.algorithm.MotionThresholds
import com.ecostep.app.algorithm.TransportEvidence
import com.ecostep.app.algorithm.TransportEvidenceProvider
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.evaluation.ShlEvaluationDataset.Sample
import com.ecostep.app.network.publictransport.DefaultTransportEvidenceProvider
import com.ecostep.app.network.publictransport.TransitousApi
import com.ecostep.app.network.publictransport.TransitousClient
import com.ecostep.app.network.publictransport.TransitousResponse
import com.ecostep.app.network.publictransport.toTransitStopRoutes
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException

/** SHL recordings + current online transit plans, using the production classifier. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ActivityHintTransportEvaluationTest {
    @Test
    fun evaluateActivityHintTransportClassification() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Use run-activity-hint-transport-evaluation.ps1", arguments.getString("live") == "true")
        val limit = arguments.getString("samplesPerMode")?.toInt() ?: 10000
        val seed = arguments.getString("seed")?.toInt() ?: 42
        require(limit in 1..10000 && seed >= 0)
        val motionThresholds = motionThresholdsFrom(arguments)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = (context.applicationContext as EcoStepApp).appContainer.okHttpClient
        val api = TransitousClient.create(client)
        val archive = File(context.getExternalFilesDir("evaluation"), "shl-preview.zip")
        val startedAt = System.currentTimeMillis()
        val directory = File(context.filesDir, "evaluation").apply { mkdirs() }
        val output = File(directory, "activity-hint-transport-evaluation-$startedAt.json")
        val report = newReport(startedAt, limit, seed, motionThresholds)
        val records = mutableListOf<JSONObject>()
        var completed = false

        try {
            check(archive.isFile) { "SHL ZIP missing; use run-activity-hint-transport-evaluation.ps1" }
            report.put("datasetSha256", sha256(archive))
            val dataset = ShlEvaluationDataset.load(archive, limit, seed)
            report.put("dataset", dataset.metadata).put("plannedSamples", dataset.samples.size)
            var hasRequested = false
            for (sample in dataset.samples) {
                if (sample.recording != null && hasRequested) delay(REQUEST_INTERVAL_MS)
                val record = evaluate(sample, api, motionThresholds)
                if (sample.recording != null) hasRequested = true
                records += record
                writeProgress(report, records, output)
                println("SHL online: ${records.size}/${dataset.samples.size}; planner=${record.getJSONObject("planner").getString("status")}")
                if (record.getJSONObject("planner").optInt("httpStatus") in listOf(403, 429)) {
                    report.put("stopReason", "HTTP 403/429: remaining requests not sent")
                    break
                }
            }
            completed = records.size == dataset.samples.size
        } catch (error: Exception) {
            report.put("runError", errorJson(error))
            throw error
        } finally {
            finishReport(report, records, completed, motionThresholds)
            output.writeText(report.toString(2))
        }
        assertTrue("Evaluation incomplete; inspect JSON report", report.getBoolean("passed"))
    }

    private suspend fun evaluate(sample: Sample, api: TransitousApi, motionThresholds: MotionThresholds?): JSONObject {
        val record = newRecord(sample, motionThresholds)
        val recording = sample.recording ?: return record.put(
            "reason", "Recording unavailable after production filtering; App would not save it",
        )
        val observedApi = CurrentTimetableApi(api, record.getJSONObject("planner"))
        val evidence = RememberingEvidenceProvider(DefaultTransportEvidenceProvider(recording, observedApi))
        val journey = checkNotNull(sample.journey)
        val start = System.nanoTime()
        try {
            // Match TrackingViewModel: classify on IO, keep UNKNOWN on timeout or exception.
            val result = withContext(Dispatchers.IO) {
                withTimeoutOrNull(CLASSIFICATION_TIMEOUT_MS) {
                    DefaultTransportClassifier(evidence).classify(journey)
                }
            }
            if (result == null) {
                record.put("timedOut", true)
            } else {
                record.put("predictedMode", result.mode.name)
                    .put("confidence", result.confidence)
                    .put("classificationCompleted", true)
                    .put("correct", result.mode == sample.mode)
                if (motionThresholds != null) {
                    // Same evidence as the production run, so only the motion rule differs; no second request.
                    val remembered = checkNotNull(evidence.evidence)
                    val variant = DefaultTransportClassifier(
                        object : TransportEvidenceProvider {
                            override suspend fun getEvidence(journey: JourneySummary) = remembered
                        },
                        motionThresholds,
                    ).classify(journey)
                    record.getJSONObject("motionVariant")
                        .put("predictedMode", variant.mode.name)
                        .put("correct", variant.mode == sample.mode)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            record.put("runtimeSuccess", false).put("classificationError", errorJson(error))
        } finally {
            record.put("durationMs", (System.nanoTime() - start) / 1_000_000.0)
            record.put("transitRoutes", observedApi.routeCount)
        }
        return record
    }

    private fun newRecord(sample: Sample, motionThresholds: MotionThresholds?) = JSONObject().apply {
        put("sampleId", sample.id)
        put("userId", sample.user)
        put("sourceMode", ShlEvaluationDataset.sourceNames.getValue(sample.source))
        put("trueMode", sample.mode.name)
        put("startTimeMillis", sample.start)
        put("endTimeMillis", sample.end)
        put("rawGpsPoints", sample.gpsPoints)
        put("acceptedGpsPoints", sample.acceptedGpsPoints)
        put("trackPoints", sample.recording?.trace?.size ?: 0)
        put("activitySamples", sample.activityPoints)
        put("acceptedActivityHints", sample.acceptedHints)
        put("activityConfidenceTies", sample.activityTies)
        put("activityHint", sample.recording?.activityHint?.let {
            JSONObject().put("type", it.type.name).put("confidence", it.confidence)
        } ?: JSONObject.NULL)
        put("predictedMode", "UNKNOWN")
        put("confidence", JSONObject.NULL)
        put("correct", false)
        put("recordingAvailable", sample.recording != null)
        put("classifierCalled", sample.recording != null)
        put("classificationCompleted", false)
        put("runtimeSuccess", true)
        put("timedOut", false)
        put("durationMs", JSONObject.NULL)
        put("transitRoutes", 0)
        put("planner", JSONObject().put("status", "not_called"))
        put("sensorFeatures", sample.journey?.sensorFeatures?.let { features ->
            JSONObject()
                .put("averageSpeedMps", features.averageSpeedMps)
                .put("p95SpeedMps", features.p95SpeedMps)
                .put("maxSpeedMps", features.maxSpeedMps)
                .put("stopRatio", features.stopRatio)
                .put("accelSampleCount", features.accelSampleCount)
                .put("accelMagnitudeMean", features.accelMagnitudeMean)
                .put("accelMagnitudeStd", features.accelMagnitudeStd)
                .put("gyroSampleCount", features.gyroSampleCount)
                .put("gyroMagnitudeMean", features.gyroMagnitudeMean)
                .put("gyroMagnitudeStd", features.gyroMagnitudeStd)
        } ?: JSONObject.NULL)
        // Timeouts and missing recordings stay UNKNOWN, as in the App.
        if (motionThresholds != null) {
            put("motionVariant", JSONObject().put("predictedMode", "UNKNOWN").put("correct", false))
        }
    }

    /** Keeps the evidence of the production run for the motion variant. */
    private class RememberingEvidenceProvider(
        private val delegate: TransportEvidenceProvider,
    ) : TransportEvidenceProvider {
        var evidence: TransportEvidence? = null
            private set

        override suspend fun getEvidence(journey: JourneySummary): TransportEvidence =
            delegate.getEvidence(journey).also { evidence = it }
    }

    /** Only changes the query time; all sensor timestamps and production API defaults are retained. */
    private class CurrentTimetableApi(
        private val delegate: TransitousApi,
        private val diagnostics: JSONObject,
    ) : TransitousApi {
        var routeCount = 0
            private set

        override suspend fun planJourney(
            fromPlace: String,
            toPlace: String,
            time: String?,
            arriveBy: Boolean,
            maxTransfers: Int,
            detailedLegs: Boolean,
            detailedTransfers: Boolean,
            userAgent: String,
        ): TransitousResponse {
            val start = System.nanoTime()
            diagnostics.put("status", "started")
                .put("requestStartedAtMillis", System.currentTimeMillis())
            try {
                // Retrofit omits null: the service plans against its current timetable.
                val response = delegate.planJourney(
                    fromPlace = fromPlace,
                    toPlace = toPlace,
                    time = null,
                    arriveBy = arriveBy,
                    maxTransfers = maxTransfers,
                    detailedLegs = detailedLegs,
                    detailedTransfers = detailedTransfers,
                    userAgent = userAgent,
                )
                val routes = response.toTransitStopRoutes()
                routeCount = routes.size
                diagnostics.put("status", "success")
                    .put("itineraries", response.itineraries.size)
                    .put("routeStopCounts", JSONArray(routes.map { it.size }))
                return response
            } catch (error: CancellationException) {
                diagnostics.put("status", "cancelled")
                throw error
            } catch (error: Exception) {
                diagnostics.put("status", "error").put("error", errorJson(error))
                if (error is HttpException) {
                    diagnostics.put("httpStatus", error.code())
                    diagnostics.put("errorBody", error.response()?.errorBody()?.string()?.take(2000) ?: JSONObject.NULL)
                }
                throw error // The production provider applies its empty-route fallback.
            } finally {
                diagnostics.put("durationMs", (System.nanoTime() - start) / 1_000_000.0)
            }
        }
    }

    private fun newReport(startedAt: Long, limit: Int, seed: Int, motionThresholds: MotionThresholds?) = JSONObject().apply {
        val arguments = InstrumentationRegistry.getArguments()
        put("schemaVersion", 5)
        put("experiment", "SHL recorded Activity, GPS and motion (when the archive has it) with live current-timetable transit plans")
        put("productionMotionThresholds", CALIBRATED_MOTION_THRESHOLDS?.toJson() ?: JSONObject.NULL)
        put("motionVariantThresholds", motionThresholds?.toJson() ?: JSONObject.NULL)
        put("timetableTime", "current")
        put("startedAtMillis", startedAt)
        put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
        put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("androidVersion", Build.VERSION.RELEASE)
        put("apiLevel", Build.VERSION.SDK_INT)
        put("buildType", BuildConfig.BUILD_TYPE)
        put("datasetVersion", "SHL Preview, pooled Hand recordings")
        put("samplesPerMode", limit)
        put("seed", seed)
        put("timeoutMs", CLASSIFICATION_TIMEOUT_MS)
        put("requestIntervalMs", REQUEST_INTERVAL_MS)
        put("apiBaseUrl", TransitousApi.BASE_URL)
        put("attribution", "https://transitous.org/sources/")
        put("timePolicy", "Planner time is omitted: server default current time. Original SHL timestamps remain in GPS/Activity replay; this is not historical route accuracy.")
        put("scope", "Production recording replay, shared App HTTP client, evidence provider and classifier; App 5-second timeout/fallback. No Firebase saving, UI, live sensors or user correction.")
        put("preprocessing", "True-label changes, day boundaries and label gaps over 300 seconds split intervals; keep intervals at least 30 seconds, then exclude intervals that cannot produce a recording with at least two accepted GPS points. Exclusions are counted in dataset metadata, outside accuracy denominators. Labels define boundaries only, never planner or classifier inputs. Mixed trips are not evaluated.")
        put("requestPolicy", "Serial requests, no extra retries/warm-ups; App defaults except time. HTTP 403/429 stops the run. No offline route data or local candidate filtering.")
        put("benchmarkMethod", "Network plus classification, excluding dataset parsing/replay and request spacing.")
        put("passCriterion", "All selected samples processed without unexpected runtime errors. Network failure/timeout remain measured outcomes; no accuracy threshold.")
        put("passed", false)
    }

    private fun writeProgress(report: JSONObject, records: List<JSONObject>, output: File) {
        report.put("records", JSONArray(records)).put("totalSamples", records.size)
        output.writeText(report.toString(2))
    }

    private fun finishReport(
        report: JSONObject,
        records: List<JSONObject>,
        completed: Boolean,
        motionThresholds: MotionThresholds?,
    ) {
        val online = records.filter {
            it.getJSONObject("planner").getString("status") == "success" && it.getBoolean("classificationCompleted")
        }
        report.put("records", JSONArray(records))
            .put("totalSamples", records.size)
            .put("skippedSamples", report.optInt("plannedSamples", 0) - records.size)
            .put("summary", TransportEvaluationMetrics.summary(records))
            .put("perMode", JSONArray(ShlEvaluationDataset.modes.map { TransportEvaluationMetrics.perMode(records, it) }))
            .put("perPrediction", TransportEvaluationMetrics.byPrediction(records))
            .put("confusionMatrix", TransportEvaluationMetrics.confusionMatrix(records))
            .put("successfulOnlineSummary", TransportEvaluationMetrics.summary(online))
            .put("plannerStatusCounts", JSONObject().apply {
                records.groupingBy { it.getJSONObject("planner").getString("status") }.eachCount()
                    .forEach { (status, count) -> put(status, count) }
            })
            .put("withTransitCandidates", records.count { it.optInt("transitRoutes") > 0 })
            .put("timeouts", records.count { it.getBoolean("timedOut") })
            .put("completed", completed)
            .put("passed", completed && records.all { it.getBoolean("runtimeSuccess") })
            .put("finishedAtMillis", System.currentTimeMillis())
        // Motion only changes the speed fallback, used when no Activity hint reaches 60.
        val fallback = records.filter { record ->
            record.isNull("activityHint") || record.getJSONObject("activityHint").getInt("confidence") < 60
        }
        report.put("sensorFallbackSummary", TransportEvaluationMetrics.summary(fallback))
        if (motionThresholds != null) {
            val variant = records.map { it.asMotionVariant() }
            report.put(
                "motionVariant",
                JSONObject()
                    .put("summary", TransportEvaluationMetrics.summary(variant))
                    .put("perMode", JSONArray(ShlEvaluationDataset.modes.map { TransportEvaluationMetrics.perMode(variant, it) }))
                    .put("confusionMatrix", TransportEvaluationMetrics.confusionMatrix(variant))
                    .put("sensorFallbackSummary", TransportEvaluationMetrics.summary(fallback.map { it.asMotionVariant() })),
            )
        }
    }

    private fun JSONObject.asMotionVariant(): JSONObject {
        val variant = getJSONObject("motionVariant")
        return JSONObject(toString())
            .put("predictedMode", variant.getString("predictedMode"))
            .put("correct", variant.getBoolean("correct"))
    }

    private fun motionThresholdsFrom(arguments: Bundle): MotionThresholds? {
        val steadyAccelStd = arguments.getString("motionSteadyAccelStd")
        val minAccelSamples = arguments.getString("motionMinAccelSamples")
        if (steadyAccelStd == null && minAccelSamples == null) return null
        require(steadyAccelStd != null && minAccelSamples != null) {
            "Supply both motionSteadyAccelStd and motionMinAccelSamples."
        }
        return MotionThresholds(steadyAccelStd.toDouble(), minAccelSamples.toInt())
    }

    private fun MotionThresholds.toJson() = JSONObject()
        .put("steadyAccelStd", steadyAccelStd)
        .put("minAccelSamples", minAccelSamples)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { stream ->
            val buffer = ByteArray(65536)
            var count = stream.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = stream.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val CLASSIFICATION_TIMEOUT_MS = 5_000L
        const val REQUEST_INTERVAL_MS = 2_000L

        fun errorJson(error: Exception) = JSONObject()
            .put("type", error.javaClass.simpleName)
            .put("message", error.message ?: JSONObject.NULL)
    }
}
