package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import kotlin.math.roundToInt

class DefaultEcoPointsCalculator : EcoPointsCalculator {

    override fun calculatePoints(
        missionResult: MissionResult,
    ): Int {
        if (!missionResult.accepted || !missionResult.completed) {
            return 0
        }

        val carbonSaving =
            missionResult.actualCarbonSavingGrams
                ?: return 0

        require(
            carbonSaving.isFinite() &&
                    carbonSaving >= 0.0,
        ) {
            "Actual carbon saving must be finite and non-negative."
        }

        if (carbonSaving == 0.0) {
            return 0
        }

        val transportMode =
            missionResult.actualTransportMode
                ?: return 0

        val basePoints =
            (carbonSaving / GRAMS_PER_POINT)
                .roundToInt()

        val bonusPoints =
            when (transportMode) {
                TransportMode.WALKING -> 20
                TransportMode.CYCLING -> 15
                TransportMode.PUBLIC_TRANSPORT -> 5
                TransportMode.CAR -> 0
                TransportMode.UNKNOWN -> 0
            }

        return (basePoints + bonusPoints)
            .coerceAtMost(MAX_POINTS)
    }

    private companion object {
        const val GRAMS_PER_POINT = 10.0
        const val MAX_POINTS = 500
    }
}