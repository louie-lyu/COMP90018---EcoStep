package com.ecostep.app.network.publictransport

import com.ecostep.app.algorithm.TransportEvidence
import com.ecostep.app.algorithm.TransportEvidenceProvider
import com.ecostep.app.algorithm.TransportTrackPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.sensors.tracking.LocationSample
import com.ecostep.app.sensors.tracking.RecordingResult
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class DefaultTransportEvidenceProvider(
    private val recording: RecordingResult,
    private val transitousApi: TransitousApi,
) : TransportEvidenceProvider {

    override suspend fun getEvidence(journey: JourneySummary): TransportEvidence {
        val routes = try {
            transitousApi.planJourney(
                fromPlace = "${journey.startLocation.latitude},${journey.startLocation.longitude}",
                toPlace = "${journey.endLocation.latitude},${journey.endLocation.longitude}",
                time = Instant.ofEpochMilli(journey.startTimeMillis).toString(),
            ).toTransitStopRoutes()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Offline or unavailable timetable: do not claim a public-transport match.
            emptyList()
        }

        val track = recording.trace.mapIndexed { index, sample ->
            val reportedSpeed = sample.speedMps?.toDouble()
                ?.takeIf { it.isFinite() && it >= 0.0 }
            val speed = reportedSpeed
                ?: if (index == 0) Double.POSITIVE_INFINITY
                   else derivedSpeed(recording.trace[index - 1], sample)

            TransportTrackPoint(
                latitude = sample.latitude,
                longitude = sample.longitude,
                timeMillis = sample.timeMillis,
                speedMps = speed,
                accuracyMeters = sample.accuracyMeters.toDouble(),
            )
        }

        return TransportEvidence(
            activity = recording.activityHint,
            track = track,
            transitRoutes = routes,
        )
    }

    private fun derivedSpeed(previous: LocationSample, current: LocationSample): Double {
        val seconds = (current.timeMillis - previous.timeMillis) / 1000.0
        if (seconds <= 0.0) return Double.POSITIVE_INFINITY

        val deltaLat = Math.toRadians(current.latitude - previous.latitude)
        val deltaLon = Math.toRadians(current.longitude - previous.longitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(Math.toRadians(previous.latitude)) *
            cos(Math.toRadians(current.latitude)) *
            sin(deltaLon / 2) * sin(deltaLon / 2)
        val meters = 12_742_000.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return meters / seconds
    }
}
