package com.ecostep.app.network.route

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteProxyErrorResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `proxy error response parses code and message`() {
        val rawJson = """
            {
              "error": {
                "code": "RATE_LIMITED",
                "message": "Route service quota exceeded. Try again later.",
                "providerDetail": "ignored"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString(
            RouteProxyErrorResponse.serializer(),
            rawJson,
        )

        assertEquals(
            "RATE_LIMITED",
            response.error.code,
        )
        assertEquals(
            "Route service quota exceeded. Try again later.",
            response.error.message,
        )
    }
}
