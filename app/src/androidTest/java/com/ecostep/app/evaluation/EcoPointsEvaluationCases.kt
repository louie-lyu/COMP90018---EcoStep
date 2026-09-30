package com.ecostep.app.evaluation

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode

internal data class EcoPointsCase(
    val scenario: String,
    val group: String,
    val input: MissionResult,
    val expectedPoints: Int? = null,
    val expectedError: String? = null,
    val journey: JourneySummary? = null,
)

/** Hand-calculated expectations, independent of the calculator's constants. */
internal object EcoPointsEvaluationCases {
    fun cases(): List<EcoPointsCase> = buildList {
        val bonuses = listOf(
            TransportMode.WALKING to 20,
            TransportMode.CYCLING to 15,
            TransportMode.PUBLIC_TRANSPORT to 5,
            TransportMode.CAR to 0,
            TransportMode.UNKNOWN to 0,
        )
        for ((mode, bonus) in bonuses) {
            fun addPoints(group: String, savings: Double, expected: Int) {
                add(EcoPointsCase("${group}_${mode.name}_$savings", group, mission(mode, savings), expected))
            }
            addPoints("reference", 100.0, 10 + bonus)
            addPoints("zero", 0.0, 0)
            addPoints("zero", -0.0, 0)
            for ((savings, base) in listOf(0.001 to 0, 4.9 to 0, 5.0 to 1, 5.1 to 1, 14.9 to 1, 15.0 to 2)) {
                addPoints("rounding", savings, base + bonus)
            }
            // The first rounded base value that reaches the cap is 500 minus the bonus.
            val boundary = (500 - bonus) * 10.0 - 5.0
            addPoints("cap", boundary - 0.1, 499)
            addPoints("cap", boundary, 500)
            addPoints("cap", boundary + 0.1, 500)
            addPoints("cap", 10_000.0, 500)
            for (savings in listOf(-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
                add(EcoPointsCase("invalid_${mode.name}_$savings", "invalid", mission(mode, savings),
                    expectedError = "IllegalArgumentException"))
            }
        }
        for (accepted in listOf(false, true)) {
            for (completed in listOf(false, true)) {
                add(EcoPointsCase("state_${accepted}_$completed", "state",
                    mission().copy(accepted = accepted, completed = completed),
                    if (accepted && completed) 25 else 0))
            }
        }
        add(EcoPointsCase("missing_savings", "missing", mission().copy(actualCarbonSavingGrams = null), 0))
        add(EcoPointsCase("missing_mode", "missing", mission().copy(actualTransportMode = null), 0))
        // Inactive missions return before validating savings.
        add(EcoPointsCase("unaccepted_invalid_savings", "state", mission(savings = Double.NaN).copy(accepted = false), 0))
        add(EcoPointsCase("incomplete_invalid_savings", "state", mission(savings = -1.0).copy(completed = false), 0))

        for ((distance, references) in listOf(
            0.0 to listOf(Triple(TransportMode.WALKING, 0.0, 0), Triple(TransportMode.CYCLING, 0.0, 0),
                Triple(TransportMode.PUBLIC_TRANSPORT, 0.0, 0)),
            1000.0 to listOf(Triple(TransportMode.WALKING, 192.0, 39), Triple(TransportMode.CYCLING, 192.0, 34),
                Triple(TransportMode.PUBLIC_TRANSPORT, 103.0, 15)),
            10_000.0 to listOf(Triple(TransportMode.WALKING, 1920.0, 212), Triple(TransportMode.CYCLING, 1920.0, 207),
                Triple(TransportMode.PUBLIC_TRANSPORT, 1030.0, 108)),
            100_000.0 to listOf(Triple(TransportMode.WALKING, 19_200.0, 500), Triple(TransportMode.CYCLING, 19_200.0, 500),
                Triple(TransportMode.PUBLIC_TRANSPORT, 10_300.0, 500)),
        )) {
            for ((mode, savings, points) in references) {
                add(EcoPointsCase("carbon_to_points_${mode.name}_$distance", "carbon_to_points",
                    mission(mode, savings), points, journey = journey(distance)))
            }
        }
    }

    private fun mission(mode: TransportMode = TransportMode.CYCLING, savings: Double = 100.0) = MissionResult(
        missionId = "ecopoints_evaluation", accepted = true, completed = true,
        actualTransportMode = mode, actualCarbonSavingGrams = savings, timestampMillis = 0L,
    )

    private fun journey(distance: Double) = JourneySummary(
        journeyId = "ecopoints_evaluation", userId = "evaluation_user",
        startLocation = GeoPoint(0.0, 0.0), endLocation = GeoPoint(0.0, 0.02),
        startTimeMillis = 0L, endTimeMillis = 60_000L,
        distanceMeters = distance, transportMode = TransportMode.CAR,
    )
}
