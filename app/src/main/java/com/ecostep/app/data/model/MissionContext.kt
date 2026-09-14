package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

/**
 * Assembled by the Algorithm & AI module (Duo) from Journey + Transport + Carbon + Weather +
 * Route + Public Transport data, then used to build the AI prompt that produces an [EcoMission].
 */
@Serializable
data class MissionContext(
    val journey: JourneySummary,
    val transportResult: TransportResult,
    val carbonResult: CarbonResult,
    val route: RouteInfo,
    val weather: WeatherData,
    val publicTransportOptions: List<PublicTransportInfo>,
    val recentJourneyHistory: List<JourneySummary>,
)
