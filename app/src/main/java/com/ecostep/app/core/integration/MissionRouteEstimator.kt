package com.ecostep.app.core.integration

import com.ecostep.app.algorithm.CarbonCalculator
import com.ecostep.app.algorithm.EcoPointsCalculator
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.MissionModeEstimate
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.ui.adapters.carbonSavedVersusCarGrams

/** Estimated impact of a mission route for each transport mode the editor offers. */
data class MissionRouteEstimate(
    val startText: String,
    val destinationText: String,
    /** Route distance used for each mode. */
    val distanceByMode: Map<TransportMode, Double>,
    val estimates: List<MissionModeEstimate>,
) {
    fun matches(startText: String, destinationText: String): Boolean =
        this.startText == startText.trim() && this.destinationText == destinationText.trim()
}

/** A route the user can fix, e.g. a place name that could not be found. */
class MissionRouteException(message: String) : Exception(message)

/**
 * Recalculates a mission's CO₂ saving and EcoPoints from its start and destination: places
 * are geocoded, real route distances fetched, and the shared calculators applied. These are
 * estimates; the backend still awards points from the recorded journey.
 */
class MissionRouteEstimator(
    private val externalDataRepository: ExternalDataRepository,
    private val locate: suspend (placeName: String, near: GeoPoint) -> GeoPoint?,
    private val carbonCalculator: CarbonCalculator,
    private val ecoPointsCalculator: EcoPointsCalculator,
    /** Where to look for the start place, e.g. the last known device position. */
    private val searchCenter: () -> GeoPoint,
) {
    suspend fun estimate(startText: String, destinationText: String): MissionRouteEstimate {
        val startName = startText.trim()
        val destinationName = destinationText.trim()
        val start = locate(startName, searchCenter())
            ?: throw MissionRouteException("Could not find the starting point. Try a street address.")
        val end = locate(destinationName, start)
            ?: throw MissionRouteException("Could not find the destination. Try a street address.")

        val routes = externalDataRepository.getRouteOptions(start, end)
            .filter { it.distanceMeters.isFinite() && it.distanceMeters >= 0.0 }
        // The routing service has no public-transport profile; transit uses the road distance.
        val road = routes.firstOrNull { it.mode == TransportMode.CAR }
            ?: routes.firstOrNull { it.mode == TransportMode.WALKING }
            ?: routes.firstOrNull()
            ?: throw MissionRouteException("No route was found between these places.")

        val distanceByMode = MISSION_MODES.associateWith { mode ->
            routes.firstOrNull { it.mode == mode && mode != TransportMode.PUBLIC_TRANSPORT }
                ?.distanceMeters
                ?: road.distanceMeters
        }
        val estimates = MISSION_MODES.map { mode ->
            val distance = distanceByMode.getValue(mode)
            val saving = carbonSavedVersusCarGrams(carbonCalculator, distance, mode)
            MissionModeEstimate(
                mode = mode,
                estimatedEcoPoints = ecoPointsCalculator.calculatePoints(
                    MissionResult(
                        missionId = "estimate",
                        accepted = true,
                        completed = true,
                        actualTransportMode = mode,
                        actualCarbonSavingGrams = saving,
                        timestampMillis = 0L,
                        actualDistanceMeters = distance,
                    ),
                ),
                estimatedCarbonSavedGrams = saving,
            )
        }
        return MissionRouteEstimate(startName, destinationName, distanceByMode, estimates)
    }

    private companion object {
        val MISSION_MODES = listOf(
            TransportMode.WALKING,
            TransportMode.CYCLING,
            TransportMode.PUBLIC_TRANSPORT,
            TransportMode.CAR,
        )
    }
}
