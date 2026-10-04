package com.ecostep.app.data.firebase

import android.util.Log
import com.ecostep.app.data.repository.WriteOutcome
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Suspends until the task completes. Cancelling the coroutine stops waiting but cannot
 * cancel the underlying Firebase operation (a queued Firestore write still syncs later).
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        val exception = task.exception
        when {
            task.isCanceled -> continuation.cancel()
            task.isSuccessful -> continuation.resume(task.result)
            exception != null -> continuation.resumeWithException(exception)
            else -> continuation.resumeWithException(
                IllegalStateException("Firebase request failed."),
            )
        }
    }
}

/**
 * Firestore applies a write to the local cache immediately, but its Task only completes once
 * the server acknowledges it, which never happens while offline. Waiting a short time tells
 * the two cases apart without reporting a queued write as a failure or as synced.
 */
internal suspend fun Task<Void>.awaitWriteOutcome(
    ackTimeoutMillis: Long = DEFAULT_ACK_TIMEOUT_MILLIS,
): WriteOutcome {
    addOnFailureListener { exception ->
        // Rejections of queued writes arrive after the caller stopped waiting. Log the type
        // only: messages can contain document paths.
        Log.w(TAG, "Firestore write rejected: ${exception.javaClass.simpleName}")
    }
    val acknowledged = withTimeoutOrNull(ackTimeoutMillis) {
        await()
        true
    }
    return if (acknowledged == true) WriteOutcome.SYNCED else WriteOutcome.QUEUED
}

internal const val DEFAULT_ACK_TIMEOUT_MILLIS = 2_000L
private const val TAG = "FirestoreWrite"
