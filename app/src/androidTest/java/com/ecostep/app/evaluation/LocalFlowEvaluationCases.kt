package com.ecostep.app.evaluation

import com.ecostep.app.data.model.TransportMode

internal data class ExpectedLocalFlow(
    val emissionsGrams: Double?,
    val missionMode: TransportMode? = TransportMode.WALKING,
    val missionSavingGrams: Double = 960.0,
    val actualSavingGrams: Double = missionSavingGrams,
    val points: Int = 116,
    val weeklyAccepted: Int = 1,
    val weeklyCompleted: Int = 1,
    val weeklyRate: Double = 100.0,
    val weeklySavingGrams: Double = actualSavingGrams,
    val weeklyMode: TransportMode? = missionMode,
    val errorStage: String? = null,
)

internal data class LocalFlowCase(
    val name: String,
    val expected: ExpectedLocalFlow,
    val distanceMeters: Double = 5000.0,
    val originalMode: TransportMode = TransportMode.CAR,
    val availableModes: Set<TransportMode> = setOf(TransportMode.WALKING, TransportMode.CYCLING, TransportMode.PUBLIC_TRANSPORT),
    val actualMode: TransportMode = TransportMode.WALKING,
    val accepted: Boolean = true,
    val completed: Boolean = true,
    val timestampMillis: Long = 1500L,
)

/** Prototype factors: car 192 g/km, public transport 89 g/km; expected totals are explicit. */
internal object LocalFlowEvaluationCases {
    fun cases(): List<LocalFlowCase> = buildList {
        val walking = ExpectedLocalFlow(960.0)
        add(LocalFlowCase("car_to_walking", walking))
        add(LocalFlowCase("car_to_cycling", ExpectedLocalFlow(960.0, TransportMode.CYCLING, points = 111),
            availableModes = setOf(TransportMode.CYCLING), actualMode = TransportMode.CYCLING))
        add(LocalFlowCase("car_to_public_transport", ExpectedLocalFlow(960.0, TransportMode.PUBLIC_TRANSPORT, 515.0, points = 57),
            availableModes = setOf(TransportMode.PUBLIC_TRANSPORT), actualMode = TransportMode.PUBLIC_TRANSPORT))
        add(LocalFlowCase("public_transport_to_walking", ExpectedLocalFlow(445.0, missionSavingGrams = 445.0, points = 65),
            originalMode = TransportMode.PUBLIC_TRANSPORT))
        add(LocalFlowCase("public_transport_to_cycling", ExpectedLocalFlow(445.0, TransportMode.CYCLING, 445.0, points = 60),
            originalMode = TransportMode.PUBLIC_TRANSPORT, availableModes = setOf(TransportMode.CYCLING), actualMode = TransportMode.CYCLING))
        add(LocalFlowCase("fractional_distance", ExpectedLocalFlow(240.096, missionSavingGrams = 240.096, points = 44), distanceMeters = 1250.5))
        add(LocalFlowCase("points_cap", ExpectedLocalFlow(5760.0, missionSavingGrams = 5760.0, points = 500), distanceMeters = 30000.0))
        add(LocalFlowCase("short_positive_journey", ExpectedLocalFlow(0.192, missionSavingGrams = 0.192, points = 20), distanceMeters = 1.0))
        val rejected = walking.copy(points = 0, weeklyAccepted = 0, weeklyCompleted = 0, weeklyRate = 0.0,
            weeklySavingGrams = 0.0, weeklyMode = null)
        add(LocalFlowCase("rejected_mission", rejected, accepted = false, completed = false))
        add(LocalFlowCase("rejected_but_marked_completed", rejected, accepted = false))
        add(LocalFlowCase("accepted_not_completed", walking.copy(points = 0, weeklyCompleted = 0, weeklyRate = 0.0,
            weeklySavingGrams = 0.0, weeklyMode = null), completed = false))
        add(LocalFlowCase("corrected_to_public_transport", walking.copy(actualSavingGrams = 515.0,
            points = 57, weeklySavingGrams = 515.0, weeklyMode = TransportMode.PUBLIC_TRANSPORT), actualMode = TransportMode.PUBLIC_TRANSPORT))
        add(LocalFlowCase("corrected_to_car", walking.copy(actualSavingGrams = 0.0,
            points = 0, weeklySavingGrams = 0.0, weeklyMode = TransportMode.CAR), actualMode = TransportMode.CAR))
        val outsideWeek = walking.copy(weeklyAccepted = 0, weeklyCompleted = 0, weeklyRate = 0.0,
            weeklySavingGrams = 0.0, weeklyMode = null)
        add(LocalFlowCase("before_week", outsideWeek, timestampMillis = 999L))
        add(LocalFlowCase("after_week", outsideWeek, timestampMillis = 2001L))
        add(LocalFlowCase("week_start_included", walking, timestampMillis = 1000L))
        add(LocalFlowCase("week_end_included", walking, timestampMillis = 2000L))
        val noAlternative = ExpectedLocalFlow(0.0, missionMode = null, errorStage = "fallback")
        add(LocalFlowCase("zero_distance_no_mission", noAlternative, distanceMeters = 0.0))
        for (mode in listOf(TransportMode.WALKING, TransportMode.CYCLING, TransportMode.UNKNOWN)) {
            add(LocalFlowCase("no_lower_carbon_${mode.name}", noAlternative, originalMode = mode))
        }
        add(LocalFlowCase("no_available_alternative", noAlternative.copy(emissionsGrams = 960.0), availableModes = emptySet()))
        add(LocalFlowCase("negative_distance_rejected", ExpectedLocalFlow(null, missionMode = null, errorStage = "carbon"), distanceMeters = -1.0))
        add(LocalFlowCase("nonfinite_distance_rejected", ExpectedLocalFlow(null, missionMode = null, errorStage = "carbon"), distanceMeters = Double.NaN))
    }

    fun mixedWeek(): List<LocalFlowCase> = listOf(
        LocalFlowCase("mixed_short_walk", ExpectedLocalFlow(192.0, missionSavingGrams = 192.0, points = 39), distanceMeters = 1000.0),
        LocalFlowCase("mixed_walk", ExpectedLocalFlow(960.0)),
        LocalFlowCase("mixed_cycle", ExpectedLocalFlow(960.0, TransportMode.CYCLING, points = 111),
            availableModes = setOf(TransportMode.CYCLING), actualMode = TransportMode.CYCLING),
    )
}
