package com.ecostep.app.evaluation


import androidx.benchmark.junit4.BenchmarkRule
import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EcoPointsBenchmark(private val group: String) {
    @get:Rule val benchmark = BenchmarkRule()

    @Test fun calculatePoints() {
        val calculator = DefaultEcoPointsCalculator()
        val cases = EcoPointsEvaluationCases.cases().filter {
            when (group) {
                "early_return" -> it.group in listOf("state", "missing", "zero") && it.expectedPoints == 0
                "cap", "carbon_to_points" -> it.group == group
                else -> it.input.actualTransportMode?.name == group.removePrefix("normal_") &&
                    it.group in listOf("reference", "rounding")
            }
        }
        // Same 1,000-call mix as the existing benchmark; one measurement is one batch.
        val results = IntArray(1000)
        val expected = IntArray(1000) { cases[it % cases.size].expectedPoints!! }
        benchmark.measureChecked(operation = {
            for (i in results.indices) results[i] = calculator.calculatePoints(pointsInput(cases[i % cases.size]))
            results
        }, verify = { assertArrayEquals(expected, it) })
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun groups() = (TransportMode.entries.map { "normal_$it" } +
            listOf("early_return", "cap", "carbon_to_points")).map { arrayOf(it) }
    }
}
