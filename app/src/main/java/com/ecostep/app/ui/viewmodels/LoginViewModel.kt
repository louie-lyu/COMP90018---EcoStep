package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.ui.mock.AuthDataSource
import com.ecostep.app.ui.mock.AuthenticatedUser
import com.ecostep.app.ui.mock.MockAuthDataSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    SIGN_UP,
    FORGOT_PASSWORD,
}

data class LoginUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val confirmationMessage: String? = null,
)

sealed interface LoginEvent {

    data class AuthenticationSucceeded(
        val user: AuthenticatedUser,
    ) : LoginEvent
}

class LoginViewModel(
    private val authDataSource: AuthDataSource =
        MockAuthDataSource(),
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(LoginUiState())

    val uiState: StateFlow<LoginUiState> =
        _uiState.asStateFlow()

    private val _events =
        MutableSharedFlow<LoginEvent>()

    val events: SharedFlow<LoginEvent> =
        _events.asSharedFlow()

    fun updateDisplayName(value: String) {
        _uiState.update {
            it.copy(
                displayName = value,
                errorMessage = null,
            )
        }
    }

    fun updateEmail(value: String) {
        _uiState.update {
            it.copy(
                email = value,
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun updatePassword(value: String) {
        _uiState.update {
            it.copy(
                password = value,
                errorMessage = null,
            )
        }
    }

    fun updateConfirmPassword(value: String) {
        _uiState.update {
            it.copy(
                confirmPassword = value,
                errorMessage = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        _uiState.update {
            it.copy(
                isPasswordVisible =
                    !it.isPasswordVisible,
            )
        }
    }

    fun toggleConfirmPasswordVisibility() {
        _uiState.update {
            it.copy(
                isConfirmPasswordVisible =
                    !it.isConfirmPasswordVisible,
            )
        }
    }

    fun showSignIn() {
        changeMode(AuthMode.SIGN_IN)
    }

    fun showSignUp() {
        changeMode(AuthMode.SIGN_UP)
    }

    fun showForgotPassword() {
        changeMode(AuthMode.FORGOT_PASSWORD)
    }

    private fun changeMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                password = "",
                confirmPassword = "",
                isPasswordVisible = false,
                isConfirmPasswordVisible = false,
                isLoading = false,
                errorMessage = null,
                confirmationMessage = null,
            )
        }
    }

    fun submit() {
        when (_uiState.value.mode) {
            AuthMode.SIGN_IN ->
                signIn()

            AuthMode.SIGN_UP ->
                signUp()

            AuthMode.FORGOT_PASSWORD ->
                sendPasswordResetEmail()
        }
    }

    private fun signIn() {
        val currentState = _uiState.value
        val emailError = validateEmail(currentState.email)

        if (emailError != null) {
            showError(emailError)
            return
        }

        if (currentState.password.isBlank()) {
            showError("Enter your password.")
            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                val user = authDataSource.signIn(
                    email = currentState.email.trim(),
                    password = currentState.password,
                )

                setLoading(false)

                _events.emit(
                    LoginEvent.AuthenticationSucceeded(user),
                )
            } catch (exception: Exception) {
                showError(
                    exception.message
                        ?: "Unable to sign in. Please try again.",
                )
            }
        }
    }

    private fun signUp() {
        val currentState = _uiState.value

        if (currentState.displayName.isBlank()) {
            showError("Enter your name.")
            return
        }

        val emailError = validateEmail(currentState.email)

        if (emailError != null) {
            showError(emailError)
            return
        }

        if (currentState.password.length < 6) {
            showError(
                "Password must contain at least 6 characters.",
            )
            return
        }

        if (
            currentState.password !=
            currentState.confirmPassword
        ) {
            showError("Passwords do not match.")
            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                val user = authDataSource.signUp(
                    displayName =
                        currentState.displayName.trim(),
                    email = currentState.email.trim(),
                    password = currentState.password,
                )

                setLoading(false)

                _events.emit(
                    LoginEvent.AuthenticationSucceeded(user),
                )
            } catch (exception: Exception) {
                showError(
                    exception.message
                        ?: "Unable to create your account.",
                )
            }
        }
    }

    private fun sendPasswordResetEmail() {
        val currentState = _uiState.value
        val emailError = validateEmail(currentState.email)

        if (emailError != null) {
            showError(emailError)
            return
        }

        viewModelScope.launch {
            setLoading(true)

            try {
                authDataSource.sendPasswordResetEmail(
                    email = currentState.email.trim(),
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        confirmationMessage =
                            "Password reset instructions have been sent.",
                    )
                }
            } catch (exception: Exception) {
                showError(
                    exception.message
                        ?: "Unable to send the reset email.",
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update {
            it.copy(errorMessage = null)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        _uiState.update {
            it.copy(
                isLoading = isLoading,
                errorMessage = null,
            )
        }
    }

    private fun showError(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
                confirmationMessage = null,
            )
        }
    }

    private fun validateEmail(email: String): String? {
        val trimmedEmail = email.trim()

        return when {
            trimmedEmail.isBlank() ->
                "Enter your email address."

            !android.util.Patterns.EMAIL_ADDRESS
                .matcher(trimmedEmail)
                .matches() ->
                "Enter a valid email address."

            else -> null
        }
    }
}