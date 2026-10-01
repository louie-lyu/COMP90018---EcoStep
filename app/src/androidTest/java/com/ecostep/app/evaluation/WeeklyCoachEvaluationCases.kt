package com.ecostep.app.evaluation

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode

internal data class ExpectedWeeklyReport(
    val accepted: Int,
    val completed: Int,
    val completionRate: Double,
    val savingsGrams: Double,
    val mode: TransportMode?,
    val fallbackMessage: String,
)

internal data class WeeklyCoachCase(
    val name: String,
    val results: List<MissionResult>,
    val expected: ExpectedWeeklyReport?,
    val startMillis: Long = 1000L,
    val endMillis: Long = 2000L,
)

/** Hand-calculated totals for fixed mission histories, independent of production aggregation. */
internal object WeeklyCoachEvaluationCases {
    const val EMPTY = "Try accepting one small low-carbon mission next week."
    const val NONE_COMPLETED = "Start with one achievable mission and build from there."
    const val LOW = "You have made a start. Choose one simple mission next week and focus on completing it."
    const val GOOD = "Good progress this week. Try completing one more achievable mission next week."
    const val HIGH = "Great work completing your missions this week. Keep choosing achievable low-carbon goals."

    fun cases(): List<WeeklyCoachCase> = buildList {
        val empty = ExpectedWeeklyReport(0, 0, 0.0, 0.0, null, EMPTY)
        add(WeeklyCoachCase("empty_week", emptyList(), empty))
        add(WeeklyCoachCase("mixed_mission_states", listOf(
            mission("walk_1", saving = 100.0), mission("walk_2", saving = 150.0),
            mission("cycle", mode = TransportMode.CYCLING, saving = 200.0),
            mission("unfinished", completed = false, saving = 500.0),
            mission("rejected", accepted = false, saving = 999.0),
        ), ExpectedWeeklyReport(4, 3, 75.0, 450.0, TransportMode.WALKING, GOOD)))
        add(WeeklyCoachCase("inclusive_week_boundaries", listOf(
            mission("start", timestamp = 1000L), mission("end", timestamp = 2000L),
            mission("before", timestamp = 999L), mission("after", timestamp = 2001L),
        ), ExpectedWeeklyReport(2, 2, 100.0, 200.0, TransportMode.WALKING, HIGH)))
        add(WeeklyCoachCase("single_instant_week", listOf(
            mission("exact", timestamp = 1000L), mission("outside", timestamp = 1001L),
        ), ExpectedWeeklyReport(1, 1, 100.0, 100.0, TransportMode.WALKING, HIGH), endMillis = 1000L))
        add(WeeklyCoachCase("all_outside_week", listOf(mission("outside", timestamp = 999L)), empty))
        add(WeeklyCoachCase("rejected_completed_mission", listOf(mission("rejected", accepted = false)), empty))
        add(WeeklyCoachCase("accepted_none_completed", listOf(
            mission("pending_1", completed = false), mission("pending_2", completed = false),
        ), ExpectedWeeklyReport(2, 0, 0.0, 0.0, null, NONE_COMPLETED)))
        add(WeeklyCoachCase("invalid_savings_ignored", listOf(null, -1.0, Double.NaN,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 80.0).mapIndexed { index, value ->
            mission("saving_$index", saving = value)
        }, ExpectedWeeklyReport(6, 6, 100.0, 80.0, TransportMode.WALKING, HIGH)))
        add(WeeklyCoachCase("missing_and_unknown_modes", listOf(
            mission("missing", mode = null), mission("unknown", mode = TransportMode.UNKNOWN),
        ), ExpectedWeeklyReport(2, 2, 100.0, 200.0, null, HIGH)))
        add(WeeklyCoachCase("most_used_completed_mode", listOf(
            mission("walk"), mission("cycle_1", mode = TransportMode.CYCLING),
            mission("cycle_2", mode = TransportMode.CYCLING),
            mission("pending_walk", completed = false),
        ), ExpectedWeeklyReport(4, 3, 75.0, 300.0, TransportMode.CYCLING, GOOD)))
        add(WeeklyCoachCase("fractional_savings", listOf(
            mission("fraction_1", saving = 100.125), mission("fraction_2", saving = 0.125),
        ), ExpectedWeeklyReport(2, 2, 100.0, 100.25, TransportMode.WALKING, HIGH)))
        add(WeeklyCoachCase("unsorted_history", listOf(
            mission("cycle_late", mode = TransportMode.CYCLING, saving = 300.0, timestamp = 1900L),
            mission("outside", timestamp = 999L), mission("walk", timestamp = 1000L),
            mission("cycle_early", mode = TransportMode.CYCLING, saving = 200.0, timestamp = 1100L),
        ), ExpectedWeeklyReport(3, 3, 100.0, 600.0, TransportMode.CYCLING, HIGH)))
        for ((completed, fallback) in listOf(49 to LOW, 50 to GOOD, 79 to GOOD, 80 to HIGH)) {
            add(WeeklyCoachCase("completion_rate_$completed", List(100) { index ->
                mission("rate_$index", completed = index < completed)
            }, ExpectedWeeklyReport(100, completed, completed.toDouble(), completed * 100.0, TransportMode.WALKING, fallback)))
        }
        add(WeeklyCoachCase("all_completed", listOf(mission("complete")),
            ExpectedWeeklyReport(1, 1, 100.0, 100.0, TransportMode.WALKING, HIGH)))
        add(WeeklyCoachCase("valid_mode_among_unknowns", listOf(
            mission("unknown", mode = TransportMode.UNKNOWN), mission("missing", mode = null), mission("walk"),
        ), ExpectedWeeklyReport(3, 3, 100.0, 300.0, TransportMode.WALKING, HIGH)))
        add(WeeklyCoachCase("reversed_week_rejected", emptyList(), null, 2000L, 1000L))
    }

    fun mission(id: String, accepted: Boolean = true, completed: Boolean = true,
        mode: TransportMode? = TransportMode.WALKING, saving: Double? = 100.0, timestamp: Long = 1500L) =
        MissionResult("private_weekly_$id", accepted, completed, mode, saving, timestamp)
}
