package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.DefaultWeeklyCoach
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WeeklyCoachCorrectnessTest(private val name: String, private val verify: () -> Unit) {
    @Test fun matchesExpectedReport() = verify()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = WeeklyCoachEvaluationCases.cases().map { case ->
            val verify: () -> Unit = {
                val coach = DefaultWeeklyCoach()
                val expected = case.expected
                if (expected == null) {
                    assertThrows(IllegalArgumentException::class.java) {
                        coach.generate(case.results, case.startMillis, case.endMillis)
                    }
                } else {
                    val actual = coach.generate(case.results, case.startMillis, case.endMillis)
                    verifyWeeklyReport(actual, expected)
                    check(case.results.none { it.missionId in actual.aiPrompt })
                }
            }
            arrayOf(case.name, verify)
        }
    }
}
