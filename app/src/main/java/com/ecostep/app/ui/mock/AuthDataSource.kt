package com.ecostep.app.ui.mock

data class AuthenticatedUser(
    val userId: String,
    val displayName: String,
    val email: String,
)

interface AuthDataSource {

    suspend fun signIn(
        email: String,
        password: String,
    ): AuthenticatedUser

    suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): AuthenticatedUser

    suspend fun sendPasswordResetEmail(
        email: String,
    )

    suspend fun signOut()
}

/**
 * Temporary authentication implementation used for the UI prototype.
 *
 * It does not contact Firebase, persist a session or store passwords.
 *
 * TODO(Auth/Firebase):
 * Replace this class with FirebaseAuthDataSource and provide it through
 * AppContainer. The Firebase implementation should support sign-in,
 * account creation, password-reset email, sign-out and current-user state.
 */
class MockAuthDataSource : AuthDataSource {

    override suspend fun signIn(
        email: String,
        password: String,
    ): AuthenticatedUser {
        return AuthenticatedUser(
            userId = "mock-user",
            displayName = "EcoStep User",
            email = email.trim(),
        )
    }

    override suspend fun signUp(
        displayName: String,
        email: String,
        password: String,
    ): AuthenticatedUser {
        return AuthenticatedUser(
            userId = "mock-user",
            displayName = displayName.trim(),
            email = email.trim(),
        )
    }

    override suspend fun sendPasswordResetEmail(
        email: String,
    ) {
        // Mock success. No email is sent in the prototype.
    }

    override suspend fun signOut() {
        // Mock success. No persisted session exists in the prototype.
    }
}