package com.ecostep.app.network.route

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit contract for the team's server-side Route proxy.
 *
 * The Firebase ID token authorises the signed-in EcoStep user.
 * The OpenRouteService API key never enters the Android application.
 */
internal interface RouteProxyApi {

    @POST("v1/route")
    suspend fun getRoute(
        @Header("Authorization") authorization: String,
        @Body request: RouteProxyRequest,
    ): OpenRouteServiceResponse
}
