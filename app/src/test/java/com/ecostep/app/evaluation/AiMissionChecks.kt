package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.MissionRecommendation
import com.ecostep.app.data.model.EcoMission
import com.ecostep.app.data.model.TransportMode

internal fun validateMission(mission: EcoMission, expected: ExpectedRecommendation) {
    check(mission.missionId.isNotBlank()) { "Missing mission ID" }
    check(mission.recommendedMode == expected.mode) { "Unexpected transport mode" }
    check(mission.estimatedCarbonSavingGrams == expected.savingsGrams) { "Verified savings changed" }
    check(mission.explanation.isNotBlank()) { "Missing explanation" }
    check(mission.confidence in 0.0..100.0) { "Invalid confidence" }
    if (!expected.usedAi) {
        val mode = when (expected.mode) {
            TransportMode.WALKING -> "walking"
            TransportMode.CYCLING -> "cycling"
            TransportMode.PUBLIC_TRANSPORT -> "public transport"
            else -> error("Unexpected fallback fixture mode")
        }
        check(mission.explanation == "Try $mode for this journey to save about ${expected.savingsGrams.toInt()} g of CO2.") { "Unexpected fallback explanation" }
        check(mission.confidence == 100.0) { "Unexpected fallback confidence" }
    }
}

internal fun validateRecommendation(result: MissionRecommendation, expected: ExpectedRecommendation) {
    validateMission(result.mission, expected)
    check(result.usedAi == expected.usedAi) { "Unexpected recommendation source" }
    check(result.notificationTitle.isNotBlank() && result.notificationMessage.isNotBlank()) { "Missing notification text" }
    if (!expected.usedAi) {
        check(result.notificationTitle == "Your EcoStep mission" && result.notificationMessage == result.mission.explanation) {
            "Unexpected fallback notification"
        }
    }
}
