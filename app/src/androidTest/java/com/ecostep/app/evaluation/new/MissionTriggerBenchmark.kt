package com.ecostep.app.evaluation.new

import com.ecostep.app.evaluation.*

import androidx.benchmark.junit4.BenchmarkRule
import com.ecostep.app.algorithm.DefaultMissionTriggerPlanner
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MissionTriggerBenchmark(private val name: String) {
    @get:Rule val benchmark = BenchmarkRule()

    @Test fun planNextTrigger() {
        val case = MissionTriggerEvaluationCases.cases().single { it.name == name }
        val planner = DefaultMissionTriggerPlanner(case.leadMinutes, ZoneId.of(case.zone))
        val pattern = case.pattern
        val now = case.currentTimeMillis
        benchmark.measureChecked({ planner.planNextTrigger(pattern, now) }, { verifyTrigger(it, case) })
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun groups() = listOf("five_minutes_before_departure", "one_millisecond_after_departure",
            "all_weekdays", "spring_gap_shifted_forward", "autumn_overlap_earlier_offset").map { arrayOf(it) }
    }
}
