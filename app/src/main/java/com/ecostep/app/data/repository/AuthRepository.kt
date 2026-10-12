package com.ecostep.app.data.repository

interface AuthRepository {
    val currentUserId: String?

    /** Email of the signed-in user; Firebase Auth is its only source of truth. */
    val currentUserEmail: String?
        get() = null

    suspend fun signIn(email: String, password: String)

    suspend fun signUp(email: String, password: String)

    val currentUserDisplayName: String?
        get() = null

    suspend fun signUp(email: String, password: String, displayName: String) {
        signUp(email, password)
    }

    suspend fun sendPasswordResetEmail(email: String) {
        throw UnsupportedOperationException("Password reset is unavailable.")
    }

    fun signOut()

    /**
     * Firebase ID token for calling the team's own backend (e.g. the route proxy),
     * sent as `Authorization: Bearer <token>`. Returns null when signed out.
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): String?
}
