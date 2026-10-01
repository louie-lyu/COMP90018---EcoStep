package com.ecostep.app.network.route

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Creates the Retrofit implementation for the team's Route proxy.
 */
internal object RouteProxyClient {

    fun create(
        okHttpClient: OkHttpClient,
        baseUrl: String,
    ): RouteProxyApi {
        val json = Json {
            ignoreUnknownKeys = true
        }

        val normalizedBaseUrl = if (baseUrl.endsWith("/")) {
            baseUrl
        } else {
            "$baseUrl/"
        }

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType(),
                ),
            )
            .build()
            .create(RouteProxyApi::class.java)
    }
}
