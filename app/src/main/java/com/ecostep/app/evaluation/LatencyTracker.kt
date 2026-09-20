package com.ecostep.app.evaluation

import java.util.concurrent.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.math.ceil

enum class Outcome { SUCCESS, FAILURE, CANCELLED, TIMEOUT }

data class LatencyRecord(
    val scenario: String,
    val durationMs: Double,
    val outcome: Outcome,
    val errorType: String? = null,
)

data class LatencySummary(
    val scenario: String,
    val outcome: Outcome,
    val count: Int,
    val p50Ms: Double,
    val p95Ms: Double,
)

/** One tracker per evaluation run (same device, network and app version). */
class LatencyTracker(
    val context: String = "",
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val lock = Any()
    private val records = mutableListOf<LatencyRecord>()

    /** A separate measurement for each operation, including concurrent operations. */
    fun start(scenario: String): Measurement {
        require(scenario.isNotBlank()) { "Scenario must not be blank" }
        return Measurement(scenario, nanoTime())
    }

    /** Measures real elapsed time, including suspension; preserves results and exceptions. */
    suspend fun <T> measure(scenario: String, block: suspend () -> T): T {
        val measurement = start(scenario)
        try {
            val result = block()
            measurement.finish()
            return result
        } catch (error: Throwable) {
            val outcome = when (error) {
                is TimeoutCancellationException -> Outcome.TIMEOUT
                is CancellationException -> Outcome.CANCELLED
                else -> Outcome.FAILURE
            }
            measurement.finish(outcome, error.javaClass.simpleName)
            throw error
        }
    }

    inner class Measurement internal constructor(
        private val scenario: String,
        private val startedAt: Long,
    ) {
        private var finished = false

        /** Returns false on duplicate completion, so callbacks cannot double-count. */
        fun finish(outcome: Outcome = Outcome.SUCCESS, errorType: String? = null): Boolean {
            val endedAt = nanoTime()
            return synchronized(lock) {
                if (finished) return@synchronized false
                finished = true
                records.add(
                    LatencyRecord(scenario, (endedAt - startedAt) / 1_000_000.0, outcome, errorType),
                )
                true
            }
        }
    }

    fun snapshot(): List<LatencyRecord> = synchronized(lock) { records.toList() }

    /** Clears completed records only. Finish active measurements before clearing a run. */
    fun clear() = synchronized(lock) { records.clear() }

    /** Nearest-rank percentiles; failures are kept separate from successful operations. */
    fun summaries(): List<LatencySummary> = snapshot()
        .groupBy { it.scenario to it.outcome }
        .map { (key, samples) ->
            val times = samples.map { it.durationMs }.sorted()
            fun percentile(fraction: Double) = times[ceil(times.size * fraction).toInt() - 1]
            LatencySummary(key.first, key.second, times.size, percentile(0.50), percentile(0.95))
        }

    /** Returns CSV text; the caller decides where and when to write it (off the UI thread). */
    fun toCsv(): String = buildString {
        append("context,scenario,duration_ms,outcome,error_type\n")
        for (record in snapshot()) {
            append(
                listOf(context, record.scenario, record.durationMs.toString(),
                    record.outcome.name, record.errorType.orEmpty())
                    .joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" },
            )
            append('\n')
        }
    }
}
