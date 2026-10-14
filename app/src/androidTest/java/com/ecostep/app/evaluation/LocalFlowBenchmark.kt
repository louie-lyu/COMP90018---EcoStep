package com.ecostep.app.evaluation


import androidx.benchmark.junit4.BenchmarkRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LocalFlowBenchmark(private val name: String) {
    @get:Rule val benchmark = BenchmarkRule()

    @Test fun journeyToWeeklyReport() = runBlocking {
        val harness = LocalFlowHarness()
        val mixed = name == "mixed_week_totals"
        val cases = if (mixed) LocalFlowEvaluationCases.mixedWeek()
            else listOf(LocalFlowEvaluationCases.cases().single { it.name == name })
        benchmark.measureSuspendingChecked({ harness.runFlow(cases) }, {
            if (mixed) harness.verifyMixed(it, cases) else harness.verifySingle(it, cases.single().expected)
        })
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun groups() = (LocalFlowEvaluationCases.cases().take(3).map { it.name } + "mixed_week_totals")
            .map { arrayOf(it) }
    }
}
