package com.ecostep.app.network.ai

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface AiWorkerApi {

    @POST("v1/ai")
    suspend fun generate(
        @Header("Authorization") authorization: String,
        @Body request: AiWorkerRequest,
    ): AiWorkerResponse
}

class AiWorkerClient(
    okHttpClient: OkHttpClient,
    private val tokenProvider: suspend () -> String = {
        FirebaseIdTokenProvider().getToken()
    },
) : AiGateway {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val api: AiWorkerApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(
            okHttpClient.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(35, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .addInterceptor { chain ->
                    val request = chain.request()
                        .newBuilder()
                        .header("User-Agent", "EcoStep/1.0")
                        .header("Accept", "application/json")
                        .build()

                    chain.proceed(request)
                }
                .build(),
        )
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType()),
        )
        .build()
        .create(AiWorkerApi::class.java)

    override suspend fun generateMission(
        prompt: String,
    ): AiMissionSuggestion {
        val result = requestResult(
            task = "mission",
            prompt = prompt,
        )

        return json.decodeFromJsonElement<AiMissionSuggestion>(result)
    }

    override suspend fun generateWeeklyAdvice(
        prompt: String,
    ): AiWeeklyAdvice {
        val result = requestResult(
            task = "weekly",
            prompt = prompt,
        )

        return json.decodeFromJsonElement<AiWeeklyAdvice>(result)
    }

    private suspend fun requestResult(
        task: String,
        prompt: String,
    ): JsonObject {
        require(prompt.isNotBlank() && prompt.length <= 10_000) {
            "AI prompt must contain between 1 and 10000 characters."
        }

        val token = tokenProvider()

        check(token.isNotBlank()) {
            "A Firebase login token is required."
        }

        val response = api.generate(
            authorization = "Bearer $token",
            request = AiWorkerRequest(
                task = task,
                prompt = prompt,
            ),
        )

        check(response.task == task) {
            "AI Worker returned a different task type."
        }

        return response.result
    }

    companion object {
        const val BASE_URL =
            "https://ecostep-ai-proxy.louie415520.workers.dev/"
    }
}