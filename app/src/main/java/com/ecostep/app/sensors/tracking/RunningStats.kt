package com.ecostep.app.sensors.tracking

import kotlin.math.sqrt

/** Welford's online population statistics. */
class RunningStats {
    var count: Long = 0
        private set
    private var average = 0.0
    private var squaredDeviations = 0.0

    fun add(value: Double) {
        if (!value.isFinite()) return
        count++
        val delta = value - average
        average += delta / count
        squaredDeviations += delta * (value - average)
    }

    fun mean(): Double = average
    fun std(): Double = if (count == 0L) 0.0 else sqrt(squaredDeviations / count)
}
