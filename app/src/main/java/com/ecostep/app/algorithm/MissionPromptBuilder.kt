package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionContext

interface MissionPromptBuilder {

    fun buildPrompt(
        context: MissionContext,
    ): String
}