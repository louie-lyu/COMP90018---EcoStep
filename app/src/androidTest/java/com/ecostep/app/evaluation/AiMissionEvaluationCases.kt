package com.ecostep.app.evaluation

import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.CarbonResult
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.TransportResult
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.network.ai.AiMissionSuggestion
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

internal data class ExpectedRecommendation(
    val mode: TransportMode = TransportMode.WALKING,
    val savingsGrams: Double = 500.0,
    val usedAi: Boolean = false,
    val calls: Int = 2,
    val error: String? = null,
)

internal data class AiMissionCase(
    val name: String,
    val expected: ExpectedRecommendation = ExpectedRecommendation(),
    val context: MissionContext = AiMissionEvaluationCases.context(),
    val reply: (Int) -> AiMissionSuggestion = { AiMissionEvaluationCases.suggestion(TransportMode.UNKNOWN) },
)

/** Fixed synthetic inputs and explicit expected recommendations; no live AI service. */
internal object AiMissionEvaluationCases {
    fun cases(): List<AiMissionCase> = buildList {
        val valid = suggestion()
        val aiCycling = ExpectedRecommendation(TransportMode.CYCLING, 350.25, true, 1)
        add(AiMissionCase("valid_cycling", aiCycling, reply = { valid }))
        add(AiMissionCase("valid_walking", ExpectedRecommendation(usedAi = true, calls = 1),
            reply = { suggestion(TransportMode.WALKING) }))
        add(AiMissionCase("valid_public_transport", ExpectedRecommendation(TransportMode.PUBLIC_TRANSPORT, 250.0, true, 1),
            context(publicTransport = true), reply = { suggestion(TransportMode.PUBLIC_TRANSPORT) }))
        add(AiMissionCase("trim_text", aiCycling, reply = {
            valid.copy(explanation = "  Try cycling.  ", notificationTitle = "  EcoStep  ", notificationMessage = "  Cycle today.  ")
        }))
        for (confidence in listOf(0.0, 100.0)) {
            add(AiMissionCase("confidence_$confidence", aiCycling, reply = { valid.copy(confidence = confidence) }))
        }
        val texts = listOf(
            "explanation" to { text: String -> valid.copy(explanation = text) },
            "title" to { text: String -> valid.copy(notificationTitle = text) },
            "message" to { text: String -> valid.copy(notificationMessage = text) },
        )
        for ((field, response) in texts) {
            val limit = when (field) { "explanation" -> 1000; "title" -> 80; else -> 240 }
            add(AiMissionCase("${field}_at_limit", aiCycling, reply = { response("x".repeat(limit)) }))
            add(AiMissionCase("${field}_over_limit", reply = { response("x".repeat(limit + 1)) }))
            add(AiMissionCase("${field}_blank", reply = { response(" ") }))
        }
        for (confidence in listOf(-1.0, 101.0)) {
            add(AiMissionCase("invalid_confidence_$confidence", reply = { valid.copy(confidence = confidence) }))
        }
        add(AiMissionCase("unlisted_car", reply = { suggestion(TransportMode.CAR) }))
        add(AiMissionCase("unknown_mode"))
        add(AiMissionCase("invalid_then_valid", aiCycling.copy(calls = 2), reply = { attempt ->
            if (attempt == 1) suggestion(TransportMode.CAR) else valid
        }))
        add(AiMissionCase("network_failure_then_valid", aiCycling.copy(calls = 2), reply = { attempt ->
            if (attempt == 1) throw IOException("Controlled failure") else valid
        }))
        add(AiMissionCase("network_failure", reply = { throw IOException("Controlled failure") }))
        add(AiMissionCase("socket_timeout", reply = { throw SocketTimeoutException("Controlled timeout") }))
        add(AiMissionCase("missing_response_fields", reply = {
            Json.decodeFromString<AiMissionSuggestion>("""{"recommendedMode":"CYCLING"}""")
        }))
        for (status in listOf(401, 403, 429, 503)) {
            add(AiMissionCase("http_$status", ExpectedRecommendation(calls = if (status == 503) 2 else 1), reply = {
                throw HttpException(Response.error<Any>(status, "Controlled failure".toResponseBody()))
            }))
        }
        add(AiMissionCase("cancelled", ExpectedRecommendation(calls = 1, error = "CancellationException"),
            reply = { throw CancellationException("Controlled cancellation") }))
        val base = context()
        add(AiMissionCase("empty_journey_id", ExpectedRecommendation(calls = 0, error = "IllegalArgumentException"),
            base.copy(journey = base.journey.copy(journeyId = " "))))
        add(AiMissionCase("no_alternatives", ExpectedRecommendation(calls = 0, error = "IllegalArgumentException"),
            base.copy(carbonResult = base.carbonResult.copy(lowerCarbonAlternatives = emptyList()))))
        val ptOnly = base.copy(carbonResult = CarbonResult(800.0, listOf(CarbonAlternative(TransportMode.PUBLIC_TRANSPORT, 250.0))))
        add(AiMissionCase("public_transport_without_service", ExpectedRecommendation(calls = 0, error = "IllegalArgumentException"), ptOnly))
        val mixed = base.copy(carbonResult = CarbonResult(1000.0, listOf(
            CarbonAlternative(TransportMode.PUBLIC_TRANSPORT, 900.0),
            CarbonAlternative(TransportMode.CYCLING, 350.25),
        )))
        add(AiMissionCase("public_transport_filtered_from_fallback", ExpectedRecommendation(TransportMode.CYCLING, 350.25),
            mixed, reply = { suggestion(TransportMode.PUBLIC_TRANSPORT) }))
        add(AiMissionCase("fallback_greatest_saving", ExpectedRecommendation(TransportMode.CYCLING, 600.0),
            base.copy(carbonResult = CarbonResult(800.0, listOf(
                CarbonAlternative(TransportMode.WALKING, 500.0), CarbonAlternative(TransportMode.CYCLING, 600.0),
            )))))
    }

    fun suggestion(mode: TransportMode = TransportMode.CYCLING) = AiMissionSuggestion(
        recommendedMode = mode, explanation = "Choose this available low-carbon option.", confidence = 80.0,
        notificationTitle = "Your low-carbon journey", notificationMessage = "Try an available alternative today.",
    )

    fun context(publicTransport: Boolean = false): MissionContext {
        val journey = JourneySummary(
            journeyId = "private_evaluation_journey", userId = "private_evaluation_user",
            startLocation = GeoPoint(11.123456, 77.987654), endLocation = GeoPoint(11.234567, 77.876543),
            startTimeMillis = 0L, endTimeMillis = 900_000L, distanceMeters = 5000.0, transportMode = TransportMode.CAR,
        )
        return MissionContext(
            journey = journey, transportResult = TransportResult(TransportMode.CAR, 90.0, emptyList()),
            carbonResult = CarbonResult(800.0, listOf(CarbonAlternative(TransportMode.WALKING, 500.0),
                CarbonAlternative(TransportMode.CYCLING, 350.25), CarbonAlternative(TransportMode.PUBLIC_TRANSPORT, 250.0))),
            route = RouteInfo(TransportMode.CAR, 5000.0, 900L), weather = WeatherData(20.0, "Clear"),
            publicTransportOptions = if (publicTransport) listOf(PublicTransportInfo("Route 1", 0L, 1200L)) else emptyList(),
            recentJourneyHistory = listOf(journey.copy(journeyId = "private_history_journey")),
        )
    }
}
