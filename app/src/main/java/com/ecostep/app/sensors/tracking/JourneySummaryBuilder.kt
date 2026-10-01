package com.ecostep.app.sensors.tracking

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.TransportMode
import java.util.UUID

class JourneySummaryBuilder {
    fun build(result: RecordingResult, userId: String): JourneySummary = JourneySummary(
        journeyId = UUID.randomUUID().toString(),
        userId = userId,
        startLocation = GeoPoint(result.first.latitude, result.first.longitude),
        endLocation = GeoPoint(result.last.latitude, result.last.longitude),
        startTimeMillis = result.startTimeMillis,
        endTimeMillis = result.endTimeMillis,
        distanceMeters = result.distanceMeters,
        transportMode = TransportMode.UNKNOWN,
        sensorFeatures = result.features,
    )
}
