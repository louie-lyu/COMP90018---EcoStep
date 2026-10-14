package com.ecostep.app.evaluation

import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.evaluation.checkProxyReply
import com.ecostep.app.network.ai.AiWorkerClient
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device -> authenticated Worker -> Gemini, using six packaged synthetic fixtures. */
@RunWith(AndroidJUnit4::class)
class AiProxyDeviceTest {
    @Test fun evaluateAuthenticatedProxy() {
        val args = InstrumentationRegistry.getArguments()
        val fixtures = JSONArray(InstrumentationRegistry.getInstrumentation().context.assets
            .open("evaluation/ai-proxy-cases.json").bufferedReader().use { it.readText() })
        val selected = (0 until fixtures.length()).map { fixtures.getJSONObject(it) }
            .filter { args.getString("caseName") == null || it.getString("name") == args.getString("caseName") }
        val samples = args.getString("samplesPerCase")?.toInt() ?: 3
        val interval = args.getString("intervalMs")?.toLong() ?: 1000L
        val timeout = args.getString("timeoutMs")?.toLong() ?: 35000L
        require(selected.isNotEmpty() && samples in 1..20 && interval in 0..60000 && timeout in 1000..120000)
        val startedAt = System.currentTimeMillis()
        val output = "ai-proxy-device-$startedAt"
        val records = mutableListOf<JSONObject>()
        val authentication = JSONObject().put("success", false)
        val report = JSONObject().put("schemaVersion", 1).put("startedAtMillis", startedAt)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("apiLevel", Build.VERSION.SDK_INT)
            .put("buildType", BuildConfig.BUILD_TYPE).put("samplesPerCase", samples)
            .put("intervalMs", interval).put("timeoutMs", timeout)
            .put("transport", "android-firebase-ai-proxy").put("proxyUrl", AiWorkerClient.BASE_URL)
            .put("modelVersion", JSONObject.NULL).put("authentication", authentication)
            .put("scope", "Existing Firebase test session; token retrieval measured separately. Synthetic prompts with schema and business validation. Android request preparation through complete body receipt; excludes validation, pacing and reporting. No retries or warm-ups. Does not measure password sign-in, raw Gemini text or semantic quality.")
        fun save() {
            report.put("finishedAtMillis", System.currentTimeMillis()).put("records", JSONArray(records))
                .put("summary", summarize(records, selected.size * samples))
                .put("byTask", JSONArray(selected.map { it.getString("task") }.distinct().map { task ->
                    summarize(records.filter { it.getString("task") == task },
                        samples * selected.count { it.getString("task") == task }).put("task", task)
                }))
            writeDeviceReport(output, report)
        }
        try {
            val authStart = System.nanoTime()
            val token = try {
                val user = checkNotNull(FirebaseAuth.getInstance().currentUser) {
                    "Sign in to a dedicated Firebase test account in EcoStep before this run"
                }
                checkNotNull(Tasks.await(user.getIdToken(false), timeout, TimeUnit.MILLISECONDS).token)
                    .also { authentication.put("success", true) }
            } finally {
                authentication.put("durationMs", (System.nanoTime() - authStart) / 1_000_000.0)
            }
            val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS).callTimeout(timeout, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false).build()
            outer@ for (sample in 1..samples) for (fixture in selected) {
                if (records.isNotEmpty()) SystemClock.sleep(interval)
                val record = request(client, token, fixture).put("sample", sample)
                records += record
                save()
                if (record.optInt("httpStatus") in listOf(400, 401, 403, 404, 429)) {
                    report.put("stopReason", "HTTP_${record.getInt("httpStatus")}")
                    break@outer
                }
            }
        } catch (error: Exception) {
            report.put("runErrorType", error.javaClass.simpleName)
            throw error
        } finally { save() }
        assertTrue("Inspect the device AI proxy report", report.getJSONObject("summary").getBoolean("passed"))
    }

    private fun request(client: OkHttpClient, token: String, fixture: JSONObject): JSONObject {
        val record = JSONObject().put("scenario", fixture.getString("name")).put("task", fixture.getString("task"))
        for (field in listOf("responseBodyReceived", "proxyJsonParsed", "proxyEnvelopeValid", "schemaValid", "businessValid", "accepted")) {
            record.put(field, false)
        }
        val start = System.nanoTime()
        try {
            val body = JSONObject().put("task", fixture.getString("task")).put("prompt", fixture.getString("prompt"))
            val request = Request.Builder().url(AiWorkerClient.BASE_URL + "v1/ai")
                .header("Authorization", "Bearer $token").header("User-Agent", "EcoStep/1.0")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                record.put("httpStatus", response.code)
                val text = response.body.string()
                record.put("responseBodyReceived", text.isNotEmpty())
                record.put("requestDurationMs", (System.nanoTime() - start) / 1_000_000.0)
                val validationStart = System.nanoTime()
                val parsed = try { Json.parseToJsonElement(text).also { record.put("proxyJsonParsed", true) } }
                    catch (_: IllegalArgumentException) {
                        return record.put("errorType", if (response.isSuccessful) "INVALID_PROXY_JSON" else "HTTP_${response.code}")
                    }
                if (!response.isSuccessful) {
                    val code = runCatching { JSONObject(text).optJSONObject("error")?.optString("code") }.getOrNull()
                    record.put("workerErrorCode", code?.takeIf { it in setOf("NOT_FOUND", "METHOD_NOT_ALLOWED", "NOT_CONFIGURED", "UNAUTHENTICATED",
                        "TOO_LARGE", "INVALID_REQUEST", "RATE_LIMITED", "UPSTREAM_AUTH", "MODEL_UNAVAILABLE",
                        "UPSTREAM_ERROR", "INVALID_AI_RESPONSE", "UPSTREAM_TIMEOUT", "UPSTREAM_UNAVAILABLE") } ?: "UNKNOWN_ERROR")
                    return record.put("errorType", "HTTP_${response.code}")
                }
                val modes = fixture.optJSONArray("allowedModes") ?: JSONArray()
                val checks = checkProxyReply(parsed, fixture.getString("task"),
                    (0 until modes.length()).map { modes.getString(it) }.toSet())
                record.put("proxyEnvelopeValid", checks.envelopeValid).put("schemaValid", checks.schemaValid)
                    .put("businessValid", checks.businessValid).put("accepted", checks.businessValid)
                    .put("reply", checks.reply?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
                    .put("validationDurationMs", (System.nanoTime() - validationStart) / 1_000_000.0)
            }
        } catch (error: Exception) {
            record.put("errorType", if (error is InterruptedIOException) "TIMEOUT" else "NETWORK_ERROR")
        } finally {
            if (!record.has("requestDurationMs")) record.put("requestDurationMs", (System.nanoTime() - start) / 1_000_000.0)
        }
        return record
    }

    private fun summarize(records: List<JSONObject>, planned: Int): JSONObject {
        fun count(flag: String) = records.count { it.optBoolean(flag) }
        fun rate(n: Int, d: Int) = JSONObject().put("numerator", n).put("denominator", d)
            .put("value", if (d == 0) JSONObject.NULL else n.toDouble() / d)
        fun latency(rows: List<JSONObject>): JSONObject {
            val values = rows.map { it.getDouble("requestDurationMs") }.sorted()
            fun percentile(p: Double): Any = if (values.isEmpty()) JSONObject.NULL else values[ceil(values.size * p).toInt() - 1]
            return JSONObject().put("count", values.size).put("p50Ms", percentile(.5)).put("p95Ms", percentile(.95))
                .put("meanMs", if (values.isEmpty()) JSONObject.NULL else values.average())
                .put("minMs", values.firstOrNull() ?: JSONObject.NULL).put("maxMs", values.lastOrNull() ?: JSONObject.NULL)
        }
        val http = records.filter { it.optInt("httpStatus") in 200..299 }
        val accepted = records.filter { it.optBoolean("accepted") }
        return JSONObject().put("plannedRequests", planned).put("attemptedRequests", records.size)
            .put("skippedRequests", planned - records.size).put("acceptedResponses", accepted.size)
            .put("httpSuccessfulRequests", http.size).put("responseBodies", count("responseBodyReceived"))
            .put("proxyJsonParsedResponses", count("proxyJsonParsed")).put("proxyEnvelopeValidResponses", count("proxyEnvelopeValid"))
            .put("schemaValidResponses", count("schemaValid")).put("businessValidResponses", count("businessValid"))
            .put("completionRate", rate(records.size, planned)).put("httpSuccessRate", rate(http.size, records.size))
            .put("proxyJsonParseRate", rate(count("proxyJsonParsed"), count("responseBodyReceived")))
            .put("schemaValidRateAmongEnvelopes", rate(count("schemaValid"), count("proxyEnvelopeValid")))
            .put("businessValidRateAmongSchemaValid", rate(count("businessValid"), count("schemaValid")))
            .put("appUsableRate", rate(accepted.size, records.size)).put("geminiRawJsonParseRate", JSONObject.NULL)
            .put("timeoutRequests", records.count { it.optString("errorType") == "TIMEOUT" })
            .put("workerTimeoutResponses", records.count { it.optString("workerErrorCode") == "UPSTREAM_TIMEOUT" })
            .put("workerInvalidAiResponses", records.count { it.optString("workerErrorCode") == "INVALID_AI_RESPONSE" })
            .put("allAttemptLatencyMs", latency(records)).put("httpSuccessLatencyMs", latency(http))
            .put("appUsableLatencyMs", latency(accepted)).put("passed", records.size == planned && accepted.size == planned)
    }
}
