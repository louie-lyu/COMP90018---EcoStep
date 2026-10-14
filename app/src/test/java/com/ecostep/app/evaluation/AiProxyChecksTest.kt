package com.ecostep.app.evaluation

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AiProxyChecksTest {
    private val mission = Json.parseToJsonElement("""{"recommendedMode":"WALKING","explanation":"Walk.","confidence":80,"notificationTitle":"Try walking","notificationMessage":"Walk today."}""").jsonObject
    private fun check(fields: JsonObject = mission, task: String = "mission", envelopeTask: String = task) =
        checkProxyReply(buildJsonObject { put("task", envelopeTask); put("result", fields) }, task, setOf("WALKING"))

    @Test fun validReplyIgnoresAndDoesNotExportUnknownFields() {
        val result = check(JsonObject(mission + ("privateExtra" to JsonPrimitive("omit"))))
        assertTrue(result.businessValid)
        assertFalse(result.reply!!.containsKey("privateExtra"))
    }
    @Test fun wrongTaskIsNotAValidEnvelope() { assertFalse(check(envelopeTask = "weekly").envelopeValid) }
    @Test fun missingAndInvalidFieldsFailSchema() {
        assertFalse(check(JsonObject(mission - "confidence")).schemaValid)
        assertFalse(check(JsonObject(mission + ("recommendedMode" to JsonPrimitive("PLANE")))).schemaValid)
    }
    @Test fun quotedNumbersAndNonStringTextFailSchema() {
        assertFalse(check(JsonObject(mission + ("confidence" to JsonPrimitive("80")))).schemaValid)
        assertFalse(check(JsonObject(mission + ("explanation" to JsonPrimitive(42)))).schemaValid)
    }
    @Test fun disallowedModeAndOutOfRangeConfidenceFailBusinessValidation() {
        for ((field, value) in listOf("recommendedMode" to JsonPrimitive("CAR"), "confidence" to JsonPrimitive(101))) {
            val result = check(JsonObject(mission + (field to value)))
            assertTrue(result.schemaValid)
            assertFalse(result.businessValid)
        }
    }
    @Test fun blankAndOverlongTextFailBusinessValidation() {
        for ((field, value) in listOf("explanation" to " ", "notificationTitle" to "x".repeat(81), "notificationMessage" to "x".repeat(241))) {
            assertFalse(check(JsonObject(mission + (field to JsonPrimitive(value)))).businessValid)
        }
    }
    @Test fun weeklyReplyRequiresBothNonblankFields() {
        val weekly = buildJsonObject { put("insight", "Good progress."); put("action", "Walk tomorrow.") }
        assertTrue(check(weekly, "weekly").businessValid)
        assertFalse(check(JsonObject(weekly + ("action" to JsonPrimitive(""))), "weekly").businessValid)
    }
}
