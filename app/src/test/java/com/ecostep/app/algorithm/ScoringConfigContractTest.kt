package com.ecostep.app.algorithm

import com.ecostep.app.data.model.MissionResult
import com.ecostep.app.data.model.TransportMode
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The trusted backend computes carbon credit and EcoPoints from functions/src/config/scoring.json.
 * This test fails if the client's constants drift from that file.
 */
class ScoringConfigContractTest {

    private val config: JsonObject by lazy {
        // Gradle runs unit tests with the app module as working directory.
        val file = listOf(
            File("../functions/src/config/scoring.json"),
            File("functions/src/config/scoring.json"),
        ).first { it.exists() }
        Json.parseToJsonElement(file.readText()).jsonObject
    }

    @Test
    fun `emission factors match the backend`() {
        val backend = config.getValue("emissionFactorsGramsPerKm").jsonObject
            .mapValues { it.value.jsonPrimitive.double }

        assertEquals(
            EmissionFactors.GRAMS_PER_KM.mapKeys { it.key.name },
            backend,
        )
    }

    @Test
    fun `EcoPoints rules match the backend`() {
        val points = config.getValue("ecoPoints").jsonObject
        val bonuses = points.getValue("modeBonusPoints").jsonObject.mapValues { it.value.jsonPrimitive.int }

        assertEquals(DefaultEcoPointsCalculator.GRAMS_PER_POINT, points.getValue("gramsPerPoint").jsonPrimitive.double, 0.0)
        assertEquals(DefaultEcoPointsCalculator.MAX_POINTS, points.getValue("maxPoints").jsonPrimitive.int)
        assertEquals(
            DefaultEcoPointsCalculator.BONUS_FULL_DISTANCE_METERS,
            points.getValue("bonusFullDistanceMeters").jsonPrimitive.double,
            0.0,
        )
        assertEquals(DefaultEcoPointsCalculator.MODE_BONUS_POINTS.mapKeys { it.key.name }, bonuses)
    }

    @Test
    fun `shared fixtures give the same points as the backend unit tests`() {
        val calculator = DefaultEcoPointsCalculator()
        fun points(mode: TransportMode, grams: Double) = calculator.calculatePoints(
            MissionResult("m", accepted = true, completed = true, actualTransportMode = mode, actualCarbonSavingGrams = grams, timestampMillis = 0L),
        )

        // Same cases as functions/test/unit/scoring.test.ts.
        assertEquals(58, points(TransportMode.WALKING, 384.0))
        assertEquals(26, points(TransportMode.PUBLIC_TRANSPORT, 205.0))
        assertEquals(500, points(TransportMode.CYCLING, 100_000.0))
        assertEquals(20, points(TransportMode.WALKING, 96.0))

        fun walk(grams: Double, meters: Double) = calculator.calculatePoints(
            MissionResult(
                "m", accepted = true, completed = true, actualTransportMode = TransportMode.WALKING,
                actualCarbonSavingGrams = grams, timestampMillis = 0L, actualDistanceMeters = meters,
            ),
        )
        assertEquals(1, walk(4.992, 26.0))
        assertEquals(20, walk(96.0, 500.0))
        assertEquals(58, walk(384.0, 2_000.0))
    }
}
