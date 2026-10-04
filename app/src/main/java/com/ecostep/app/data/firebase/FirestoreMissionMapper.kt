package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.Mission
import com.ecostep.app.data.model.MissionModeEstimate
import com.ecostep.app.data.model.MissionOccurrence
import com.ecostep.app.data.model.MissionRecurrence
import com.ecostep.app.data.model.MissionStatus
import com.ecostep.app.data.model.RecurrenceType

internal const val MISSION_SCHEMA_VERSION = 1
internal const val MISSION_RESULT_SCHEMA_VERSION = 1
private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** Client-owned mission fields; never includes createdAt (set once at creation). */
internal fun Mission.toFirestoreMap(): Map<String, Any?> = mapOf(
    "schemaVersion" to MISSION_SCHEMA_VERSION,
    "missionId" to missionId,
    "title" to title,
    "description" to description,
    "missionType" to missionType,
    "startLabel" to startLabel,
    "destinationLabel" to destinationLabel,
    "targetTransportMode" to targetTransportMode?.name,
    "targetDistanceMeters" to targetDistanceMeters,
    "status" to status.firestoreValue,
    "recurrence" to mapOf(
        "type" to recurrence.type.firestoreValue,
        "interval" to recurrence.interval,
        "daysOfWeek" to recurrence.daysOfWeek.sorted(),
        "timezone" to recurrence.timezone,
    ),
    "scheduledMinuteOfDay" to scheduledMinuteOfDay,
    "nextOccurrenceDate" to nextOccurrenceDate,
    "activeOccurrenceDate" to activeOccurrenceDate,
    "estimates" to estimates.map {
        mapOf(
            "mode" to it.mode.name,
            "estimatedEcoPoints" to it.estimatedEcoPoints,
            "estimatedCarbonSavedGrams" to it.estimatedCarbonSavedGrams,
        )
    },
)

/** Validates a mission before any write. */
internal fun Mission.requireValidForWrite() {
    require(missionId.isNotBlank() && missionId.length <= 128 && '/' !in missionId) {
        "Mission ID is invalid."
    }
    require(title.isNotBlank() && title.length <= 120) { "Mission title must be 1-120 characters." }
    require(description.length <= 500) { "Mission description is too long." }
    require(scheduledMinuteOfDay in 0 until 24 * 60) { "Mission time is invalid." }
    require(recurrence.interval in 1..52) { "Mission interval is invalid." }
    require(recurrence.daysOfWeek.all { it in 1..7 }) { "Mission days are invalid." }
    targetDistanceMeters?.let {
        require(it.isFinite() && it >= 0.0) { "Target distance must be finite and non-negative." }
    }
    estimates.forEach {
        require(it.estimatedEcoPoints >= 0) { "Estimated EcoPoints cannot be negative." }
        require(it.estimatedCarbonSavedGrams.isFinite() && it.estimatedCarbonSavedGrams >= 0.0) {
            "Estimated carbon saving must be finite and non-negative."
        }
    }
    listOfNotNull(nextOccurrenceDate, activeOccurrenceDate).forEach {
        require(ISO_DATE.matches(it)) { "Occurrence dates must be YYYY-MM-DD." }
    }
}

/** Returns null for documents with an unknown status or no title. */
internal fun Map<String, Any?>.toMission(documentId: String): Mission? {
    val status = MissionStatus.fromFirestore(this["status"]) ?: return null
    val title = this["title"] as? String ?: return null
    val recurrence = this["recurrence"] as? Map<*, *> ?: emptyMap<String, Any?>()
    return Mission(
        missionId = documentId,
        title = title,
        description = this["description"] as? String ?: "",
        missionType = this["missionType"] as? String ?: Mission.MISSION_TYPE_ROUTE,
        startLabel = this["startLabel"] as? String ?: "",
        destinationLabel = this["destinationLabel"] as? String ?: "",
        targetTransportMode = (this["targetTransportMode"] as? String)?.toTransportMode(),
        targetDistanceMeters = (this["targetDistanceMeters"] as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it >= 0.0 },
        status = status,
        recurrence = MissionRecurrence(
            type = RecurrenceType.fromFirestore(recurrence["type"]),
            interval = (recurrence["interval"] as? Number)?.toInt()?.coerceIn(1, 52) ?: 1,
            daysOfWeek = (recurrence["daysOfWeek"] as? List<*>).orEmpty()
                .mapNotNull { (it as? Number)?.toInt() }
                .filterTo(mutableSetOf()) { it in 1..7 },
            timezone = recurrence["timezone"] as? String ?: "UTC",
        ),
        scheduledMinuteOfDay = (this["scheduledMinuteOfDay"] as? Number)?.toInt()
            ?.coerceIn(0, 24 * 60 - 1) ?: 0,
        nextOccurrenceDate = (this["nextOccurrenceDate"] as? String)?.takeIf { ISO_DATE.matches(it) },
        activeOccurrenceDate = (this["activeOccurrenceDate"] as? String)?.takeIf { ISO_DATE.matches(it) },
        estimates = (this["estimates"] as? List<*>).orEmpty().mapNotNull { raw ->
            val estimate = raw as? Map<*, *> ?: return@mapNotNull null
            val mode = (estimate["mode"] as? String)?.toTransportMode() ?: return@mapNotNull null
            MissionModeEstimate(
                mode = mode,
                estimatedEcoPoints = (estimate["estimatedEcoPoints"] as? Number)?.toInt()
                    ?.coerceAtLeast(0) ?: 0,
                estimatedCarbonSavedGrams = (estimate["estimatedCarbonSavedGrams"] as? Number)
                    ?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            )
        },
        createdAtMillis = timestampMillis(this["createdAt"]),
        updatedAtMillis = timestampMillis(this["updatedAt"]),
    )
}

internal fun Map<String, Any?>.toMissionOccurrence(documentId: String): MissionOccurrence? {
    val missionId = this["missionId"] as? String ?: return null
    val date = (this["occurrenceDate"] as? String)?.takeIf { ISO_DATE.matches(it) } ?: return null
    return MissionOccurrence(
        resultId = documentId,
        missionId = missionId,
        occurrenceDate = date,
        accepted = this["accepted"] as? Boolean ?: false,
        completed = this["completed"] as? Boolean ?: false,
        skipped = this["skipped"] as? Boolean ?: false,
        linkedJourneyId = this["linkedJourneyId"] as? String,
        actualTransportMode = (this["actualTransportMode"] as? String)?.toTransportMode(),
        actualCarbonSavingGrams = (this["actualCarbonSavingGrams"] as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it >= 0.0 },
        ecoPointsAwarded = (this["ecoPointsAwarded"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
        completedAtMillis = (this["completedAtMillis"] as? Number)?.toLong(),
        lastActionAtMillis = (this["lastActionAtMillis"] as? Number)?.toLong(),
    )
}

/** Merge body for a client occurrence action; backend-only result fields are never included. */
internal fun occurrenceMergeDocument(
    missionId: String,
    occurrenceDate: String,
    fields: Map<String, Any?>,
    atMillis: Long,
    serverTimestamp: Any,
): Map<String, Any?> {
    require(ISO_DATE.matches(occurrenceDate)) { "Occurrence dates must be YYYY-MM-DD." }
    return mapOf(
        "schemaVersion" to MISSION_RESULT_SCHEMA_VERSION,
        "missionId" to missionId,
        "occurrenceDate" to occurrenceDate,
        "lastActionAtMillis" to atMillis,
        "updatedAt" to serverTimestamp,
    ) + fields
}
