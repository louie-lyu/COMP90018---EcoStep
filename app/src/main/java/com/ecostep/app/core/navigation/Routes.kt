package com.ecostep.app.core.navigation

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    const val JOURNEY_REVIEW = "journey_review"
    const val JOURNEY_ID_ARGUMENT = "journeyId"
    const val JOURNEY_REVIEW_WITH_ID =
        "$JOURNEY_REVIEW/{$JOURNEY_ID_ARGUMENT}"

    const val MISSIONS = "missions"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun journeyReview(journeyId: String): String {
        return "$JOURNEY_REVIEW/$journeyId"
    }
}