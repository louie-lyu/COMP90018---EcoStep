package com.ecostep.app.network.route

import kotlinx.serialization.Serializable

/**
 * Error body returned by the team's Route proxy.
 */
@Serializable
internal data class RouteProxyErrorResponse(
    val error: RouteProxyError,
)

@Serializable
internal data class RouteProxyError(
    val code: String,
    val message: String,
)

/**
 * Raised before a proxy request when no signed-in Firebase user token exists.
 */
internal class RouteProxyAuthenticationException : Exception(
    "A signed-in user is required to request routes.",
)

/**
 * Raised when the Route proxy returns an unsuccessful HTTP response.
 */
internal class RouteProxyServiceException(
    val statusCode: Int,
    val errorCode: String?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
