package com.ecostep.app.network.route

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.RouteInfo
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Supplies the Firebase ID token needed by the Route proxy.
 *
 * AppContainer will later adapt Zongcheng's AuthRepository to this small
 * interface after the authentication branch is merged.
 */
internal fun interface RouteAccessTokenProvider {
    suspend fun getIdToken(): String?
}

/**
 * Calls the team's Route proxy and maps its response into shared RouteInfo.
 */
internal class RouteProxyDataSource(
    private val routeProxyApi: RouteProxyApi,
    private val accessTokenProvider: RouteAccessTokenProvider,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) {

    suspend fun getRoute(
        start: GeoPoint,
        end: GeoPoint,
        profile: OpenRouteServiceProfile,
    ): RouteInfo {
        val idToken = accessTokenProvider.getIdToken()
            ?.takeIf { it.isNotBlank() }
            ?: throw RouteProxyAuthenticationException()

        val response = try {
            routeProxyApi.getRoute(
                authorization = "Bearer $idToken",
                request = RouteProxyRequest(
                    profile = profile.apiValue,
                    start = start,
                    end = end,
                ),
            )
        } catch (error: HttpException) {
            throw error.toRouteProxyServiceException()
        }

        return response.toRouteInfo(profile)
    }

    private fun HttpException.toRouteProxyServiceException():
            RouteProxyServiceException {
        val parsedError = response()
            ?.errorBody()
            ?.string()
            ?.let { errorBody ->
                runCatching {
                    json.decodeFromString(
                        RouteProxyErrorResponse.serializer(),
                        errorBody,
                    )
                }.getOrNull()
            }

        return RouteProxyServiceException(
            statusCode = code(),
            errorCode = parsedError?.error?.code,
            message = parsedError?.error?.message
                ?: "Route proxy returned HTTP ${code()}.",
            cause = this,
        )
    }
}
