package com.ecostep.app.evaluation

import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.data.model.*
import kotlin.math.max
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CarbonCorrectnessTest(private val name: String, private val verify: () -> Unit) {
    @Test fun matchesExpectedCarbon() = verify()

    companion object {
        private fun calculate(mode: TransportMode, distance: Double): CarbonResult =
            DefaultCarbonCalculator().calculate(JourneySummary("carbon_evaluation", "evaluation_user",
                GeoPoint(-37.81, 144.96), GeoPoint(-37.82, 144.97), 0L, 60_000L, distance, mode))

        private fun close(expected: Double, actual: Double) {
            assertTrue(actual.isFinite())
            assertEquals(expected, actual, max(1e-9, kotlin.math.abs(expected) * 1e-9))
        }

        private fun bounds(result: CarbonResult) {
            assertTrue(result.emissionsGrams.isFinite() && result.emissionsGrams >= 0)
            result.lowerCarbonAlternatives.forEach {
                assertTrue(it.savingsGrams.isFinite() && it.savingsGrams in 0.0..result.emissionsGrams)
            }
        }

        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList {
            fun case(name: String, verify: () -> Unit) { add(arrayOf(name, verify)) }
            fun reference(mode: TransportMode, distance: Double, emissions: Double,
                savings: Map<TransportMode, Double>) {
                case("reference_${mode}_$distance") {
                    val actual = calculate(mode, distance)
                    close(emissions, actual.emissionsGrams)
                    assertEquals(savings.size, actual.lowerCarbonAlternatives.size)
                    val alternatives = actual.lowerCarbonAlternatives.associate { it.mode to it.savingsGrams }
                    assertEquals(savings.keys, alternatives.keys)
                    savings.forEach { (mode, value) -> close(value, alternatives.getValue(mode)) }
                }
            }
            reference(TransportMode.WALKING, 10_000.0, 0.0, emptyMap())
            reference(TransportMode.CYCLING, 10_000.0, 0.0, emptyMap())
            reference(TransportMode.PUBLIC_TRANSPORT, 10_000.0, 890.0,
                mapOf(TransportMode.WALKING to 890.0, TransportMode.CYCLING to 890.0))
            reference(TransportMode.CAR, 10_000.0, 1920.0, mapOf(TransportMode.WALKING to 1920.0,
                TransportMode.CYCLING to 1920.0, TransportMode.PUBLIC_TRANSPORT to 1030.0))
            reference(TransportMode.UNKNOWN, 10_000.0, 0.0, emptyMap())
            reference(TransportMode.CAR, 1250.5, 240.096, mapOf(TransportMode.WALKING to 240.096,
                TransportMode.CYCLING to 240.096, TransportMode.PUBLIC_TRANSPORT to 128.8015))
            reference(TransportMode.PUBLIC_TRANSPORT, 1250.5, 111.2945,
                mapOf(TransportMode.WALKING to 111.2945, TransportMode.CYCLING to 111.2945))
            for (mode in TransportMode.entries) {
                case("zero_distance_$mode") {
                    val result = calculate(mode, 0.0)
                    close(0.0, result.emissionsGrams)
                    result.lowerCarbonAlternatives.forEach { close(0.0, it.savingsGrams) }
                }
                for (distance in listOf(-1.0, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)) {
                    case("invalid_distance_${mode}_$distance") {
                        assertThrows(IllegalArgumentException::class.java) { calculate(mode, distance) }
                    }
                }
            }
            for (mode in TransportMode.entries.filter { it != TransportMode.UNKNOWN }) {
                case("large_finite_distance_$mode") { bounds(calculate(mode, Double.MAX_VALUE)) }
                case("distance_scaling_$mode") {
                    val base = calculate(mode, 1250.5)
                    for (scale in listOf(2.0, 10.0)) {
                        val scaled = calculate(mode, 1250.5 * scale)
                        close(base.emissionsGrams * scale, scaled.emissionsGrams)
                        val baseSavings = base.lowerCarbonAlternatives.associate { it.mode to it.savingsGrams }
                        val scaledSavings = scaled.lowerCarbonAlternatives.associate { it.mode to it.savingsGrams }
                        assertEquals(baseSavings.keys, scaledSavings.keys)
                        baseSavings.forEach { (key, value) -> close(value * scale, scaledSavings.getValue(key)) }
                    }
                }
                for (distance in listOf(0.001, 1.0, 1250.5, 1_000_000_000.0)) {
                    case("savings_consistency_${mode}_$distance") {
                        val result = calculate(mode, distance)
                        bounds(result)
                        val alternatives = result.lowerCarbonAlternatives
                        assertEquals(alternatives.size, alternatives.map { it.mode }.distinct().size)
                        for (alternative in alternatives) {
                            assertTrue(alternative.mode != mode && alternative.mode != TransportMode.UNKNOWN)
                            val other = calculate(alternative.mode, distance)
                            assertTrue(other.emissionsGrams < result.emissionsGrams)
                            close(result.emissionsGrams - other.emissionsGrams, alternative.savingsGrams)
                        }
                    }
                }
            }
        }
    }
}
