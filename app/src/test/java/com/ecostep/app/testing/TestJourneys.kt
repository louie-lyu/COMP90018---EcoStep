package com.ecostep.app.testing

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode

fun testJourney(
    journeyId: String = "j1",
    userId: String = "user-a",
    distanceMeters: Double = 2_000.0,
    transportMode: TransportMode = TransportMode.CYCLING,
    startTimeMillis: Long = 1_757_800_000_000L,
    linkedMissionId: String? = null,
) = JourneySummary(
    journeyId = journeyId,
    userId = userId,
    startLocation = GeoPoint(-37.80, 144.96),
    endLocation = GeoPoint(-37.81, 144.97),
    startTimeMillis = startTimeMillis,
    endTimeMillis = startTimeMillis + 900_000L,
    distanceMeters = distanceMeters,
    transportMode = transportMode,
    linkedMissionId = linkedMissionId,
)
