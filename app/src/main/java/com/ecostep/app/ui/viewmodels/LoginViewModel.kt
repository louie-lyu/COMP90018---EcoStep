package com.ecostep.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.model.normalizeDisplayName
import com.ecostep.app.ui.mock.AuthDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val isAuthenticated: Boolean = false,
)


class LoginViewModel(
    private val authDataSource: AuthDataSource,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(LoginUiState())

    val uiState: StateFlow<LoginUiState> =
        _uiState.asStateFlow()

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
        if (_uiState.value.isLoading) return
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
        if (_uiState.value.isLoading || _uiState.value.isAuthenticated) return
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

        setLoading(true)
        viewModelScope.launch {
            try {
                authDataSource.signIn(
                    email = currentState.email.trim(),
                    password = currentState.password,
                )

                authenticationSucceeded()
            } catch (exception: CancellationException) {
                throw exception
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

        try {
            normalizeDisplayName(currentState.displayName)
        } catch (exception: IllegalArgumentException) {
            showError(exception.message ?: "Enter your name.")
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

        setLoading(true)
        viewModelScope.launch {
            try {
                authDataSource.signUp(
                    displayName =
                        currentState.displayName.trim(),
                    email = currentState.email.trim(),
                    password = currentState.password,
                )

                authenticationSucceeded()
            } catch (exception: CancellationException) {
                throw exception
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

        setLoading(true)
        viewModelScope.launch {
            try {
                authDataSource.sendPasswordResetEmail(
                    email = currentState.email.trim(),
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        confirmationMessage =
                            "If an account exists for this email, password reset instructions will be sent.",
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
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

    private fun authenticationSucceeded() {
        _uiState.update {
            it.copy(isLoading = false, isAuthenticated = true, password = "", confirmPassword = "")
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
