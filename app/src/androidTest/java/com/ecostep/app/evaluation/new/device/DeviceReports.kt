package com.ecostep.app.evaluation.new.device

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Gradle pulls this directory even when an instrumentation assertion fails. */
internal fun writeDeviceReport(name: String, report: JSONObject) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        ?.let(::File) ?: File(instrumentation.targetContext.getExternalFilesDir(null), "evaluation")
    directory.mkdirs()
    report.remove("gitCommit")
    report.remove("workingTreeDirty")
    File(directory, "$name.json").writeText(report.toString(2))
    val records = report.optJSONArray("records") ?: JSONArray()
    val rows = (0 until records.length()).map { records.getJSONObject(it) }
    val columns = rows.flatMap { it.keys().asSequence().toList() }.distinct()
    fun csv(value: Any?) = "\"${if (value == null || value == JSONObject.NULL) "" else value.toString().replace("\"", "\"\"")}\""
    File(directory, "$name.csv").writeText((listOf(columns.joinToString(",", transform = ::csv)) +
        rows.map { row -> columns.joinToString(",") { csv(row.opt(it)) } }).joinToString("\n"))
    instrumentation.sendStatus(2, Bundle().apply { putString("stream", "Report: $name.json / $name.csv\n") })
}

/** Run the retained weather implementation and export only reports from this invocation. */
internal fun exportWeatherReport(prefix: String, block: () -> Unit) {
    val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "evaluation")
    val before = directory.list()?.toSet().orEmpty()
    try {
        block()
    } finally {
        directory.listFiles().orEmpty().filter {
            it.name.startsWith("$prefix-") && it.extension == "json" && it.name !in before
        }.forEach { writeDeviceReport(it.nameWithoutExtension, JSONObject(it.readText())) }
    }
}
