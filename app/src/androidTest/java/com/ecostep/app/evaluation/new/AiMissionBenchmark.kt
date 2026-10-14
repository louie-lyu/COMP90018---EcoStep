package com.ecostep.app.evaluation.new

import com.ecostep.app.evaluation.*

import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ecostep.app.algorithm.DefaultMissionValidator
import com.ecostep.app.algorithm.FallbackMissionGenerator
import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiMissionBenchmark {
    @get:Rule val benchmark = BenchmarkRule()

    @Test fun validator() {
        val validator = DefaultMissionValidator()
        val mission = EcoMission("evaluation", TransportMode.WALKING, 500.0, "Try walking.", 80.0)
        benchmark.measureChecked({ validator.isValid(mission) }, { check(it) })
    }

    @Test fun fallback() = runBlocking {
        val generator = FallbackMissionGenerator()
        val context = AiMissionEvaluationCases.context()
        benchmark.measureSuspendingChecked({ generator.generateMission(context) },
            { validateMission(it, ExpectedRecommendation()) })
    }
}
