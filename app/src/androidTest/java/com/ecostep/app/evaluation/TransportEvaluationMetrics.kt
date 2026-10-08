package com.ecostep.app.evaluation

import com.ecostep.app.data.model.TransportMode
import kotlin.math.ceil
import org.json.JSONArray
import org.json.JSONObject

/** Accuracy uses every selected sample; unavailable recordings remain UNKNOWN. */
internal object TransportEvaluationMetrics {
    private val modes = ShlEvaluationDataset.modes

    fun summary(records: List<JSONObject>): JSONObject {
        val successful = records.filter { it.getBoolean("runtimeSuccess") }
        val classified = successful.count { it.getString("predictedMode") != "UNKNOWN" }
        val correct = records.count { it.getBoolean("correct") }
        val times = successful.filter { !it.isNull("durationMs") }.map { it.getDouble("durationMs") }.sorted()
        fun percentile(fraction: Double): Any = if (times.isEmpty()) JSONObject.NULL else times[ceil(times.size * fraction).toInt() - 1]
        val f1 = modes.filter { mode -> records.any { it.getString("trueMode") == mode.name } }.map { perMode(records, it).getDouble("f1") }
        return JSONObject().put("totalSamples", records.size).put("correctSamples", correct)
            .put("incorrectSamples", records.size - correct).put("accuracy", ratio(correct, records.size))
            .put("unknownSamples", records.count { it.getString("predictedMode") == "UNKNOWN" }).put("unknownRate", ratio(records.count { it.getString("predictedMode") == "UNKNOWN" }, records.size))
            .put("classifiedSamples", classified).put("coverage", ratio(classified, records.size))
            .put("accuracyAmongClassified", ratio(correct, classified)).put("runtimeErrors", records.size - successful.size)
            .put("recordingFailures", records.count { !it.getBoolean("recordingAvailable") })
            .put("latencySamples", times.size).put("p50Ms", percentile(0.50)).put("p95Ms", percentile(0.95))
            .put("macroF1", if (f1.isEmpty()) JSONObject.NULL else f1.average())
    }

    fun perMode(records: List<JSONObject>, mode: TransportMode): JSONObject {
        val support = records.filter { it.getString("trueMode") == mode.name }
        val tp = support.count { it.getBoolean("correct") }
        val fp = records.count { it.optString("predictedMode") == mode.name && it.getString("trueMode") != mode.name }
        val unknown = support.count { it.optString("predictedMode") == "UNKNOWN" }
        return JSONObject().put("mode", mode.name).put("totalSamples", support.size).put("correctSamples", tp)
            .put("accuracy", ratio(tp, support.size)).put("precision", ratio(tp, tp + fp)).put("recall", ratio(tp, support.size))
            .put("f1", ratio(2 * tp, support.size + tp + fp)).put("unknownRate", ratio(unknown, support.size))
    }

    fun confusionMatrix(records: List<JSONObject>) = JSONObject().apply {
        for (mode in modes) put(mode.name, JSONObject().apply {
            for (prediction in modes.map { it.name } + listOf("UNKNOWN", "ERROR")) put(prediction, records.count {
                it.getString("trueMode") == mode.name && (if (it.getBoolean("runtimeSuccess")) it.getString("predictedMode") else "ERROR") == prediction
            })
        })
    }

    fun byPrediction(records: List<JSONObject>) = JSONArray(
        (modes.map { it.name } + "UNKNOWN").map { prediction ->
            val selected = records.filter { it.getString("predictedMode") == prediction }
            val incorrect = selected.count { !it.getBoolean("correct") }
            JSONObject()
                .put("mode", prediction)
                .put("count", selected.size)
                .put("share", ratio(selected.size, records.size))
                .put("incorrect", incorrect)
                .put("errorRate", ratio(incorrect, selected.size))
        },
    )

    private fun ratio(numerator: Int, denominator: Int): Any =
        if (denominator == 0) JSONObject.NULL else numerator.toDouble() / denominator
}
