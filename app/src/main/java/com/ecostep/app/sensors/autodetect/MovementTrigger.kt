package com.ecostep.app.sensors.autodetect

/** Movement reported by Activity Recognition, reduced to what the trigger needs. */
enum class DetectedMovement {
    WALKING,
    CYCLING,
    IN_VEHICLE,
    STILL_OR_OTHER,
}

/**
 * Decides when sustained movement justifies starting a recording. A single reading never
 * triggers: it needs [requiredEvents] consecutive confident movement readings spanning at
 * least [minimumMovingMillis]. Any still or low-confidence reading starts over.
 */
class MovementTrigger(
    private val minimumConfidence: Int = 75,
    private val requiredEvents: Int = 2,
    private val minimumMovingMillis: Long = 20_000L,
) {
    private var firstMovingAtMillis: Long? = null
    private var movingEvents = 0

    /** Returns true exactly when recording should start; the trigger then resets. */
    fun onActivity(movement: DetectedMovement, confidence: Int, atMillis: Long): Boolean {
        if (movement == DetectedMovement.STILL_OR_OTHER || confidence < minimumConfidence) {
            reset()
            return false
        }
        val firstAt = firstMovingAtMillis ?: atMillis.also { firstMovingAtMillis = it }
        movingEvents++
        if (movingEvents >= requiredEvents && atMillis - firstAt >= minimumMovingMillis) {
            reset()
            return true
        }
        return false
    }

    fun reset() {
        firstMovingAtMillis = null
        movingEvents = 0
    }
}
