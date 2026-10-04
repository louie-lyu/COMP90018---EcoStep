package com.ecostep.app.data.firebase

import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.UserProfile
import com.ecostep.app.data.model.normalizeDisplayName
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.ProfileRepository
import com.ecostep.app.data.repository.WriteOutcome
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreProfileRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
    private val ackTimeoutMillis: Long = DEFAULT_ACK_TIMEOUT_MILLIS,
) : ProfileRepository {

    override fun observeProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = requireCurrentUserId()
        val listener = profile(uid).addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                close(exception)
                return@addSnapshotListener
            }
            trySend(snapshot?.data?.toUserProfile(uid))
        }
        awaitClose { listener.remove() }
    }

    override suspend fun getProfile(): UserProfile? {
        val uid = requireCurrentUserId()
        return profile(uid).get().await().data?.toUserProfile(uid)
    }

    override suspend fun ensureProfile(defaultDisplayName: String): Boolean {
        val uid = requireCurrentUserId()
        val displayName = runCatching { normalizeDisplayName(defaultDisplayName) }
            .getOrDefault(DEFAULT_DISPLAY_NAME)
        val document = profile(uid)
        // A transaction reads the server copy, so an existing profile (e.g. from another
        // device) is never replaced by defaults. It needs the network; callers retry later.
        return firestore.runTransaction { transaction ->
            if (transaction.get(document).exists()) {
                false
            } else {
                transaction.set(document, newProfileDocument(displayName, FieldValue.serverTimestamp()))
                true
            }
        }.await()
    }

    override suspend fun updateDisplayName(displayName: String): WriteOutcome {
        val uid = requireCurrentUserId()
        val name = normalizeDisplayName(displayName)
        return profile(uid)
            .set(
                mapOf(
                    "displayName" to name,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .awaitWriteOutcome(ackTimeoutMillis)
    }

    override suspend fun updatePreference(update: PreferenceUpdate): WriteOutcome {
        val uid = requireCurrentUserId()
        // Merge-set writes only preferences.<field>, so concurrent edits of different
        // preferences on two devices both survive; the same field is last-write-wins.
        return profile(uid)
            .set(preferenceMergeDocument(update, FieldValue.serverTimestamp()), SetOptions.merge())
            .awaitWriteOutcome(ackTimeoutMillis)
    }

    private fun requireCurrentUserId(): String =
        authRepository.currentUserId
            ?: throw IllegalStateException("You must be signed in to access your profile.")

    private fun profile(uid: String) = firestore.collection("users").document(uid)
}
