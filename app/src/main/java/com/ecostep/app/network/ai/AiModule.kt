package com.ecostep.app.network.ai

import com.ecostep.app.algorithm.DefaultAiMissionGenerator
import com.ecostep.app.algorithm.DefaultAiWeeklyCoach
import okhttp3.OkHttpClient

class AiModule(
    private val okHttpClient: OkHttpClient,
) {
    val gateway: AiGateway by lazy {
        AiWorkerClient(okHttpClient)
    }

    val missionGenerator: DefaultAiMissionGenerator by lazy {
        DefaultAiMissionGenerator(aiGateway = gateway)
    }

    val weeklyCoach: DefaultAiWeeklyCoach by lazy {
        DefaultAiWeeklyCoach(aiGateway = gateway)
    }
}