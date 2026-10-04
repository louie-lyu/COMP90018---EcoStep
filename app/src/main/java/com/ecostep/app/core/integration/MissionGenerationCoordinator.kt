package com.ecostep.app.core.integration

import com.ecostep.app.algorithm.CarbonCalculator
import com.ecostep.app.algorithm.DefaultAiMissionGenerator
import com.ecostep.app.algorithm.EcoPointsCalculator
import com.ecostep.app.algorithm.FallbackMissionGenerator
import com.ecostep.app.algorithm.MissionGenerator
import com.ecostep.app.algorithm.MissionRecommendation
import com.ecostep.app.algorithm.RecurringJourneyDetector
import com.ecostep.app.data.model.CarbonAlternative
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.MissionContext
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.model.TransportResult
import com.ecostep.app.data.model.UserPreferences
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.data.repository.MissionRepository
import com.ecostep.app.data.repository.ProfileRepository
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

sealed interface MissionGenerationOutcome {
    data class Created(val missionId: String, val usedAi: Boolean) : MissionGenerationOutcome
    data object NotSignedIn : MissionGenerationOutcome
    data object Disabled : MissionGenerationOutcome
    data object LinkedToMission : MissionGenerationOutcome
    data object AlreadyHandled : MissionGenerationOutcome
    data object SuggestionPending : MissionGenerationOutcome
    data object NotRecurring : MissionGenerationOutcome
    data object NoAlternative : MissionGenerationOutcome
    data class Failed(val reason: String) : MissionGenerationOutcome
}

/**
 * Confirmed journey -> recurring pattern -> carbon alternatives -> external context ->
 * AI (or non-AI fallback) -> suggested Mission in Firestore.
 *
 * Orchestration only: every rule lives in the detector, calculators and generator. Never
 * throws (except cancellation), so a failure here cannot affect the journey confirmation.
 * Only aggregated journey data reaches the AI; raw GPS traces are never read.
 */
class MissionGenerationCoordinator(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val journeyRepository: JourneyRepository,
    private val missionRepository: MissionRepository,
    private val externalDataRepository: ExternalDataRepository,
    private val recurringJourneyDetector: RecurringJourneyDetector,
    private val carbonCalculator: CarbonCalculator,
    private val ecoPointsCalculator: EcoPointsCalculator,
    private val missionGenerator: DefaultAiMissionGenerator,
    /** Used when the weather needed by the AI prompt is unavailable. */
    private val fallbackGenerator: MissionGenerator = FallbackMissionGenerator(),
    /** Display name for a point; coordinates are used when it returns null. */
    private val placeName: suspend (GeoPoint) -> String? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    /** Short, type-only diagnostics; never prompts, coordinates or history. */
    private val log: (String) -> Unit = {},
) {
    private val handledJourneyIds = mutableSetOf<String>()

    suspend fun onJourneyConfirmed(journey: JourneySummary): MissionGenerationOutcome {
        val outcome = try {
            generate(journey)
        } catch (exception: CancellationException) {
            synchronized(handledJourneyIds) { handledJourneyIds -= journey.journeyId }
            throw exception
        } catch (exception: Exception) {
            MissionGenerationOutcome.Failed(exception.javaClass.simpleName)
        }
        if (outcome is MissionGenerationOutcome.Failed) {
            // Allow a later confirmation of the same journey to retry.
            synchronized(handledJourneyIds) { handledJourneyIds -= journey.journeyId }
        }
        log("Mission generation: ${outcome.label()}")
        return outcome
    }

    private suspend fun generate(journey: JourneySummary): MissionGenerationOutcome {
        val uid = authRepository.currentUserId ?: return MissionGenerationOutcome.NotSignedIn
        if (journey.userId != uid) return MissionGenerationOutcome.NotSignedIn
        if (journey.linkedMissionId != null) return MissionGenerationOutcome.LinkedToMission

        val firstTime = synchronized(handledJourneyIds) { handledJourneyIds.add(journey.journeyId) }
        if (!firstTime) return MissionGenerationOutcome.AlreadyHandled

        val preferences = profileRepository.getProfile()?.preferences ?: UserPreferences()
        if (!preferences.routineLearningEnabled) return MissionGenerationOutcome.Disabled

        val now = clock()
        val history = journeyRepository.observeJourneyHistory(uid).first()
            .filter { it.journeyId != journey.journeyId && it.startTimeMillis >= now - HISTORY_WINDOW_MILLIS }

        val pattern = recurringJourneyDetector.detect(journey, history)
            ?: return MissionGenerationOutcome.NotRecurring

        val carbon = carbonCalculator.calculate(journey)
        val candidates = carbon.lowerCarbonAlternatives.filter { it.isUsable() }
        if (candidates.isEmpty()) return MissionGenerationOutcome.NoAlternative

        val startLabel = placeName(journey.startLocation) ?: journey.startLocation.toLabel()
        val destinationLabel = placeName(journey.endLocation) ?: journey.endLocation.toLabel()
        val existing = missionRepository.observeMissions().first()
        if (existing.any { it.startLabel == startLabel && it.destinationLabel == destinationLabel }) {
            // This route already has a mission (any status, including dismissed).
            return MissionGenerationOutcome.AlreadyHandled
        }
        if (existing.any { it.status == MissionStatus.SUGGESTED }) {
            // The Missions screen shows one suggestion at a time; wait until it is answered.
            return MissionGenerationOutcome.SuggestionPending
        }

        val needsTransit = candidates.any { it.mode == TransportMode.PUBLIC_TRANSPORT }
        val (weather, routes, transit) = coroutineScope {
            val weather = async { orNull { externalDataRepository.getWeather(journey.startLocation) } }
            val routes = async {
                orNull { externalDataRepository.getRouteOptions(journey.startLocation, journey.endLocation) }
            }
            val transit = async {
                if (needsTransit) {
                    orNull {
                        externalDataRepository.getPublicTransportOptions(journey.startLocation, journey.endLocation)
                    }.orEmpty()
                } else {
                    emptyList()
                }
            }
            Triple(weather.await(), routes.await().orEmpty(), transit.await())
        }

        // Public transport is only recommended when a real service was found.
        val alternatives = candidates.filter {
            it.mode != TransportMode.PUBLIC_TRANSPORT || transit.isNotEmpty()
        }
        if (alternatives.isEmpty()) return MissionGenerationOutcome.NoAlternative

        val context = MissionContext(
            journey = journey,
            transportResult = TransportResult(
                mode = journey.transportMode,
                // Confirmed by the user on the review screen, but no classifier confidence is
                // persisted with the journey; a neutral value avoids implying certainty.
                confidence = CONSERVATIVE_CONFIDENCE,
                alternativesConsidered = alternatives.map { it.mode },
            ),
            carbonResult = carbon.copy(lowerCarbonAlternatives = alternatives),
            route = routes.firstOrNull { it.mode == journey.transportMode } ?: journey.toRouteInfo(),
            weather = weather ?: UNKNOWN_WEATHER,
            publicTransportOptions = transit,
            recentJourneyHistory = history
                .sortedByDescending { it.startTimeMillis }
                .take(MAX_HISTORY_FOR_AI),
        )

        val recommendation = if (weather != null) {
            missionGenerator.generateRecommendation(context)
        } else {
            fallbackGenerator.generateMission(context).let { mission ->
                MissionRecommendation(
                    mission = mission,
                    notificationTitle = "Your EcoStep mission",
                    notificationMessage = mission.explanation,
                    usedAi = false,
                )
            }
        }

        val mission = EcoMissionMapper.toMission(
            ecoMission = recommendation.mission,
            pattern = pattern,
            referenceJourney = journey,
            startLabel = startLabel,
            destinationLabel = destinationLabel,
            alternatives = alternatives,
            ecoPointsCalculator = ecoPointsCalculator,
            zoneId = zoneId,
            today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate(),
        )
        if (existing.any { it.missionId == mission.missionId }) {
            return MissionGenerationOutcome.AlreadyHandled
        }
        missionRepository.createMission(mission)
        return MissionGenerationOutcome.Created(mission.missionId, recommendation.usedAi)
    }

    private suspend fun <T> orNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            null
        }

    private fun CarbonAlternative.isUsable(): Boolean =
        mode != TransportMode.UNKNOWN && savingsGrams.isFinite() && savingsGrams > 0.0

    private fun JourneySummary.toRouteInfo() = RouteInfo(
        mode = transportMode,
        distanceMeters = distanceMeters,
        durationSeconds = ((endTimeMillis - startTimeMillis) / 1_000L).coerceAtLeast(0L),
    )

    private fun GeoPoint.toLabel(): String =
        String.format(Locale.US, "%.4f, %.4f", latitude, longitude)

    private fun MissionGenerationOutcome.label(): String = when (this) {
        is MissionGenerationOutcome.Created -> if (usedAi) "created (AI)" else "created (fallback)"
        is MissionGenerationOutcome.Failed -> "failed ($reason)"
        else -> javaClass.simpleName
    }

    private companion object {
        const val HISTORY_WINDOW_MILLIS = 60L * 24 * 60 * 60 * 1_000
        const val MAX_HISTORY_FOR_AI = 10
        const val CONSERVATIVE_CONFIDENCE = 50.0

        /** Only read by the non-AI fallback, which ignores weather. */
        val UNKNOWN_WEATHER = WeatherData(temperatureCelsius = 0.0, conditions = "unknown")
    }
}
