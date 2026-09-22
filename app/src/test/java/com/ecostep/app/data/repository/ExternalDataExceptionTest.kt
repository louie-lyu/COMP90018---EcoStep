package com.ecostep.app.data.repository

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalDataExceptionTest {

    @Test
    fun `network failure preserves its stable reason and original cause`() {
        val originalCause = IOException("Network unavailable")

        val exception = ExternalDataException(
            reason = ExternalDataException.Reason.NETWORK,
            cause = originalCause,
        )

        assertEquals(
            ExternalDataException.Reason.NETWORK,
            exception.reason,
        )
        assertSame(originalCause, exception.cause)
        assertTrue(exception.message.orEmpty().isNotBlank())
    }

    @Test
    fun `custom message can be provided without losing the original cause`() {
        val originalCause = IllegalStateException("Provider failed")

        val exception = ExternalDataException(
            reason = ExternalDataException.Reason.SERVICE,
            message = "Weather service is temporarily unavailable.",
            cause = originalCause,
        )

        assertEquals(
            ExternalDataException.Reason.SERVICE,
            exception.reason,
        )
        assertEquals(
            "Weather service is temporarily unavailable.",
            exception.message,
        )
        assertSame(originalCause, exception.cause)
    }

    @Test
    fun `each reason provides a non-empty safe default message`() {
        ExternalDataException.Reason.entries.forEach { reason ->
            val exception = ExternalDataException(reason = reason)

            assertTrue(
                "Expected a default message for $reason",
                exception.message.orEmpty().isNotBlank(),
            )
        }
    }
}