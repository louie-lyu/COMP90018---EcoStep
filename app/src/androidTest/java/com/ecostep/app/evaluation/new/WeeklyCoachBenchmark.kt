package com.ecostep.app.evaluation.new

import com.ecostep.app.evaluation.*

import androidx.benchmark.junit4.BenchmarkRule
import com.ecostep.app.algorithm.DefaultWeeklyCoach
import com.ecostep.app.data.model.TransportMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WeeklyCoachBenchmark(private val size: Int, private val allInWeek: Boolean) {
    @get:Rule val benchmark = BenchmarkRule()

    @Test fun generateWeeklyReport() {
        val history = List(size) { index -> WeeklyCoachEvaluationCases.mission("benchmark_$index",
            saving = 1.0, timestamp = if (allInWeek || index % 10 == 0) 1500L else 999L) }
        val count = if (allInWeek) size else size / 10
        val expected = ExpectedWeeklyReport(count, count, if (count == 0) 0.0 else 100.0,
            count.toDouble(), if (count == 0) null else TransportMode.WALKING,
            if (count == 0) WeeklyCoachEvaluationCases.EMPTY else WeeklyCoachEvaluationCases.HIGH)
        val coach = DefaultWeeklyCoach()
        benchmark.measureChecked({ coach.generate(history, 1000L, 2000L) }, { verifyWeeklyReport(it, expected) })
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "size={0}, allInWeek={1}")
        fun groups(): List<Array<Any>> = listOf(0, 10, 100, 1000, 10000).map { arrayOf<Any>(it, true) } +
            listOf(arrayOf<Any>(10000, false))
    }
}
