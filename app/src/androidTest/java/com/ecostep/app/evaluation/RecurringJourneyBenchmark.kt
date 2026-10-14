package com.ecostep.app.evaluation


import androidx.benchmark.junit4.BenchmarkRule
import com.ecostep.app.algorithm.DefaultRecurringJourneyDetector
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecurringJourneyBenchmark(private val size: Int, private val allMatching: Boolean) {
    @get:Rule val benchmark = BenchmarkRule()

    @Test fun detectPattern() {
        val detector = DefaultRecurringJourneyDetector(zoneId = RecurringJourneyEvaluationCases.zone)
        val reference = RecurringJourneyEvaluationCases.journey("benchmark_reference")
        val history = RecurringJourneyEvaluationCases.benchmarkHistory(size, allMatching)
        val expectedCount = 1 + if (allMatching) size else size / 10
        benchmark.measureChecked({ detector.detect(reference, history) }, { pattern ->
            if (expectedCount < 3) assertNull(pattern) else {
                assertNotNull(pattern)
                assertEquals(expectedCount, pattern!!.occurrenceCount)
                assertEquals(480, pattern.typicalDepartureMinuteOfDay)
                assertEquals(30L, pattern.averageDurationMinutes)
                assertEquals(TransportMode.CAR, pattern.usualTransportMode)
            }
        })
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "size={0}, allMatching={1}")
        fun groups(): List<Array<Any>> = listOf(10, 100, 1000, 10000).flatMap { size ->
            listOf(true, false).map { arrayOf<Any>(size, it) }
        }
    }
}
