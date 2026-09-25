package com.ecostep.app.data.repository

/**
 * Stable caller-facing failure for external weather, route and public
 * transport data.
 *
 * Provider-specific exceptions remain available through [cause], while
 * callers can handle the small set of reasons without depending directly on
 * Retrofit, OkHttp or a particular external API.
 */
class ExternalDataException(
    val reason: Reason,
    message: String = reason.defaultMessage,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Reason(
        internal val defaultMessage: String,
    ) {
        NETWORK(
            "External data is unavailable because of a network problem.",
        ),
        SERVICE(
            "The external data service returned an unsuccessful response.",
        ),
        INVALID_RESPONSE(
            "The external data service returned an invalid response.",
        ),
        UNKNOWN(
            "External data is currently unavailable.",
        ),
    }
}