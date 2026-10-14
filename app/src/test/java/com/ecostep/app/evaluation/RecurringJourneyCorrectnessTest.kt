package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.DefaultRecurringJourneyDetector
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RecurringJourneyCorrectnessTest(private val name: String, private val verify: () -> Unit) {
    @Test fun matchesExpectedPattern() = verify()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = RecurringJourneyEvaluationCases.correctnessCases().map { case ->
            val verify: () -> Unit = {
                val detector = DefaultRecurringJourneyDetector(zoneId = RecurringJourneyEvaluationCases.zone)
                assertEquals(case.expected, detector.detect(case.reference, case.history))
            }
            arrayOf(case.name, verify)
        }
    }
}
