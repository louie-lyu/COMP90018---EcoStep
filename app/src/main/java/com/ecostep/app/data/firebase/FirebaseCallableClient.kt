package com.ecostep.app.data.firebase

import com.ecostep.app.data.repository.BackendException
import com.ecostep.app.data.repository.CloudFunctionsClient
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException

/**
 * Callable HTTPS functions. The Firebase SDK attaches the signed-in user's ID token, which
 * the backend verifies before doing anything.
 */
class FirebaseCallableClient(
    private val functions: FirebaseFunctions,
) : CloudFunctionsClient {

    override suspend fun call(name: String, data: Map<String, Any?>): Any? {
        try {
            return functions.getHttpsCallable(name).call(data).await().getData()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: FirebaseFunctionsException) {
            throw BackendException(
                message = exception.toUserMessage(),
                code = exception.code.name,
                cause = exception,
            )
        }
    }
}

private fun FirebaseFunctionsException.toUserMessage(): String = when (code) {
    // These codes carry messages written for users by the backend.
    FirebaseFunctionsException.Code.INVALID_ARGUMENT,
    FirebaseFunctionsException.Code.FAILED_PRECONDITION,
    FirebaseFunctionsException.Code.NOT_FOUND,
    FirebaseFunctionsException.Code.ALREADY_EXISTS,
    FirebaseFunctionsException.Code.PERMISSION_DENIED,
    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED,
    -> message ?: "The request was rejected."
    FirebaseFunctionsException.Code.UNAUTHENTICATED -> "Please sign in again."
    FirebaseFunctionsException.Code.UNAVAILABLE,
    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
    -> "Network unavailable. Please try again."
    else -> "Something went wrong. Please try again."
}
