package com.ecostep.app.evaluation

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.algorithm.DefaultTransportClassifier
import com.ecostep.app.algorithm.TransportEvidence
import com.ecostep.app.algorithm.TransportEvidenceProvider
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.sensors.tracking.FeatureAccumulator
import com.ecostep.app.sensors.tracking.LocationSample
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import kotlin.math.ceil
import kotlin.math.roundToLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Rui Fang: labeled GeoLife GPS replay; no live activity recognition, transit API or user corrections. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TransportClassificationEvaluationTest {
    private val modes = listOf(TransportMode.WALKING, TransportMode.CYCLING, TransportMode.PUBLIC_TRANSPORT, TransportMode.CAR)
    private val classifier = DefaultTransportClassifier(object : TransportEvidenceProvider {
        override suspend fun getEvidence(journey: JourneySummary) = TransportEvidence()
    })

    @Test
    fun evaluateTransportClassification() = runBlocking {
        val startedAt = System.currentTimeMillis()
        val arguments = InstrumentationRegistry.getArguments()
        fun argument(name: String, default: Int, range: IntRange): Int {
            val value = arguments.getString(name)?.toInt() ?: default
            require(value in range) { "$name must be in $range" }
            return value
        }
        val samplesPerMode = argument("samplesPerMode", 100, 1..10000)
        val warmups = argument("warmups", 10, 1..100)
        val seed = argument("seed", 42, 0..Int.MAX_VALUE)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.getExternalFilesDir("evaluation"), "geolife.zip")
        val records = mutableListOf<JSONObject>()
        val report = JSONObject().apply {
            put("schemaVersion", 1)
            put("datasetVersion", "GeoLife 1.3")
            put("datasetUrl", "https://www.microsoft.com/en-us/download/details.aspx?id=52367")
            put("classifier", "DefaultTransportClassifier")
            put("startedAtMillis", startedAt)
            put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
            put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidVersion", Build.VERSION.RELEASE)
            put("apiLevel", Build.VERSION.SDK_INT)
            put("buildType", BuildConfig.BUILD_TYPE)
            put("scope", "Labeled public GPS replay with production FeatureAccumulator and DefaultTransportClassifier. No ActivityHint, transit routes, network, Firebase, UI or user corrections. Vehicle predictions can be UNKNOWN without transit evidence; this is not full-app accuracy.")
            put("sampling", JSONObject().put("method", "Seeded reservoir sampling per mapped mode across all labeled users; sorted archive traversal")
                .put("samplesPerMode", samplesPerMode).put("seed", seed).put("warmupCallsPerMode", warmups))
            put("preprocessing", JSONObject().put("unit", "One continuous single-label segment within one trajectory file")
                .put("minimumDurationSeconds", 30).put("minimumAcceptedGpsPoints", 2).put("maximumGapSeconds", 300)
                .put("labelIntervals", "Half-open [start, end); intervals overlapping any other label are excluded entirely")
                .put("nonIncreasingTimestamps", "Duplicate or out-of-order points are discarded, without splitting the segment")
                .put("timestamp", "UTC Excel days, rounded to the nearest millisecond")
                .put("gpsAccuracy", "Unavailable: adapter supplies 0 m solely to allow feature extraction; not a measured accuracy")
                .put("motionSensors", "Unavailable: no samples supplied")
                .put("labelMapping", JSONObject().put("walk", "WALKING").put("bike", "CYCLING")
                    .put("bus/train/subway", "PUBLIC_TRANSPORT").put("car/taxi", "CAR")))
            put("benchmarkMethod", "System.nanoTime around one classify call per selected segment after warm-ups. Includes empty evidence provider and coroutine overhead; excludes ZIP parsing, feature extraction and JSON writes. Nearest-rank P50/P95 include all runtime-successful calls, including incorrect and UNKNOWN predictions; no latency threshold.")
            put("passCriterion", "All four true modes represented and every selected segment evaluated without a runtime error. No accuracy threshold; misclassifications are evaluation results.")
        }
        var completed = false
        try {
            check(archive.isFile) { "GeoLife archive missing; use scripts/run-transport-classification-evaluation.ps1" }
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().buffered().use { stream ->
                val buffer = ByteArray(65536)
                var count = stream.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = stream.read(buffer)
                }
            }
            report.put("datasetSha256", digest.digest().joinToString("") { "%02x".format(it) })
            val dataset = DatasetLoader(samplesPerMode, seed).load(archive)
            report.put("dataset", dataset.metadata)
            report.put("plannedSamples", dataset.samples.size)
            check(modes.all { mode -> dataset.samples.any { it.trueMode == mode } }) { "Dataset must contain all four supported true modes" }
            for (mode in modes) {
                val sample = dataset.samples.first { it.trueMode == mode }
                repeat(warmups) { classifier.classify(sample.journey) }
            }
            for (sample in dataset.samples) {
                val record = JSONObject().put("sampleId", sample.journey.journeyId).put("userId", sample.journey.userId)
                    .put("sourceMode", sample.sourceMode).put("trueMode", sample.trueMode.name)
                    .put("correctedMode", JSONObject.NULL).put("predictedMode", JSONObject.NULL)
                    .put("confidence", JSONObject.NULL).put("runtimeSuccess", false).put("correct", false)
                val start = System.nanoTime()
                var durationMs = 0.0
                try {
                    val result = try {
                        classifier.classify(sample.journey)
                    } finally {
                        durationMs = (System.nanoTime() - start) / 1_000_000.0
                    }
                    record.put("predictedMode", result.mode.name).put("confidence", result.confidence)
                        .put("runtimeSuccess", true).put("correct", result.mode == sample.trueMode)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    record.put("error", errorJson(error))
                } finally {
                    record.put("durationMs", durationMs)
                    records.add(record)
                }
            }
            completed = true
        } catch (error: Exception) {
            report.put("runError", errorJson(error))
            throw error
        } finally {
            report.put("totalSamples", records.size)
            report.put("skippedSamples", report.optInt("plannedSamples", 0) - records.size)
            report.put("summary", metrics(records))
            val perMode = modes.map { mode ->
                val support = records.filter { it.getString("trueMode") == mode.name }
                val truePositives = support.count { it.getBoolean("correct") }
                val falsePositives = records.count { it.optString("predictedMode") == mode.name && it.getString("trueMode") != mode.name }
                metrics(support).put("mode", mode.name)
                    .put("precision", ratio(truePositives, truePositives + falsePositives))
                    .put("recall", ratio(truePositives, support.size))
                    .put("f1", ratio(2 * truePositives, support.size + truePositives + falsePositives))
            }
            report.put("perMode", JSONArray(perMode))
            val matrix = JSONObject()
            for (mode in modes) {
                val row = JSONObject()
                for (prediction in modes.map { it.name } + listOf("UNKNOWN", "ERROR")) {
                    row.put(prediction, records.count {
                        it.getString("trueMode") == mode.name &&
                            (if (it.getBoolean("runtimeSuccess")) it.getString("predictedMode") else "ERROR") == prediction
                    })
                }
                matrix.put(mode.name, row)
            }
            report.put("confusionMatrix", matrix)
            val f1Scores = perMode.filter { it.getInt("totalSamples") > 0 }.map { it.getDouble("f1") }
            report.getJSONObject("summary").put("macroF1", if (f1Scores.isEmpty()) JSONObject.NULL else f1Scores.average())
            report.put("records", JSONArray(records))
            report.put("passed", completed && records.all { it.getBoolean("runtimeSuccess") })
            report.put("finishedAtMillis", System.currentTimeMillis())
            val directory = File(context.filesDir, "evaluation")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create evaluation directory" }
            File(directory, "transport-classification-evaluation-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)
        }
        assertTrue("Transport classification evaluation did not complete; inspect the JSON report", report.getBoolean("passed"))
    }

    private fun metrics(records: List<JSONObject>): JSONObject {
        val successful = records.filter { it.getBoolean("runtimeSuccess") }
        val classified = successful.count { it.getString("predictedMode") != "UNKNOWN" }
        val correct = records.count { it.getBoolean("correct") }
        val unknown = successful.size - classified
        val times = successful.map { it.getDouble("durationMs") }.sorted()
        fun percentile(fraction: Double): Any = if (times.isEmpty()) JSONObject.NULL else
            times[ceil(times.size * fraction).toInt() - 1]
        return JSONObject().put("totalSamples", records.size).put("correctSamples", correct)
            .put("incorrectSamples", records.size - correct).put("accuracy", ratio(correct, records.size))
            .put("unknownSamples", unknown).put("unknownRate", ratio(unknown, records.size))
            .put("classifiedSamples", classified).put("coverage", ratio(classified, records.size))
            .put("accuracyAmongClassified", ratio(correct, classified))
            .put("runtimeErrors", records.size - successful.size).put("latencySamples", times.size)
            .put("p50Ms", percentile(0.50)).put("p95Ms", percentile(0.95))
    }

    private data class Label(val start: Long, val end: Long, val sourceMode: String, val mode: TransportMode?)
    private data class Sample(val journey: JourneySummary, val trueMode: TransportMode, val sourceMode: String)
    private data class Dataset(val samples: List<Sample>, val metadata: JSONObject)

    /** Streaming reader: bounds memory while keeping a reproducible sample of every supported mode. */
    private inner class DatasetLoader(private val limit: Int, seed: Int) {
        private val samples = modes.associateWith { mutableListOf<Sample>() }
        private val eligible = modes.associateWith { 0 }.toMutableMap()
        private val random = modes.associateWith { Random(seed.toLong() + it.ordinal) }
        private val labelFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
        private val whitespace = Regex("\\s+")
        private var scannedPoints = 0
        private var unlabeledPoints = 0
        private var unsupportedPoints = 0
        private var malformedPoints = 0
        private var rejectedPoints = 0
        private var shortSegments = 0
        private var insufficientSegments = 0
        private var overlappingLabels = 0
        private var nonIncreasingPoints = 0

        fun load(archive: File): Dataset = ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val labelFiles = entries.filter { it.name.endsWith("/labels.txt") }.sortedBy { it.name }
            check(labelFiles.isNotEmpty()) { "GeoLife ZIP contains no labels.txt files" }
            var trajectories = 0
            labelFiles.forEachIndexed { index, entry ->
                val prefix = entry.name.removeSuffix("labels.txt")
                val user = prefix.trimEnd('/').substringAfterLast('/')
                val labels = readLabels(zip, entry)
                val tracks = entries.filter { it.name.startsWith("${prefix}Trajectory/") && it.name.endsWith(".plt") }.sortedBy { it.name }
                tracks.forEach { readTrajectory(zip, it, user, labels) }
                trajectories += tracks.size
                println("GeoLife: ${index + 1}/${labelFiles.size} labeled users processed")
            }
            Dataset(samples.values.flatten().sortedBy { it.journey.journeyId }, JSONObject()
                .put("labeledUsers", labelFiles.size).put("trajectoryFiles", trajectories).put("scannedGpsPoints", scannedPoints)
                .put("unlabeledGpsPoints", unlabeledPoints).put("unsupportedModeGpsPoints", unsupportedPoints)
                .put("malformedGpsPoints", malformedPoints).put("rejectedGpsPoints", rejectedPoints)
                .put("excludedOverlappingLabelIntervals", overlappingLabels)
                .put("nonIncreasingGpsPoints", nonIncreasingPoints)
                .put("shortSegments", shortSegments).put("insufficientGpsSegments", insufficientSegments)
                .put("eligibleSegmentsPerMode", JSONObject().apply { eligible.forEach { (mode, count) -> put(mode.name, count) } })
                .put("selectedSegmentsPerMode", JSONObject().apply { samples.forEach { (mode, cases) -> put(mode.name, cases.size) } }))
        }

        private fun readLabels(zip: ZipFile, entry: ZipEntry): List<Label> {
            val labels = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1).filter { it.isNotBlank() }.map { line ->
                    val fields = line.trim().split(whitespace)
                    check(fields.size == 5) { "Invalid GeoLife label in ${entry.name}" }
                    fun time(offset: Int) = LocalDateTime.parse("${fields[offset]} ${fields[offset + 1]}", labelFormat)
                        .toInstant(ZoneOffset.UTC).toEpochMilli()
                    val source = fields[4].lowercase()
                    val mode = when (source) {
                        "walk" -> TransportMode.WALKING
                        "bike" -> TransportMode.CYCLING
                        "bus", "train", "subway" -> TransportMode.PUBLIC_TRANSPORT
                        "car", "taxi" -> TransportMode.CAR
                        else -> null
                    }
                    Label(time(0), time(2), source, mode).also { check(it.end >= it.start) { "Reversed GeoLife label interval" } }
                }.sortedBy { it.start }.toList()
            }
            var latestEnd = Long.MIN_VALUE
            return labels.filterIndexed { index, label ->
                val overlaps = label.start < latestEnd || (labels.getOrNull(index + 1)?.start ?: Long.MAX_VALUE) < label.end
                latestEnd = maxOf(latestEnd, label.end)
                if (overlaps) overlappingLabels++
                !overlaps
            }
        }

        private fun labelAt(labels: List<Label>, time: Long): Label? {
            var low = 0
            var high = labels.lastIndex
            while (low <= high) {
                val middle = (low + high) / 2
                if (labels[middle].start <= time) low = middle + 1 else high = middle - 1
            }
            return labels.getOrNull(high)?.takeIf { time < it.end }
        }

        private fun readTrajectory(zip: ZipFile, entry: ZipEntry, user: String, labels: List<Label>) {
            var currentLabel: Label? = null
            var accumulator = FeatureAccumulator()
            var previousTime: Long? = null
            var segment = 0
            fun finish() {
                val label = currentLabel ?: return
                val first = accumulator.first
                val last = accumulator.last
                if (first == null || last == null || accumulator.gpsCount < 2) {
                    insufficientSegments++
                } else if (last.timeMillis - first.timeMillis < 30_000L) {
                    shortSegments++
                } else {
                    val mode = checkNotNull(label.mode)
                    val name = entry.name.substringAfterLast('/').removeSuffix(".plt")
                    val journey = JourneySummary("${user}_${name}_$segment", user,
                        GeoPoint(first.latitude, first.longitude), GeoPoint(last.latitude, last.longitude),
                        first.timeMillis, last.timeMillis, accumulator.distanceMeters, TransportMode.UNKNOWN,
                        accumulator.toFeatures(last.timeMillis - first.timeMillis))
                    val seen = eligible.getValue(mode) + 1
                    eligible[mode] = seen
                    val selected = samples.getValue(mode)
                    val sample = Sample(journey, mode, label.sourceMode)
                    if (selected.size < limit) selected.add(sample) else {
                        val replacement = random.getValue(mode).nextInt(seen)
                        if (replacement < limit) selected[replacement] = sample
                    }
                }
                segment++
                accumulator = FeatureAccumulator()
                currentLabel = null
                previousTime = null
            }
            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(6).filter { it.isNotBlank() }.forEach { line ->
                    scannedPoints++
                    val fields = line.split(',')
                    val latitude = fields.getOrNull(0)?.toDoubleOrNull()
                    val longitude = fields.getOrNull(1)?.toDoubleOrNull()
                    val days = fields.getOrNull(4)?.toDoubleOrNull()
                    if (latitude == null || longitude == null || days == null || !days.isFinite() || days !in 25569.0..100000.0) {
                        malformedPoints++
                        finish()
                        return@forEach
                    }
                    val time = ((days - 25569.0) * 86_400_000.0).roundToLong()
                    val gap = previousTime?.let { time - it }
                    if (gap != null && gap <= 0) {
                        nonIncreasingPoints++
                        return@forEach
                    }
                    val label = labelAt(labels, time)
                    if (label == null || label.mode == null) {
                        if (label == null) unlabeledPoints++ else unsupportedPoints++
                        finish()
                        return@forEach
                    }
                    if (label != currentLabel || (gap != null && gap > 300_000L)) finish()
                    currentLabel = label
                    previousTime = time
                    // GeoLife has neither reported speed nor GPS accuracy; derive speed without inventing evidence.
                    if (!accumulator.onLocation(LocationSample(latitude, longitude, 0f, null, time))) rejectedPoints++
                }
            }
            finish()
        }
    }

    private fun errorJson(error: Exception) = JSONObject().put("type", error.javaClass.simpleName)
        .put("message", error.message ?: JSONObject.NULL)

    private fun ratio(numerator: Int, denominator: Int): Any =
        if (denominator == 0) JSONObject.NULL else numerator.toDouble() / denominator
}
