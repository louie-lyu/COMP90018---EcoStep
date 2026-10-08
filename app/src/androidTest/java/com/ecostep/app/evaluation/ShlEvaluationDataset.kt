package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.ActivityHint
import com.ecostep.app.algorithm.MotionHint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.sensors.tracking.JourneySummaryBuilder
import com.ecostep.app.sensors.tracking.JourneyTracker
import com.ecostep.app.sensors.tracking.LocationSample
import com.ecostep.app.sensors.tracking.RecordingResult
import java.io.File
import java.util.Random
import java.util.StringTokenizer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject

/** Reads single-label SHL intervals and replays their recorded sensor inputs. */
internal object ShlEvaluationDataset {
    val modes = listOf(TransportMode.WALKING, TransportMode.CYCLING, TransportMode.PUBLIC_TRANSPORT, TransportMode.CAR)
    private val whitespace = Regex("\\s+")
    private val sourceModes = mapOf(2 to TransportMode.WALKING, 4 to TransportMode.CYCLING, 5 to TransportMode.CAR,
        6 to TransportMode.PUBLIC_TRANSPORT, 7 to TransportMode.PUBLIC_TRANSPORT, 8 to TransportMode.PUBLIC_TRANSPORT)
    val sourceNames = mapOf(2 to "walk", 4 to "bike", 5 to "car", 6 to "bus", 7 to "train", 8 to "subway")

    private data class Interval(val user: String, val prefix: String, val start: Long, val end: Long, val source: Int)
    private data class ActivitySample(val time: Long, val hint: ActivityHint?, val tied: Boolean)
    data class Sample(
        val id: String,
        val user: String,
        val start: Long,
        val end: Long,
        val source: Int,
        val mode: TransportMode,
        val gpsPoints: Int,
        val acceptedGpsPoints: Int,
        val activityPoints: Int,
        val acceptedHints: Int,
        val activityTies: Int,
        val recording: RecordingResult?,
        val journey: JourneySummary?,
    )
    data class Dataset(val samples: List<Sample>, val metadata: JSONObject)

    fun load(archive: File, limit: Int, seed: Int): Dataset = ZipFile(archive).use { zip ->
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
        // Labels define evaluation boundaries; never pass them to the classifier or planner.
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
                LocationSample(
                    latitude = fields[4].toDouble(),
                    longitude = fields[5].toDouble(),
                    accuracyMeters = fields[3].toFloat(),
                    speedMps = null,
                    timeMillis = fields[0].toLong(),
                )
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
            // Replay GPS and recorded Google Activity in timestamp order through production code.
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
                samples += Sample(
                    id = id,
                    user = interval.user,
                    start = interval.start,
                    end = interval.end,
                    source = interval.source,
                    mode = sourceModes.getValue(interval.source),
                    gpsPoints = locations.size,
                    acceptedGpsPoints = acceptedGps,
                    activityPoints = hints.size,
                    acceptedHints = hints.count { it.hint != null },
                    activityTies = hints.count { it.tied },
                    recording = recording,
                    journey = journey,
                )
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

}
