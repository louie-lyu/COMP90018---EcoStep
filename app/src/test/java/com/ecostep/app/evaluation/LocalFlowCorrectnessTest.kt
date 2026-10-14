package com.ecostep.app.evaluation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class LocalFlowCorrectnessTest(private val name: String, private val verify: suspend () -> Unit) {
    @Test fun matchesExpectedFlow() = runTest { verify() }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            for (case in LocalFlowEvaluationCases.cases()) {
                val verify: suspend () -> Unit = {
                    val flow = LocalFlowHarness()
                    var stage = ""
                    if (case.expected.errorStage == null) {
                        flow.verifySingle(flow.runFlow(listOf(case)), case.expected)
                    } else {
                        try {
                            flow.runFlow(listOf(case)) { stage = it }
                            fail("Expected rejection at ${case.expected.errorStage}")
                        } catch (error: IllegalArgumentException) {
                            assertEquals(case.expected.errorStage, stage)
                        }
                    }
                }
                add(arrayOf(case.name, verify))
            }
            val verify: suspend () -> Unit = {
                val harness = LocalFlowHarness()
                val cases = LocalFlowEvaluationCases.mixedWeek()
                harness.verifyMixed(harness.runFlow(cases), cases)
            }
            add(arrayOf("mixed_week_totals", verify))
        }
    }
}
