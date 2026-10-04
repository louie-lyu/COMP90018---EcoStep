package com.ecostep.app.testing

import com.ecostep.app.data.repository.AuthRepository

class FakeAuthRepository(
    override var currentUserId: String? = "user-a",
    override var currentUserEmail: String? = "a@example.com",
) : AuthRepository {
    override suspend fun signIn(email: String, password: String) {
        currentUserId = "uid-$email"
        currentUserEmail = email
    }

    override suspend fun signUp(email: String, password: String) = signIn(email, password)

    override fun signOut() {
        currentUserId = null
        currentUserEmail = null
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? =
        currentUserId?.let { "token-$it" }
}
