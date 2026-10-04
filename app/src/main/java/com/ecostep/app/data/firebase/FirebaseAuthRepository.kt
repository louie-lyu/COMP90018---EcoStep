package com.ecostep.app.data.firebase

import com.ecostep.app.data.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.CancellationException

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    override val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    override val currentUserEmail: String?
        get() = firebaseAuth.currentUser?.email

    override suspend fun signIn(email: String, password: String) {
        runAuthRequest {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
        }
    }

    override suspend fun signUp(email: String, password: String) {
        runAuthRequest {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    override suspend fun getIdToken(forceRefresh: Boolean): String? {
        val user = firebaseAuth.currentUser ?: return null
        return user.getIdToken(forceRefresh).await().token
    }

    private suspend fun runAuthRequest(block: suspend () -> Unit) {
        try {
            block()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw IllegalStateException(exception.toUserMessage(), exception)
        }
    }
}

private fun Exception.toUserMessage(): String = when (this) {
    is FirebaseAuthWeakPasswordException -> "Password must be at least 6 characters."
    is FirebaseAuthUserCollisionException -> "An account already exists for this email."
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> "Email or password is incorrect."
    is FirebaseNetworkException -> "Network unavailable. Please try again."
    else -> "Authentication failed. Please try again."
}
