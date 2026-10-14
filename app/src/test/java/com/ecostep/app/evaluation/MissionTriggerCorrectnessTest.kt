package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.DefaultMissionTriggerPlanner
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MissionTriggerCorrectnessTest(private val name: String, private val verify: () -> Unit) {
    @Test fun matchesExpectedTime() = verify()

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = MissionTriggerEvaluationCases.cases().map { case ->
            val verify: () -> Unit = {
                if (case.rejectsLead) {
                    assertThrows(IllegalArgumentException::class.java) {
                        DefaultMissionTriggerPlanner(case.leadMinutes, ZoneId.of(case.zone))
                    }
                } else {
                    val planner = DefaultMissionTriggerPlanner(case.leadMinutes, ZoneId.of(case.zone))
                    verifyTrigger(planner.planNextTrigger(case.pattern, case.currentTimeMillis), case)
                }
            }
            arrayOf(case.name, verify)
        }
    }
}
