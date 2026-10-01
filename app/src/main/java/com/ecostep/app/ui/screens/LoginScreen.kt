package com.ecostep.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ecostep.app.ui.viewmodels.AuthMode
import com.ecostep.app.ui.viewmodels.LoginEvent
import com.ecostep.app.ui.viewmodels.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onAuthenticated: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LoginEvent.AuthenticationSucceeded -> {
                    onAuthenticated()
                }
            }
        }
    }

    val title = when (uiState.mode) {
        AuthMode.SIGN_IN ->
            "Welcome back"

        AuthMode.SIGN_UP ->
            "Create your account"

        AuthMode.FORGOT_PASSWORD ->
            "Reset your password"
    }

    val description = when (uiState.mode) {
        AuthMode.SIGN_IN ->
            "Sign in to continue your sustainable journey."

        AuthMode.SIGN_UP ->
            "Join EcoStep and turn greener journeys into rewards."

        AuthMode.FORGOT_PASSWORD ->
            "Enter your email and we’ll send you reset instructions."
    }

    val primaryButtonLabel = when (uiState.mode) {
        AuthMode.SIGN_IN ->
            "Sign in"

        AuthMode.SIGN_UP ->
            "Create account"

        AuthMode.FORGOT_PASSWORD ->
            "Send reset email"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    horizontal = 24.dp,
                    vertical = 32.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            EcoStepLogo()

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "EcoStep",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Every journey makes a difference",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(36.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.mode == AuthMode.SIGN_UP) {
                    OutlinedTextField(
                        value = uiState.displayName,
                        onValueChange =
                            viewModel::updateDisplayName,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Name")
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(
                            capitalization =
                                androidx.compose.ui.text.input
                                    .KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                focusManager.moveFocus(
                                    FocusDirection.Down,
                                )
                            },
                        ),
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = viewModel::updateEmail,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Email")
                    },
                    placeholder = {
                        Text("name@example.com")
                    },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction =
                            if (
                                uiState.mode ==
                                AuthMode.FORGOT_PASSWORD
                            ) {
                                ImeAction.Done
                            } else {
                                ImeAction.Next
                            },
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            focusManager.moveFocus(
                                FocusDirection.Down,
                            )
                        },
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.submit()
                        },
                    ),
                )

                if (
                    uiState.mode == AuthMode.SIGN_IN ||
                    uiState.mode == AuthMode.SIGN_UP
                ) {
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange =
                            viewModel::updatePassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Password")
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        visualTransformation =
                            if (uiState.isPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        trailingIcon = {
                            TextButton(
                                onClick =
                                    viewModel::togglePasswordVisibility,
                            ) {
                                Text(
                                    if (uiState.isPasswordVisible) {
                                        "Hide"
                                    } else {
                                        "Show"
                                    },
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType =
                                KeyboardType.Password,
                            imeAction =
                                if (
                                    uiState.mode ==
                                    AuthMode.SIGN_UP
                                ) {
                                    ImeAction.Next
                                } else {
                                    ImeAction.Done
                                },
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = {
                                focusManager.moveFocus(
                                    FocusDirection.Down,
                                )
                            },
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.submit()
                            },
                        ),
                    )
                }

                if (uiState.mode == AuthMode.SIGN_UP) {
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = uiState.confirmPassword,
                        onValueChange =
                            viewModel::updateConfirmPassword,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Confirm password")
                        },
                        supportingText = {
                            if (uiState.mode == AuthMode.SIGN_UP) {
                                Text("Use at least 6 characters.")
                            }
                        },
                        singleLine = true,
                        enabled = !uiState.isLoading,
                        visualTransformation =
                            if (
                                uiState.isConfirmPasswordVisible
                            ) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        trailingIcon = {
                            TextButton(
                                onClick =
                                    viewModel::
                                    toggleConfirmPasswordVisibility,
                            ) {
                                Text(
                                    if (
                                        uiState
                                            .isConfirmPasswordVisible
                                    ) {
                                        "Hide"
                                    } else {
                                        "Show"
                                    },
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType =
                                KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.submit()
                            },
                        ),
                    )
                }

                if (uiState.mode == AuthMode.SIGN_IN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick =
                                viewModel::showForgotPassword,
                            enabled = !uiState.isLoading,
                        ) {
                            Text("Forgot password?")
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(18.dp))
                }

                uiState.errorMessage?.let { message ->
                    MessageSurface(
                        message = message,
                        isError = true,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                uiState.confirmationMessage?.let { message ->
                    MessageSurface(
                        message = message,
                        isError = false,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.submit()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !uiState.isLoading,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color =
                                MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = primaryButtonLabel,
                            style =
                                MaterialTheme.typography.titleMedium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                when (uiState.mode) {
                    AuthMode.SIGN_IN -> {
                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "New to EcoStep?",
                            modifier =
                                Modifier.align(Alignment.CenterHorizontally),
                            style = MaterialTheme.typography.bodyLarge,
                            color =
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = viewModel::showSignUp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(
                                text = "Create an account",
                                style =
                                    MaterialTheme.typography.titleMedium,
                            )
                        }
                    }

                    AuthMode.SIGN_UP -> {
                        AuthSwitchRow(
                            prompt = "Already have an account?",
                            actionLabel = "Sign in",
                            onClick = viewModel::showSignIn,
                        )
                    }

                    AuthMode.FORGOT_PASSWORD -> {
                        AuthSwitchRow(
                            prompt = "Remember your password?",
                            actionLabel = "Back to sign in",
                            onClick = viewModel::showSignIn,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text =
                    "By continuing, you agree to EcoStep’s Terms and Privacy Policy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EcoStepLogo() {
    Box(
        modifier = Modifier
            .size(76.dp)
            .background(
                color =
                    MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "E",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color =
                MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun MessageSurface(
    message: String,
    isError: Boolean,
) {
    val containerColor =
        if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }

    val contentColor =
        if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AuthSwitchRow(
    prompt: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prompt,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = onClick) {
            Text(actionLabel)
        }
    }
}