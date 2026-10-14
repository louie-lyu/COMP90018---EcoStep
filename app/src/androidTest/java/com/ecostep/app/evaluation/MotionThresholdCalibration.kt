package com.ecostep.app.evaluation

import android.os.Bundle
import com.ecostep.app.algorithm.DefaultTransportClassifier
import com.ecostep.app.algorithm.MotionThresholds
import com.ecostep.app.algorithm.TransportEvidence
import com.ecostep.app.algorithm.TransportEvidenceProvider
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import org.json.JSONArray
import org.json.JSONObject

/** Evaluation only: mask Activity hints and compare all candidates on the same evidence. */
internal class MotionThresholdCalibration(thresholds: List<MotionThresholds>) {
    // Stable accuracy ties: prefer disabled, then lower std, then higher minimum sample count.
    private val candidates: List<MotionThresholds?> = listOf(null) + thresholds.distinct()
        .sortedWith(compareBy<MotionThresholds> { it.steadyAccelStd }.thenByDescending { it.minAccelSamples })

    init {
        require(thresholds.isNotEmpty()) { "Supply at least one motion threshold candidate." }
    }

    fun pendingPredictions() = JSONArray(candidates.map {
        JSONObject().put("predictedMode", "UNKNOWN").put("correct", false)
    })

    suspend fun classify(journey: JourneySummary, evidence: TransportEvidence, trueMode: TransportMode): JSONArray {
        val sensorEvidence = evidence.copy(activity = null)
        val provider = object : TransportEvidenceProvider {
            override suspend fun getEvidence(journey: JourneySummary) = sensorEvidence
        }
        val predictions = JSONArray()
        for (threshold in candidates) {
            val result = DefaultTransportClassifier(provider, threshold).classify(journey)
            predictions.put(JSONObject().put("predictedMode", result.mode.name).put("correct", result.mode == trueMode))
        }
        return predictions
    }

    fun report(records: List<JSONObject>, completed: Boolean): JSONObject {
        val results = candidates.mapIndexed { index, thresholds ->
            val predictions = records.map { record ->
                val prediction = record.getJSONArray("sensorCalibrationPredictions").getJSONObject(index)
                JSONObject(record.toString())
                    .put("predictedMode", prediction.getString("predictedMode"))
                    .put("correct", prediction.getBoolean("correct"))
            }
            JSONObject()
                .put("id", if (index == 0) "speed_only" else "motion_$index")
                .put("thresholds", thresholds?.let {
                    JSONObject().put("steadyAccelStd", it.steadyAccelStd).put("minAccelSamples", it.minAccelSamples)
                } ?: JSONObject.NULL)
                .put("summary", TransportEvaluationMetrics.summary(predictions))
                .put("perMode", JSONArray(ShlEvaluationDataset.modes.map { TransportEvaluationMetrics.perMode(predictions, it) }))
                .put("confusionMatrix", TransportEvaluationMetrics.confusionMatrix(predictions))
                .put("motionEligibleSamples", if (thresholds == null) 0 else records.count {
                    val features = it.optJSONObject("sensorFeatures")
                    features != null && features.optInt("accelSampleCount") >= thresholds.minAccelSamples
                })
        }
        val usable = completed && records.isNotEmpty() && records.all { it.getBoolean("runtimeSuccess") } &&
            records.any { it.optBoolean("sensorCalibrationCompleted") }
        // Every candidate has the same denominator, including network timeouts as UNKNOWN.
        val best = if (usable) results.maxByOrNull { it.getJSONObject("summary").getInt("correctSamples") } else null
        return JSONObject()
            .put("activityHintsMasked", true)
            .put("selectionMetric", "overall accuracy on all evaluated samples; no train/validation split")
            .put("tieBreak", "Prefer speed_only, then lower steadyAccelStd, then higher minAccelSamples")
            .put("timeoutPolicy", "Same production timeout for all candidates; timed-out samples remain UNKNOWN")
            .put("baselineId", "speed_only")
            .put("candidates", JSONArray(results))
            .put("selectionAvailable", usable)
            .put("bestCandidateId", best?.getString("id") ?: JSONObject.NULL)
            .put("bestThresholds", best?.get("thresholds") ?: JSONObject.NULL)
            .put("bestAccuracy", best?.getJSONObject("summary")?.get("accuracy") ?: JSONObject.NULL)
            .put("tiedBestCandidateIds", JSONArray(if (best == null) emptyList<String>() else results.filter {
                it.getJSONObject("summary").getInt("correctSamples") == best.getJSONObject("summary").getInt("correctSamples")
            }.map { it.getString("id") }))
    }

    companion object {
        fun from(arguments: Bundle): MotionThresholdCalibration? {
            if (arguments.getString("calibrateMotion") != "true") return null
            val stds = (arguments.getString("motionStdCandidates") ?: "0.2,0.4,0.6,0.8,1.0,1.5,2.0,3.0")
                .split(',').map { it.trim().toDouble() }.distinct()
            val counts = (arguments.getString("motionSampleCandidates") ?: "250,500,1000")
                .split(',').map { it.trim().toInt() }.distinct()
            require(stds.size * counts.size <= 200) { "At most 200 threshold combinations are supported." }
            return MotionThresholdCalibration(stds.flatMap { std -> counts.map { MotionThresholds(std, it) } })
        }
    }
}
