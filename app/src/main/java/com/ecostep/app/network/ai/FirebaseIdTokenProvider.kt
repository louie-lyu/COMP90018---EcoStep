package com.ecostep.app.network.ai

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseIdTokenProvider {

    suspend fun getToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException(
                "Please sign in before requesting AI advice.",
            )

        return suspendCancellableCoroutine { continuation ->
            // Firebase automatically refreshes an expired cached token.
            user.getIdToken(false).addOnCompleteListener { task ->
                if (continuation.isActive) {
                    if (task.isSuccessful) {
                        val token = task.result?.token

                        if (token.isNullOrBlank()) {
                            continuation.resumeWithException(
                                IllegalStateException(
                                    "Firebase returned an empty login token.",
                                ),
                            )
                        } else {
                            continuation.resume(token)
                        }
                    } else {
                        continuation.resumeWithException(
                            task.exception ?: IllegalStateException(
                                "Could not obtain a Firebase login token.",
                            ),
                        )
                    }
                }
            }
        }
    }
}