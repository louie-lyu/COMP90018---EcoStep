package com.ecostep.app.algorithm

import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.TransportMode

class DefaultMissionValidator : MissionValidator {

    override fun isValid(
        mission: EcoMission,
    ): Boolean {
        if (mission.missionId.isBlank()) {
            return false
        }

        if (mission.recommendedMode == TransportMode.UNKNOWN) {
            return false
        }

        if (
            !mission.estimatedCarbonSavingGrams.isFinite() ||
            mission.estimatedCarbonSavingGrams <= 0.0
        ) {
            return false
        }

        if (mission.explanation.isBlank()) {
            return false
        }

        if (
            !mission.confidence.isFinite() ||
            mission.confidence !in 0.0..100.0
        ) {
            return false
        }

        return true
    }
}