package com.ecostep.app.ui.adapters

import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.ui.mock.AuthDataSource
import com.ecostep.app.ui.mock.AuthenticatedUser

class RepositoryAuthDataSource(private val auth: AuthRepository) : AuthDataSource {
    override suspend fun signIn(email: String, password: String): AuthenticatedUser {
        auth.signIn(email, password)
        return currentUser()
    }

    override suspend fun signUp(displayName: String, email: String, password: String): AuthenticatedUser {
        auth.signUp(email, password, displayName)
        return currentUser()
    }

    override suspend fun sendPasswordResetEmail(email: String) = auth.sendPasswordResetEmail(email)
    override suspend fun signOut() = auth.signOut()

    private fun currentUser() = AuthenticatedUser(
        userId = checkNotNull(auth.currentUserId) { "Authentication did not create a session." },
        displayName = auth.currentUserDisplayName.orEmpty(),
        email = auth.currentUserEmail.orEmpty(),
    )
}
