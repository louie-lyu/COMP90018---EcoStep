package com.ecostep.app.ui.adapters

import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionModeEstimate
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.ui.mock.MissionDay
import com.ecostep.app.ui.mock.MissionPageItem
import com.ecostep.app.ui.mock.MissionScreenDataSource
import com.ecostep.app.ui.mock.MissionTransportOption
import com.ecostep.app.ui.viewmodels.UpcomingMissionUi
import com.ecostep.app.core.integration.MissionRouteEstimate
import java.time.DayOfWeek

/** Display labels used by the mission UI for each transport mode. */
object TransportModeLabels {
    fun label(mode: TransportMode?): String = when (mode) {
        TransportMode.WALKING -> "Walking"
        TransportMode.CYCLING -> "Cycling"
        TransportMode.PUBLIC_TRANSPORT -> "Public Transport"
        TransportMode.CAR -> "Car"
        TransportMode.UNKNOWN, null -> "Any"
    }

    fun parse(label: String): TransportMode? = when (label.trim().lowercase()) {
        "walking", "walk" -> TransportMode.WALKING
        "cycling", "cycle", "bike", "bicycle" -> TransportMode.CYCLING
        "public transport", "public_transport" -> TransportMode.PUBLIC_TRANSPORT
        "car", "driving" -> TransportMode.CAR
        else -> null
    }
}

fun Mission.toPageItem(): MissionPageItem {
    val targetEstimate = estimates.firstOrNull { it.mode == targetTransportMode }
    return MissionPageItem(
        mission = UpcomingMissionUi(
            missionId = missionId,
            routeTitle = title,
            transportLabel = TransportModeLabels.label(targetTransportMode),
            startTimeMillis = 0L,
            endTimeMillis = 0L,
            estimatedCarbonSavedKg = (targetEstimate?.estimatedCarbonSavedGrams ?: 0.0) / 1000.0,
            estimatedEcoPoints = targetEstimate?.estimatedEcoPoints ?: 0,
        ),
        startLocation = startLabel,
        destination = destinationLabel,
        scheduledHour = scheduledMinuteOfDay / 60,
        scheduledMinute = scheduledMinuteOfDay % 60,
        repeatDays = recurrence.toMissionDays(),
        explanation = description,
        transportOptions = estimates.map(MissionModeEstimate::toTransportOption),
        distanceMeters = targetDistanceMeters,
        nextOccurrenceDate = nextOccurrenceDate,
    )
}

/** Applies the editable UI fields of [item] onto this mission, including recalculated estimates. */
fun Mission.withEditsFrom(item: MissionPageItem): Mission = copy(
    title = item.mission.routeTitle.trim(),
    startLabel = item.startLocation.trim(),
    destinationLabel = item.destination.trim(),
    targetTransportMode = TransportModeLabels.parse(item.mission.transportLabel) ?: targetTransportMode,
    targetDistanceMeters = item.distanceMeters ?: targetDistanceMeters,
    scheduledMinuteOfDay = item.scheduledHour * 60 + item.scheduledMinute,
    recurrence = MissionRecurrence.weekly(
        days = item.repeatDays.mapTo(mutableSetOf()) { it.toDayOfWeek() },
        timezone = recurrence.timezone,
    ),
    estimates = item.transportOptions.toEstimates().ifEmpty { estimates },
)

/**
 * Replaces the item's per-mode options with [estimate] (for a new start or destination) and
 * updates the chosen mode's headline values and distance.
 */
fun MissionPageItem.withRouteEstimate(estimate: MissionRouteEstimate): MissionPageItem {
    val selectedMode = TransportModeLabels.parse(mission.transportLabel)
    val selected = estimate.estimates.firstOrNull { it.mode == selectedMode }
    val selectedOption = selected?.toTransportOption()
    return copy(
        transportOptions = estimate.estimates.map(MissionModeEstimate::toTransportOption),
        distanceMeters = selectedMode?.let(estimate.distanceByMode::get) ?: distanceMeters,
        mission = mission.copy(
            estimatedEcoPoints = selectedOption?.estimatedEcoPoints ?: mission.estimatedEcoPoints,
            estimatedCarbonSavedKg = selectedOption?.estimatedCarbonSavedKg ?: mission.estimatedCarbonSavedKg,
        ),
    )
}

private fun MissionModeEstimate.toTransportOption() = MissionTransportOption(
    label = TransportModeLabels.label(mode),
    estimatedEcoPoints = estimatedEcoPoints,
    estimatedCarbonSavedKg = estimatedCarbonSavedGrams / 1000.0,
)

private fun List<MissionTransportOption>.toEstimates(): List<MissionModeEstimate> =
    mapNotNull { option ->
        val mode = TransportModeLabels.parse(option.label) ?: return@mapNotNull null
        MissionModeEstimate(
            mode = mode,
            estimatedEcoPoints = option.estimatedEcoPoints,
            estimatedCarbonSavedGrams = option.estimatedCarbonSavedKg * 1000.0,
        )
    }

/** Converts a UI mission (e.g. the development seed) into a production mission. */
fun MissionPageItem.toMission(status: MissionStatus, timezone: String): Mission = Mission(
    missionId = mission.missionId,
    title = mission.routeTitle,
    description = explanation,
    startLabel = startLocation,
    destinationLabel = destination,
    targetTransportMode = TransportModeLabels.parse(mission.transportLabel),
    status = status,
    recurrence = MissionRecurrence.weekly(repeatDays.mapTo(mutableSetOf()) { it.toDayOfWeek() }, timezone),
    scheduledMinuteOfDay = scheduledHour * 60 + scheduledMinute,
    estimates = transportOptions.toEstimates(),
)

/** Development seed built from the UI prototype's sample missions (debug builds only). */
fun MissionScreenDataSource.toSeedMissions(timezone: String): List<Mission> =
    listOfNotNull(getSuggestedMission()?.toMission(MissionStatus.SUGGESTED, timezone)) +
        getUpcomingMissions().map { it.toMission(MissionStatus.ACCEPTED, timezone) }

private fun MissionRecurrence.toMissionDays(): Set<MissionDay> = when {
    daysOfWeek.isEmpty() && type == com.ecostep.app.data.model.RecurrenceType.DAILY ->
        MissionDay.entries.toSet()
    else -> daysOfWeek.mapTo(mutableSetOf()) { MissionDay.entries[it - 1] }
}

private fun MissionDay.toDayOfWeek(): DayOfWeek = DayOfWeek.of(ordinal + 1)
