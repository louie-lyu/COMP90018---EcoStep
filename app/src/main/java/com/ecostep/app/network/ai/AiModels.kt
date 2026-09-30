package com.ecostep.app.network.ai

import com.ecostep.app.data.model.TransportMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Data sent from the app to the Worker.
@Serializable
data class AiWorkerRequest(
    val task: String,
    val prompt: String,
)

// Common response wrapper returned by the Worker.
@Serializable
data class AiWorkerResponse(
    val task: String,
    val result: JsonObject,
)

// AI chooses a mode and writes text.
// Carbon savings will come from the app's verified calculations.
@Serializable
data class AiMissionSuggestion(
    val recommendedMode: TransportMode,
    val explanation: String,
    val confidence: Double,
    val notificationTitle: String,
    val notificationMessage: String,
)

@Serializable
data class AiWeeklyAdvice(
    val insight: String,
    val action: String,
)

// Algorithms use this interface, so tests can provide a fake AI service.
interface AiGateway {
    suspend fun generateMission(prompt: String): AiMissionSuggestion

    suspend fun generateWeeklyAdvice(prompt: String): AiWeeklyAdvice
}