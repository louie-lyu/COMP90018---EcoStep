package com.ecostep.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecostep.app.data.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
)

class LoginViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(isAuthenticated = authRepository.currentUserId != null),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    fun signIn() = authenticate { email, password ->
        authRepository.signIn(email, password)
    }

    fun signUp() = authenticate { email, password ->
        authRepository.signUp(email, password)
    }

    private fun authenticate(request: suspend (String, String) -> Unit) {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter an email and password.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                request(state.email, state.password)
                _uiState.update {
                    it.copy(isLoading = false, isAuthenticated = true)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "Authentication failed.",
                    )
                }
            }
        }
    }
}
