package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.data.model.TransportMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class EcoPointsCorrectnessTest(private val name: String, private val verify: () -> Unit) {
    @Test fun matchesExpectedPoints() = verify()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            val calculator = DefaultEcoPointsCalculator()
            val cases = EcoPointsEvaluationCases.cases()
            for (case in cases) {
                val verify: () -> Unit = {
                    if (case.expectedError != null) {
                        assertThrows(IllegalArgumentException::class.java) { calculator.calculatePoints(case.input) }
                    } else {
                        val input = pointsInput(case)
                        if (case.journey != null) {
                            assertEquals(case.input.actualCarbonSavingGrams!!, input.actualCarbonSavingGrams!!, 1e-9)
                        }
                        assertEquals(case.expectedPoints!!, calculator.calculatePoints(input))
                    }
                }
                add(arrayOf(case.scenario, verify))
            }
            for (mode in TransportMode.entries) {
                val verify: () -> Unit = {
                    var previous = 0
                    cases.filter { it.input.actualTransportMode == mode &&
                        it.group in listOf("reference", "rounding", "zero", "cap") }
                        .sortedBy { it.input.actualCarbonSavingGrams }.forEach {
                            val actual = calculator.calculatePoints(it.input)
                            assertTrue(actual in previous..500)
                            assertEquals(actual, calculator.calculatePoints(it.input))
                            previous = actual
                        }
                }
                add(arrayOf("consistency_$mode", verify))
            }
        }
    }
}
