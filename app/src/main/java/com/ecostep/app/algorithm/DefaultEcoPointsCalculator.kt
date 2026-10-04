package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import kotlin.math.roundToInt

/**
 * points = min(round(saving / 10 g) + round(modeBonus × min(1, distance / 1000 m)), 500)
 *
 * The transport-mode bonus grows with the distance travelled, so a few metres on foot no
 * longer earns the same bonus as a full trip.
 */
class DefaultEcoPointsCalculator(
    private val emissionFactorsGramsPerKm: Map<TransportMode, Double> =
        EmissionFactors.GRAMS_PER_KM,
) : EcoPointsCalculator {

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

        missionResult.actualDistanceMeters?.let { distance ->
            require(distance.isFinite() && distance >= 0.0) {
                "Actual distance must be finite and non-negative."
            }
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
            ((MODE_BONUS_POINTS[transportMode] ?: 0) *
                bonusShare(missionResult.actualDistanceMeters, carbonSaving, transportMode))
                .roundToInt()

        return (basePoints + bonusPoints)
            .coerceAtMost(MAX_POINTS)
    }

    /** Fraction of the mode bonus earned: the distance travelled over [BONUS_FULL_DISTANCE_METERS]. */
    private fun bonusShare(
        distanceMeters: Double?,
        carbonSavingGrams: Double,
        mode: TransportMode,
    ): Double {
        val distance = distanceMeters ?: distanceFromSaving(carbonSavingGrams, mode)
        return (distance / BONUS_FULL_DISTANCE_METERS).coerceAtMost(1.0)
    }

    /** saving = km × (car factor − mode factor), so the distance follows from the saving. */
    private fun distanceFromSaving(carbonSavingGrams: Double, mode: TransportMode): Double {
        val car = emissionFactorsGramsPerKm[TransportMode.CAR] ?: return 0.0
        val modeFactor = emissionFactorsGramsPerKm[mode] ?: return 0.0
        val savingPerKm = car - modeFactor
        return if (savingPerKm > 0.0) carbonSavingGrams / savingPerKm * 1000.0 else 0.0
    }

    /**
     * Scoring rules shared with the trusted backend (`functions/src/config/scoring.json`);
     * `ScoringConfigContractTest` keeps both copies identical.
     */
    companion object {
        const val GRAMS_PER_POINT = 10.0
        const val MAX_POINTS = 500

        /** The full mode bonus is earned from this distance on; shorter trips earn a share. */
        const val BONUS_FULL_DISTANCE_METERS = 1_000.0

        val MODE_BONUS_POINTS: Map<TransportMode, Int> =
            mapOf(
                TransportMode.WALKING to 20,
                TransportMode.CYCLING to 15,
                TransportMode.PUBLIC_TRANSPORT to 5,
                TransportMode.CAR to 0,
                TransportMode.UNKNOWN to 0,
            )
    }
}
