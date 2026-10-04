package com.ecostep.app.data.model

import java.time.LocalDate

/**
 * Pure mission state machine. Repositories persist its results; invalid transitions throw
 * [IllegalStateException] so a stale UI action cannot corrupt a mission.
 *
 * suggested --accept--> accepted --start--> active --end/complete--> accepted (recurring)
 * suggested --dismiss--> dismissed                     \-> completed / archived (one-off)
 * accepted --skipToday--> accepted (occurrence recorded as skipped, definition kept)
 */
object MissionTransitions {

    fun accept(mission: Mission, today: LocalDate): Mission {
        check(mission.status == MissionStatus.SUGGESTED) { "Only a suggested mission can be accepted." }
        return mission.copy(
            status = MissionStatus.ACCEPTED,
            nextOccurrenceDate = firstOccurrenceOnOrAfter(mission.recurrence, today).toString(),
        )
    }

    fun dismiss(mission: Mission): Mission {
        check(mission.status == MissionStatus.SUGGESTED) { "Only a suggested mission can be dismissed." }
        return mission.copy(status = MissionStatus.DISMISSED, nextOccurrenceDate = null)
    }

    fun start(mission: Mission, today: LocalDate): Mission {
        check(mission.status == MissionStatus.ACCEPTED) { "Only an accepted mission can be started." }
        return mission.copy(status = MissionStatus.ACTIVE, activeOccurrenceDate = today.toString())
    }

    /** Ends the active occurrence. Recurring missions return to the upcoming list. */
    fun end(mission: Mission, today: LocalDate, completed: Boolean): Mission {
        check(mission.status == MissionStatus.ACTIVE) { "Only an active mission can be ended." }
        return if (mission.recurrence.isRecurring) {
            mission.copy(
                status = MissionStatus.ACCEPTED,
                activeOccurrenceDate = null,
                nextOccurrenceDate = mission.recurrence.nextOccurrenceAfter(today)?.toString(),
            )
        } else {
            mission.copy(
                status = if (completed) MissionStatus.COMPLETED else MissionStatus.ARCHIVED,
                activeOccurrenceDate = null,
                nextOccurrenceDate = null,
            )
        }
    }

    /**
     * Skipping today only moves the schedule forward; the definition is never deleted. A
     * one-off mission keeps its status and simply has no occurrence today.
     */
    fun skipToday(mission: Mission, today: LocalDate): Mission {
        check(mission.status == MissionStatus.ACCEPTED) { "Only an upcoming mission can be skipped." }
        return mission.copy(
            nextOccurrenceDate =
                mission.recurrence.nextOccurrenceAfter(today)?.toString() ?: mission.nextOccurrenceDate,
        )
    }

    fun edit(mission: Mission, edited: Mission, today: LocalDate): Mission {
        check(mission.missionId == edited.missionId) { "Cannot change a mission's ID." }
        check(mission.status in EDITABLE) { "This mission can no longer be edited." }
        return edited.copy(
            status = mission.status,
            activeOccurrenceDate = mission.activeOccurrenceDate,
            createdAtMillis = mission.createdAtMillis,
            nextOccurrenceDate = if (mission.status == MissionStatus.SUGGESTED) {
                mission.nextOccurrenceDate
            } else {
                firstOccurrenceOnOrAfter(edited.recurrence, today).toString()
            },
        )
    }

    private fun firstOccurrenceOnOrAfter(recurrence: MissionRecurrence, date: LocalDate): LocalDate =
        if (recurrence.occursOn(date)) date else recurrence.nextOccurrenceAfter(date) ?: date

    private val EDITABLE = setOf(MissionStatus.SUGGESTED, MissionStatus.ACCEPTED, MissionStatus.ACTIVE)
}
