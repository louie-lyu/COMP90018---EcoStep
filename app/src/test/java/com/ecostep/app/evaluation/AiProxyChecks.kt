package com.ecostep.app.evaluation

import com.ecostep.app.network.ai.AiMissionSuggestion
import com.ecostep.app.network.ai.AiWeeklyAdvice
import kotlinx.serialization.json.*

internal data class ProxyChecks(val envelopeValid: Boolean = false, val schemaValid: Boolean = false,
    val businessValid: Boolean = false, val reply: JsonObject? = null)

/** Proxy response field types, limits and allowed transport-mode checks. */
internal fun checkProxyReply(data: JsonElement, task: String, allowedModes: Set<String>): ProxyChecks {
    val envelope = data as? JsonObject ?: return ProxyChecks()
    val result = envelope["result"] as? JsonObject ?: return ProxyChecks()
    if (envelope["task"] != JsonPrimitive(task)) return ProxyChecks()
    val textFields = if (task == "weekly") listOf("insight", "action") else
        listOf("recommendedMode", "explanation", "notificationTitle", "notificationMessage")
    if (textFields.any { (result[it] as? JsonPrimitive)?.isString != true }) return ProxyChecks(envelopeValid = true)
    if (task == "mission") {
        val confidence = result["confidence"] as? JsonPrimitive
        if (confidence == null || confidence.isString || confidence.doubleOrNull?.isFinite() != true) {
            return ProxyChecks(envelopeValid = true)
        }
    }
    val json = Json { ignoreUnknownKeys = true }
    fun validText(value: String, limit: Int) = value.trim().length in 1..limit
    return try {
        if (task == "weekly") {
            val reply = json.decodeFromJsonElement<AiWeeklyAdvice>(result)
            ProxyChecks(true, true, validText(reply.insight, 1000) && validText(reply.action, 1000),
                json.encodeToJsonElement(reply).jsonObject)
        } else {
            val reply = json.decodeFromJsonElement<AiMissionSuggestion>(result)
            ProxyChecks(true, true, reply.recommendedMode.name in allowedModes &&
                reply.confidence in 0.0..100.0 && validText(reply.explanation, 1000) &&
                validText(reply.notificationTitle, 80) && validText(reply.notificationMessage, 240),
                json.encodeToJsonElement(reply).jsonObject)
        }
    } catch (_: IllegalArgumentException) {
        ProxyChecks(envelopeValid = true)
    }
}
