package com.ecostep.app.network.publictransport

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/**
 * Creates the Retrofit implementation for Transitous.
 */
object TransitousClient {

    fun create(
        okHttpClient: OkHttpClient,
    ): TransitousApi {
        val json = Json {
            ignoreUnknownKeys = true
        }

        return Retrofit.Builder()
            .baseUrl(TransitousApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType(),
                ),
            )
            .build()
            .create(TransitousApi::class.java)
    }
}
