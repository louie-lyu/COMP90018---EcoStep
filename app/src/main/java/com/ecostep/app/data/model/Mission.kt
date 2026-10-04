package com.ecostep.app.data.model

import java.time.DayOfWeek
import java.time.LocalDate

/*
 * Production mission model persisted at users/{uid}/missions/{missionId}, and one
 * occurrence/result per (missionId, occurrenceDate) at users/{uid}/missionResults/{resultId}.
 * UI models (MissionPageItem) are mapped from these, never persisted directly.
 */

enum class MissionStatus(val firestoreValue: String) {
    SUGGESTED("suggested"),
    ACCEPTED("accepted"),
    ACTIVE("active"),
    COMPLETED("completed"),
    DISMISSED("dismissed"),
    ARCHIVED("archived"),
    ;

    companion object {
        fun fromFirestore(value: Any?): MissionStatus? =
            entries.firstOrNull { it.firestoreValue == value }
    }
}

enum class RecurrenceType(val firestoreValue: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKLY("weekly"),
    CUSTOM("custom"),
    ;

    companion object {
        fun fromFirestore(value: Any?): RecurrenceType =
            entries.firstOrNull { it.firestoreValue == value } ?: NONE
    }
}

data class MissionRecurrence(
    val type: RecurrenceType = RecurrenceType.NONE,
    val interval: Int = 1,
    /** ISO days of week: 1 = Monday ... 7 = Sunday. */
    val daysOfWeek: Set<Int> = emptySet(),
    /** IANA zone the schedule and occurrence dates are expressed in. */
    val timezone: String = "UTC",
) {
    val isRecurring: Boolean get() = type != RecurrenceType.NONE

    /** First occurrence date strictly after [date], or null for a one-off mission. */
    fun nextOccurrenceAfter(date: LocalDate): LocalDate? {
        val days = when (type) {
            RecurrenceType.NONE -> return null
            RecurrenceType.DAILY -> (1..7).toSet()
            RecurrenceType.WEEKLY, RecurrenceType.CUSTOM -> daysOfWeek.ifEmpty { (1..7).toSet() }
        }
        return (1L..7L).map { date.plusDays(it) }.first { it.dayOfWeek.value in days }
    }

    /** True when the schedule includes [date] (one-off missions are always due). */
    fun occursOn(date: LocalDate): Boolean = when (type) {
        RecurrenceType.NONE, RecurrenceType.DAILY -> true
        RecurrenceType.WEEKLY, RecurrenceType.CUSTOM ->
            daysOfWeek.isEmpty() || date.dayOfWeek.value in daysOfWeek
    }

    companion object {
        fun weekly(days: Set<DayOfWeek>, timezone: String): MissionRecurrence = MissionRecurrence(
            type = when {
                days.isEmpty() -> RecurrenceType.NONE
                days.size == 7 -> RecurrenceType.DAILY
                else -> RecurrenceType.WEEKLY
            },
            daysOfWeek = days.mapTo(mutableSetOf()) { it.value },
            timezone = timezone,
        )
    }
}

/** Per-mode estimate shown on the mission card; an estimate only, never a trusted value. */
data class MissionModeEstimate(
    val mode: TransportMode,
    val estimatedEcoPoints: Int,
    val estimatedCarbonSavedGrams: Double,
)

data class Mission(
    val missionId: String,
    val title: String,
    val description: String = "",
    val missionType: String = MISSION_TYPE_ROUTE,
    val startLabel: String = "",
    val destinationLabel: String = "",
    val targetTransportMode: TransportMode? = null,
    val targetDistanceMeters: Double? = null,
    val status: MissionStatus = MissionStatus.SUGGESTED,
    val recurrence: MissionRecurrence = MissionRecurrence(),
    /** Local time of day the mission is scheduled, in minutes after midnight. */
    val scheduledMinuteOfDay: Int = 0,
    /** "YYYY-MM-DD" in the recurrence timezone. */
    val nextOccurrenceDate: String? = null,
    /** Occurrence currently in progress while [status] is ACTIVE. */
    val activeOccurrenceDate: String? = null,
    val estimates: List<MissionModeEstimate> = emptyList(),
    val createdAtMillis: Long? = null,
    val updatedAtMillis: Long? = null,
) {
    companion object {
        const val MISSION_TYPE_ROUTE = "route"
    }
}

/** One occurrence of a mission and its outcome. */
data class MissionOccurrence(
    val resultId: String,
    val missionId: String,
    val occurrenceDate: String,
    val accepted: Boolean = false,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val linkedJourneyId: String? = null,
    /** Backend-written from the linked, confirmed journey. */
    val actualTransportMode: TransportMode? = null,
    /** Backend-written; null until the award is processed. */
    val actualCarbonSavingGrams: Double? = null,
    /** Backend-written; 0 until the award is processed. */
    val ecoPointsAwarded: Int = 0,
    val completedAtMillis: Long? = null,
    /** Client time of the last user action (start, skip, complete). */
    val lastActionAtMillis: Long? = null,
) {
    fun toMissionResult(): MissionResult = MissionResult(
        missionId = missionId,
        accepted = accepted,
        completed = completed,
        actualTransportMode = actualTransportMode,
        actualCarbonSavingGrams = actualCarbonSavingGrams,
        timestampMillis = completedAtMillis ?: lastActionAtMillis ?: 0L,
    )

    companion object {
        /** Deterministic ID: one document per (missionId, occurrenceDate). */
        fun resultId(missionId: String, occurrenceDate: String): String =
            "${missionId}_$occurrenceDate"
    }
}
