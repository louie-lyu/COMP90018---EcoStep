package com.ecostep.app.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecostep.app.core.di.ViewModelFactory
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.ui.adapters.RepositoryAuthDataSource
import com.ecostep.app.ui.screens.LoginScreen
import com.ecostep.app.ui.viewmodels.LoginViewModel

@Composable
fun LoginRoute(authRepository: AuthRepository, onLoginSuccess: () -> Unit) {
    val loginViewModel: LoginViewModel = viewModel(
        factory = ViewModelFactory { LoginViewModel(RepositoryAuthDataSource(authRepository)) },
    )
    LaunchedEffect(authRepository) {
        if (authRepository.currentUserId != null) onLoginSuccess()
    }
    LoginScreen(viewModel = loginViewModel, onAuthenticated = onLoginSuccess)
}
