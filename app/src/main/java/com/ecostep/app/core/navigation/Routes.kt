package com.ecostep.app.core.navigation

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"

    const val JOURNEY_REVIEW = "journey_review"
    const val JOURNEY_ID_ARGUMENT = "journeyId"
    const val READ_ONLY_ARGUMENT = "readOnly"

    const val JOURNEY_REVIEW_WITH_ID =
        "$JOURNEY_REVIEW/{$JOURNEY_ID_ARGUMENT}" +
                "?$READ_ONLY_ARGUMENT={$READ_ONLY_ARGUMENT}"

    const val MISSIONS = "missions"
    const val WEEKLY_INSIGHT = "weekly_insight"
    const val REWARDS = "rewards"
    const val JOURNEY_HISTORY = "journey_history"
    const val PROFILE = "profile"

    /**
     * Opens Journey Review after a newly completed journey.
     *
     * The user can confirm the detected transport mode before saving.
     */
    fun journeyReview(
        journeyId: String,
        readOnly: Boolean = false,
    ): String {
        return "$JOURNEY_REVIEW/$journeyId" +
                "?$READ_ONLY_ARGUMENT=$readOnly"
    }

    /**
     * Opens an existing saved journey from Journey History.
     *
     * Existing records are displayed without editing or confirming again.
     */
    fun journeyHistoryDetail(
        journeyId: String,
    ): String {
        return journeyReview(
            journeyId = journeyId,
            readOnly = true,
        )
    }
}