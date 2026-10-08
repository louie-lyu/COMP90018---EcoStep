package com.ecostep.app.evaluation

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.DefaultTransportClassifier
import com.ecostep.app.algorithm.MotionHint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.network.publictransport.DefaultTransportEvidenceProvider
import com.ecostep.app.network.publictransport.TransitousApi
import com.ecostep.app.network.publictransport.TransitousItinerary
import com.ecostep.app.network.publictransport.TransitousLeg
import com.ecostep.app.network.publictransport.TransitousPlace
import com.ecostep.app.network.publictransport.TransitousResponse
import com.ecostep.app.sensors.tracking.JourneySummaryBuilder
import com.ecostep.app.sensors.tracking.JourneyTracker
import com.ecostep.app.sensors.tracking.LocationSample
import com.ecostep.app.sensors.tracking.RecordingResult
import java.io.File
import java.security.MessageDigest
import java.util.Random
import java.util.StringTokenizer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: replay SHL GPS/Activity with offline transit sequences and no-transit controls. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ActivityHintTransportEvaluationTest {
    private val modes = listOf(TransportMode.WALKING, TransportMode.CYCLING, TransportMode.PUBLIC_TRANSPORT, TransportMode.CAR)
    private val whitespace = Regex("\\s+")
    private val sourceModes = mapOf(2 to TransportMode.WALKING, 4 to TransportMode.CYCLING, 5 to TransportMode.CAR,
        6 to TransportMode.PUBLIC_TRANSPORT, 7 to TransportMode.PUBLIC_TRANSPORT, 8 to TransportMode.PUBLIC_TRANSPORT)
    private val sourceNames = mapOf(2 to "walk", 4 to "bike", 5 to "car", 6 to "bus", 7 to "train", 8 to "subway")
    // No-transit control; the primary variants supply independently published offline GTFS sequences.
    private val offlineTransit = object : TransitousApi {
        override suspend fun planJourney(fromPlace: String, toPlace: String, time: String?, arriveBy: Boolean,
            maxTransfers: Int, detailedLegs: Boolean, detailedTransfers: Boolean, userAgent: String) = TransitousResponse()
    }

    @Test
    fun evaluateActivityHintTransportClassification() = runBlocking {
        val startedAt = System.currentTimeMillis()
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String, default: Int, range: IntRange): Int {
            val value = arguments.getString(name)?.toInt() ?: default
            require(value in range) { "$name must be in $range" }
            return value
        }
        val limit = argument("samplesPerMode", 10000, 1..10000)
        val warmups = argument("warmups", 10, 1..100)
        val seed = argument("seed", 42, 0..Int.MAX_VALUE)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.getExternalFilesDir("evaluation"), "shl-preview.zip")
        val baselineRecords = mutableListOf<JSONObject>()
        val baselineGpsOnly = mutableListOf<JSONObject>()
        val records = mutableListOf<JSONObject>()
        val gpsOnly = mutableListOf<JSONObject>()
        val transitFile = File(context.getExternalFilesDir("evaluation"), "transit-routes.json")
        val report = JSONObject().apply {
            put("schemaVersion", 2).put("experiment", "Offline real transit stop sequences with Activity-hint and no-transit controls, pooled SHL")
            put("primaryVariant", "recorded_activity_with_offline_transit")
            put("candidatePolicy", "Same policy for every true mode and both Activity variants: ordered origin/end stop pairs within 1000 m of the recorded journey endpoints; rank by sum of endpoint distances; at most 50 distinct published stop sequences. No labels, activity predictions, interior stop/dwell matching, calendar or timetables used for selection. Current feed is not a 2017 reconstruction.")
            put("datasetVersion", "SHL preview v1, Hand position")
            put("datasetUrl", "http://www.shl-dataset.org/download/#shldataset-preview")
            put("attribution", "University of Sussex and Huawei; Wang et al., IEEE Access 2018, doi:10.1109/ACCESS.2018.2858933. Non-profit research use.")
            put("classifier", "DefaultTransportClassifier").put("startedAtMillis", startedAt)
            put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
            put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("androidVersion", Build.VERSION.RELEASE)
            put("apiLevel", Build.VERSION.SDK_INT).put("buildType", BuildConfig.BUILD_TYPE)
            put("scope", "Recorded Google API confidences and GPS -> production JourneyTracker -> JourneySummaryBuilder -> DefaultTransportEvidenceProvider -> DefaultTransportClassifier. No live Google inference, hardware sensors, transit network, Firebase, UI or user corrections. Baseline has empty transit routes. Primary variants use independently published current GTFS sequences selected offline by endpoints; no live service or 2017 timetable validation. This is not full-app accuracy.")
            put("sampling", JSONObject().put("method", "Seeded reservoir sampling per mapped true mode pooled across all users, before GPS quality filtering; sorted user/day paths")
                .put("samplesPerMode", limit).put("seed", seed).put("warmupCallsPerMode", warmups))
            put("preprocessing", JSONObject().put("unit", "Continuous single-coarse-label interval in Label.txt, at least 30 seconds")
                .put("intervals", "Half-open [start, end); label changes, day boundaries and label gaps over 300 seconds split intervals")
                .put("timestamps", "Recorded epoch milliseconds; GPS and Google API rows are replayed in timestamp order")
                .put("activityMapping", "Like ActivityRecognitionReceiver: highest-confidence WALKING/ON_BICYCLE/IN_VEHICLE at >=60. Other types, including ON_FOOT, are ignored. Supported-confidence ties prefer WALKING, then CYCLING, then IN_VEHICLE.")
                .put("gps", "Recorded accuracy; no reported speed available. Production filtering and derived speeds are retained.")
                .put("recordingFailure", "JourneyTracker.finish returning null is reported as UNKNOWN and remains in the accuracy denominator; no classifier latency is recorded")
                .put("unavailableEvidence", "No IMU samples or historical transit routes; provided routes are current public GTFS geometry. correctedMode is null"))
            put("benchmarkMethod", "System.nanoTime around classify after warm-ups, including the production evidence provider with a cached offline response. Candidate selection is excluded from timings; route matching is included. Excludes parsing, recording replay and report writes. P50/P95 include all successful classifier calls, including UNKNOWN and incorrect predictions.")
            put("passCriterion", "All four true modes represented and all selected intervals processed without runtime errors. No accuracy threshold; unavailable recordings remain UNKNOWN.")
        }
        var completed = false
        try {
            check(transitFile.isFile) { "Transit routes missing; use scripts/run-activity-hint-transport-evaluation.ps1" }
            val transitBytes = transitFile.readBytes()
            loadRoutes(JSONObject(transitBytes.toString(Charsets.UTF_8)))
            report.put("transitDataSha256", MessageDigest.getInstance("SHA-256").digest(transitBytes).joinToString("") { "%02x".format(it) })
            report.put("transitDataMetadata", transitMetadata)
            check(archive.isFile) { "SHL ZIP missing; use scripts/run-activity-hint-transport-evaluation.ps1" }
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().buffered().use { stream ->
                val buffer = ByteArray(65536)
                var count = stream.read(buffer)
                while (count != -1) { digest.update(buffer, 0, count); count = stream.read(buffer) }
            }
            report.put("datasetSha256", digest.digest().joinToString("") { "%02x".format(it) })
            val dataset = loadDataset(archive, limit, seed)
            report.put("dataset", dataset.metadata).put("plannedSamples", dataset.samples.size)
            check(modes.all { mode -> dataset.samples.any { it.mode == mode } }) { "Dataset must contain all four supported true modes" }
            for (mode in modes) {
                dataset.samples.firstOrNull { it.mode == mode && it.recording != null }?.let { sample ->
                    repeat(warmups) {
                        classifier(sample, false).classify(checkNotNull(sample.journey))
                        classifier(sample, true).classify(checkNotNull(sample.journey))
                        classifier(sample, false, true).classify(checkNotNull(sample.journey))
                        classifier(sample, true, true).classify(checkNotNull(sample.journey))
                    }
                }
            }
            for (sample in dataset.samples) {
                baselineRecords += evaluate(sample, false)
                baselineGpsOnly += evaluate(sample, true)
                records += evaluate(sample, false, true)
                gpsOnly += evaluate(sample, true, true)
            }
            completed = true
        } catch (error: Exception) {
            report.put("runError", errorJson(error))
            throw error
        } finally {
            report.put("baselineRecords", JSONArray(baselineRecords)).put("baselineGpsOnlyRecords", JSONArray(baselineGpsOnly))
            report.put("totalSamples", baselineRecords.size).put("skippedSamples", report.optInt("plannedSamples", 0) - baselineRecords.size)
            report.put("baselineSummary", metrics(baselineRecords)).put("baselineGpsOnlySummary", metrics(baselineGpsOnly))
            report.put("baselinePerMode", JSONArray(modes.map { perMode(baselineRecords, it) }))
            report.put("baselineGpsOnlyPerMode", JSONArray(modes.map { perMode(baselineGpsOnly, it) }))
            report.put("baselineConfusionMatrix", confusionMatrix(baselineRecords)).put("baselineGpsOnlyConfusionMatrix", confusionMatrix(baselineGpsOnly))
            report.put("records", JSONArray(records)).put("gpsOnlyRecords", JSONArray(gpsOnly))
            report.put("summary", metrics(records)).put("gpsOnlySummary", metrics(gpsOnly))
            report.put("perMode", JSONArray(modes.map { perMode(records, it) }))
            report.put("gpsOnlyPerMode", JSONArray(modes.map { perMode(gpsOnly, it) }))
            report.put("confusionMatrix", confusionMatrix(records))
            report.put("gpsOnlyConfusionMatrix", confusionMatrix(gpsOnly))
            report.put("candidateCoverage", JSONObject().put("recordings", records.count { it.getBoolean("recordingAvailable") })
                .put("withCandidates", records.count { it.optInt("transitRoutes") > 0 })
                .put("candidateSequencesAvailable", routes.size))
            report.put("passed", completed && (baselineRecords + baselineGpsOnly + records + gpsOnly).all { it.getBoolean("runtimeSuccess") })
            report.put("finishedAtMillis", System.currentTimeMillis())
            val directory = File(context.filesDir, "evaluation")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create evaluation directory" }
            File(directory, "activity-hint-transport-evaluation-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)
        }
        assertTrue("Activity hint evaluation did not complete; inspect the JSON report", report.getBoolean("passed"))
    }

    private fun classifier(sample: Sample, gpsOnly: Boolean, withTransit: Boolean = false) = DefaultTransportClassifier(
        DefaultTransportEvidenceProvider(checkNotNull(sample.recording).let { if (gpsOnly) it.copy(activityHint = null) else it }, if (withTransit) candidateApi(sample) else offlineTransit),
    )

    private suspend fun evaluate(sample: Sample, gpsOnly: Boolean, withTransit: Boolean = false): JSONObject {
        val recording = sample.recording
        val hint = if (gpsOnly) null else recording?.activityHint
        val record = JSONObject().put("sampleId", sample.id).put("userId", sample.user)
            .put("sourceMode", sourceNames.getValue(sample.source)).put("trueMode", sample.mode.name)
            .put("correctedMode", JSONObject.NULL).put("predictedMode", JSONObject.NULL).put("confidence", JSONObject.NULL)
            .put("startTimeMillis", sample.start).put("endTimeMillis", sample.end)
            .put("rawGpsPoints", sample.gpsPoints).put("acceptedGpsPoints", sample.acceptedGpsPoints)
            .put("activitySamples", sample.activityPoints).put("acceptedActivityHints", sample.acceptedHints)
            .put("activityConfidenceTies", sample.activityTies)
            .put("activityHint", hint?.let { JSONObject().put("type", it.type.name).put("confidence", it.confidence) } ?: JSONObject.NULL)
            .put("trackPoints", recording?.trace?.size ?: 0).put("transitRoutes", if (withTransit) candidates(sample).size else 0)
            .put("candidateRouteIds", JSONArray(if (withTransit) candidates(sample).map { it.id } else emptyList<String>()))
            .put("recordingAvailable", recording != null).put("classifierCalled", recording != null)
            .put("runtimeSuccess", false).put("correct", false).put("durationMs", JSONObject.NULL)
        if (recording == null) {
            return record.put("predictedMode", "UNKNOWN").put("runtimeSuccess", true)
                .put("reason", "Recording unavailable after production GPS filtering")
        }
        val classifier = classifier(sample, gpsOnly, withTransit)
        val journey = checkNotNull(sample.journey)
        val start = System.nanoTime()
        var durationMs = 0.0
        try {
            val result = try {
                classifier.classify(journey)
            } finally {
                durationMs = (System.nanoTime() - start) / 1_000_000.0
            }
            record.put("predictedMode", result.mode.name).put("confidence", result.confidence)
                .put("runtimeSuccess", true).put("correct", result.mode == sample.mode)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            record.put("error", errorJson(error))
        } finally {
            record.put("durationMs", durationMs)
        }
        return record
    }

    private data class Interval(val user: String, val prefix: String, val start: Long, val end: Long, val source: Int)
    private data class ActivitySample(val time: Long, val hint: ActivityHint?, val tied: Boolean)
    private data class Sample(val id: String, val user: String, val start: Long, val end: Long, val source: Int,
        val mode: TransportMode, val gpsPoints: Int, val acceptedGpsPoints: Int, val activityPoints: Int, val acceptedHints: Int, val activityTies: Int,
        val recording: RecordingResult?, val journey: JourneySummary?)
    private data class Dataset(val samples: List<Sample>, val metadata: JSONObject)

    private fun loadDataset(archive: File, limit: Int, seed: Int): Dataset = ZipFile(archive).use { zip ->
        val entries = zip.entries().asSequence().toList()
        val labelFiles = entries.filter { it.name.endsWith("/Label.txt") && !it.isDirectory }.sortedBy { it.name }
        check(labelFiles.isNotEmpty()) { "SHL ZIP contains no Label.txt files" }
        val selected = modes.associateWith { mutableListOf<Interval>() }
        val eligible = modes.associateWith { 0 }.toMutableMap()
        val random = modes.associateWith { Random(seed.toLong() + it.ordinal) }
        var labelRows = 0
        var shortIntervals = 0
        var unsupportedIntervals = 0
        fun offer(interval: Interval) {
            val mode = sourceModes[interval.source]
            if (mode == null) { unsupportedIntervals++; return }
            if (interval.end - interval.start < 30_000L) { shortIntervals++; return }
            val seen = eligible.getValue(mode) + 1
            eligible[mode] = seen
            val cases = selected.getValue(mode)
            if (cases.size < limit) cases += interval else {
                val replacement = random.getValue(mode).nextInt(seen)
                if (replacement < limit) cases[replacement] = interval
            }
        }
        for (entry in labelFiles) {
            val prefix = entry.name.removeSuffix("Label.txt")
            val user = prefix.split('/').firstOrNull { it.startsWith("User") } ?: error("Missing user in ${entry.name}")
            var first = 0L
            var previous = 0L
            var source = -1
            zip.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.forEach { line ->
                    val fields = StringTokenizer(line)
                    val time = fields.nextToken().toLong()
                    val label = fields.nextToken().toInt()
                    check(time > previous) { "Non-increasing label timestamps in ${entry.name}" }
                    if (source != -1 && (label != source || time - previous > 300_000L)) {
                        offer(Interval(user, prefix, first, if (time - previous > 300_000L) previous + 1 else time, source))
                        source = -1
                    }
                    if (source == -1) { first = time; source = label }
                    previous = time
                    labelRows++
                }
            }
            if (source != -1) offer(Interval(user, prefix, first, previous + 1, source))
            println("SHL: labels processed for ${prefix.trimEnd('/')}")
        }
        val intervals = selected.values.flatten().sortedWith(compareBy({ it.prefix }, { it.start }))
        val samples = mutableListOf<Sample>()
        var gpsRows = 0
        var activityRows = 0
        var activityTies = 0
        for ((prefix, group) in intervals.groupBy { it.prefix }) {
            fun entry(name: String) = checkNotNull(zip.getEntry("$prefix$name")) { "Missing $prefix$name" }
            val gps = readRows(zip, entry("Hand_Location.txt"), 7) { fields ->
                LocationSample(fields[4].toDouble(), fields[5].toDouble(), fields[3].toFloat(), null, fields[0].toLong())
            }.sortedBy { it.timeMillis }
            val activities = readRows(zip, entry("Hand_API.txt"), 11) { fields ->
                val hints = listOf(MotionHint.WALKING to 5, MotionHint.CYCLING to 7, MotionHint.IN_VEHICLE to 8)
                    .map { (type, column) -> ActivityHint(type, fields[column].toInt()) }
                check(hints.all { it.confidence in 0..100 }) { "Invalid Google confidence" }
                val hint = hints.filter { it.confidence >= 60 }.maxByOrNull { it.confidence }
                ActivitySample(fields[0].toLong(), hint, hint != null && hints.count { it.confidence == hint.confidence } > 1)
            }.sortedBy { it.time }
            gpsRows += gps.size
            activityRows += activities.size
            activityTies += activities.count { it.tied }
            for (interval in group) {
                val locations = gps.filter { it.timeMillis >= interval.start && it.timeMillis < interval.end }
                val hints = activities.filter { it.time >= interval.start && it.time < interval.end }
                val tracker = JourneyTracker()
                check(tracker.begin(interval.start))
                var activityIndex = 0
                for (location in locations) {
                    while (activityIndex < hints.size && hints[activityIndex].time <= location.timeMillis) {
                        hints[activityIndex++].hint?.let { tracker.onActivityHint(it) }
                    }
                    tracker.onLocation(location)
                }
                while (activityIndex < hints.size) hints[activityIndex++].hint?.let { tracker.onActivityHint(it) }
                val acceptedGps = tracker.state.value.gpsCount
                val recording = tracker.finish(interval.end)
                val id = "${interval.user}_${prefix.trimEnd('/').substringAfterLast('/')}_${interval.start}"
                val journey = recording?.let { JourneySummaryBuilder().build(it, interval.user).copy(journeyId = id) }
                samples += Sample(id, interval.user, interval.start, interval.end, interval.source,
                    sourceModes.getValue(interval.source), locations.size, acceptedGps, hints.size, hints.count { it.hint != null },
                    hints.count { it.tied }, recording, journey)
            }
            println("SHL: GPS and Google activity replayed for ${prefix.trimEnd('/')}")
        }
        val metadata = JSONObject().put("labelFiles", labelFiles.size).put("labelRows", labelRows)
            .put("users", JSONArray(labelFiles.map { it.name.split('/').first { part -> part.startsWith("User") } }.distinct()))
            .put("loadedGpsPoints", gpsRows).put("loadedActivitySamples", activityRows)
            .put("supportedConfidenceTieSamples", activityTies)
            .put("shortIntervals", shortIntervals).put("unsupportedIntervals", unsupportedIntervals)
            .put("eligibleSegmentsPerMode", JSONObject().apply { eligible.forEach { (mode, count) -> put(mode.name, count) } })
            .put("selectedSegmentsPerMode", JSONObject().apply { selected.forEach { (mode, cases) -> put(mode.name, cases.size) } })
        zip.getEntry("manifest.json")?.let { metadata.put("downloadManifest", JSONObject(zip.getInputStream(it).bufferedReader().use { reader -> reader.readText() })) }
        Dataset(samples, metadata)
    }

    private fun <T> readRows(zip: ZipFile, entry: ZipEntry, columns: Int, parse: (List<String>) -> T): List<T> =
        zip.getInputStream(entry).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line ->
                val fields = line.trim().split(whitespace)
                check(fields.size == columns) { "Expected $columns columns in ${entry.name}" }
                parse(fields)
            }.toList()
        }

    private fun metrics(baselineRecords: List<JSONObject>): JSONObject {
        val successful = baselineRecords.filter { it.getBoolean("runtimeSuccess") }
        val classified = successful.count { it.getString("predictedMode") != "UNKNOWN" }
        val correct = baselineRecords.count { it.getBoolean("correct") }
        val times = successful.filter { !it.isNull("durationMs") }.map { it.getDouble("durationMs") }.sorted()
        fun percentile(fraction: Double): Any = if (times.isEmpty()) JSONObject.NULL else times[ceil(times.size * fraction).toInt() - 1]
        val f1 = modes.filter { mode -> baselineRecords.any { it.getString("trueMode") == mode.name } }.map { perMode(baselineRecords, it).getDouble("f1") }
        return JSONObject().put("totalSamples", baselineRecords.size).put("correctSamples", correct)
            .put("incorrectSamples", baselineRecords.size - correct).put("accuracy", ratio(correct, baselineRecords.size))
            .put("unknownSamples", successful.size - classified).put("unknownRate", ratio(successful.size - classified, baselineRecords.size))
            .put("classifiedSamples", classified).put("coverage", ratio(classified, baselineRecords.size))
            .put("accuracyAmongClassified", ratio(correct, classified)).put("runtimeErrors", baselineRecords.size - successful.size)
            .put("recordingFailures", baselineRecords.count { !it.getBoolean("recordingAvailable") })
            .put("latencySamples", times.size).put("p50Ms", percentile(0.50)).put("p95Ms", percentile(0.95))
            .put("macroF1", if (f1.isEmpty()) JSONObject.NULL else f1.average())
    }

    private fun perMode(baselineRecords: List<JSONObject>, mode: TransportMode): JSONObject {
        val support = baselineRecords.filter { it.getString("trueMode") == mode.name }
        val tp = support.count { it.getBoolean("correct") }
        val fp = baselineRecords.count { it.optString("predictedMode") == mode.name && it.getString("trueMode") != mode.name }
        val unknown = support.count { it.optString("predictedMode") == "UNKNOWN" }
        return JSONObject().put("mode", mode.name).put("totalSamples", support.size).put("correctSamples", tp)
            .put("accuracy", ratio(tp, support.size)).put("precision", ratio(tp, tp + fp)).put("recall", ratio(tp, support.size))
            .put("f1", ratio(2 * tp, support.size + tp + fp)).put("unknownRate", ratio(unknown, support.size))
    }

    private fun confusionMatrix(baselineRecords: List<JSONObject>) = JSONObject().apply {
        for (mode in modes) put(mode.name, JSONObject().apply {
            for (prediction in modes.map { it.name } + listOf("UNKNOWN", "ERROR")) put(prediction, baselineRecords.count {
                it.getString("trueMode") == mode.name && (if (it.getBoolean("runtimeSuccess")) it.getString("predictedMode") else "ERROR") == prediction
            })
        })
    }

    private fun errorJson(error: Exception) = JSONObject().put("type", error.javaClass.simpleName).put("message", error.message ?: JSONObject.NULL)
    private fun ratio(numerator: Int, denominator: Int): Any = if (denominator == 0) JSONObject.NULL else numerator.toDouble() / denominator
    private data class Route(val id: String, val mode: String, val stops: List<TransitousPlace>)
    private var routes = emptyList<Route>()
    private var transitMetadata = JSONObject()
    private val candidateCache = mutableMapOf<String, List<Route>>()

    private fun loadRoutes(data: JSONObject) {
        transitMetadata = data.getJSONObject("metadata")
        val list = data.getJSONArray("routes")
        routes = (0 until list.length()).map { index ->
            val route = list.getJSONObject(index)
            val stops = route.getJSONArray("stops")
            Route(route.getString("id"), route.getString("mode"), (0 until stops.length()).map { i ->
                val point = stops.getJSONArray(i)
                TransitousPlace(point.getDouble(0), point.getDouble(1))
            })
        }
    }

    private fun candidates(sample: Sample): List<Route> = candidateCache.getOrPut(sample.id) {
        val journey = sample.journey
        if (journey == null) emptyList() else routes.mapNotNull { route ->
            var closestOrigin = Double.POSITIVE_INFINITY
            var score = Double.POSITIVE_INFINITY
            for (point in route.stops) {
                val endDistance = meters(point.lat, point.lon, journey.endLocation.latitude, journey.endLocation.longitude)
                if (endDistance <= 1000.0 && closestOrigin <= 1000.0) score = minOf(score, closestOrigin + endDistance)
                val startDistance = meters(point.lat, point.lon, journey.startLocation.latitude, journey.startLocation.longitude)
                if (startDistance <= 1000.0) closestOrigin = minOf(closestOrigin, startDistance)
            }
            if (score.isFinite()) route to score else null
        }.sortedWith(compareBy<Pair<Route, Double>> { it.second }.thenBy { it.first.id }).take(50).map { it.first }
    }

    private fun candidateApi(sample: Sample): TransitousApi {
        val selected = candidates(sample)
        // Candidate legs are a fixture for the production mapper, not a planned connected itinerary.
        val response = TransitousResponse(listOf(TransitousItinerary(0L, "", "", legs = selected.map { route ->
            TransitousLeg(mode = route.mode, startTime = "", endTime = "", routeShortName = route.id,
                from = route.stops.first(), to = route.stops.last(), intermediateStops = route.stops.drop(1).dropLast(1))
        })))
        return object : TransitousApi {
            override suspend fun planJourney(fromPlace: String, toPlace: String, time: String?, arriveBy: Boolean,
                maxTransfers: Int, detailedLegs: Boolean, detailedTransfers: Boolean, userAgent: String) = response
        }
    }

    private fun meters(a: Double, b: Double, c: Double, d: Double): Double {
        val lat = Math.toRadians(c-a); val lon = Math.toRadians(d-b)
        val x = kotlin.math.sin(lat/2) * kotlin.math.sin(lat/2) + kotlin.math.cos(Math.toRadians(a)) *
            kotlin.math.cos(Math.toRadians(c)) * kotlin.math.sin(lon/2) * kotlin.math.sin(lon/2)
        return 12742000.0 * kotlin.math.asin(kotlin.math.sqrt(x.coerceIn(0.0,1.0)))
    }

}
