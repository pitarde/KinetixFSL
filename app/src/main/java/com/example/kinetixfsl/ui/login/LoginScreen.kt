package com.example.kinetixfsl.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.R
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixPrimaryButton


/**
 * The login screen. Email/password sign-in and Google Sign-In (via Credential
 * Manager) both go through the shared [LoginViewModel]. On success,
 * [onLoginSuccess] fires.
 *
 * @param onLoginSuccess       navigate to Home
 * @param onNavigateToSignUp   navigate to the register screen
 * @param onForgotPassword     navigate to password reset (not-yet-built)
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Credential Manager needs an Activity context to render its account sheet.
    val context = LocalContext.current

    // When the ViewModel reports success, tell the caller once, then reset the flag.
    if (state.isLoginSuccessful) {
        onLoginSuccess()
        viewModel.onLoginHandled()
    }

    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onToggleVisibility = viewModel::togglePasswordVisibility,
        onLoginClick = viewModel::login,
        onForgotPassword = onForgotPassword,
        onGoogleClick = { viewModel.signInWithGoogle(context) },
        onSignUpClick = onNavigateToSignUp,
        modifier = modifier,
    )
}

/**
 * The stateless UI. Split from [LoginScreen] so the @Preview can render it with
 * fake state and no Firebase.
 */
@Composable
private fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onLoginClick: () -> Unit,
    onForgotPassword: () -> Unit,
    onGoogleClick: () -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "Welcome Back!",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Sign in to continue to KinetixFSL.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Image(
            painter = painterResource(R.drawable.ic_logo_mark),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )

        Spacer(Modifier.height(40.dp))

        // ---- Email ----
        FieldLabel("Email")
        KinetixTextField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "Enter your email",
            leadingIcon = LoginIcons.Email,
            keyboardType = KeyboardType.Email,
            isError = state.errorMessage != null,
        )

        Spacer(Modifier.height(20.dp))

        // ---- Password ----
        FieldLabel("Password")
        KinetixTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = "Enter your password",
            leadingIcon = LoginIcons.Lock,
            keyboardType = KeyboardType.Password,
            isError = state.errorMessage != null,
            visualTransformation = if (state.isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                val icon = if (state.isPasswordVisible) {
                    LoginIcons.EyeOpen
                } else {
                    LoginIcons.EyeClosed
                }
                Icon(
                    imageVector = icon,
                    contentDescription = if (state.isPasswordVisible) {
                        "Hide password"
                    } else {
                        "Show password"
                    },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onToggleVisibility),
                )
            },
        )

        // ---- Error message ----
        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- Forgot password ----
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onForgotPassword) {
                Text(
                    text = "Forgot password?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- Login ----
        KinetixPrimaryButton(
            text = if (state.isLoading) "Signing in..." else "Login",
            onClick = onLoginClick,
            enabled = !state.isLoading,
        )

        Spacer(Modifier.height(24.dp))

        // ---- Divider ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            Text(
                text = "or continue with",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }

        Spacer(Modifier.height(24.dp))

        // ---- Google ----
        GoogleButton(
            onClick = onGoogleClick,
            enabled = !state.isLoading,
        )

        Spacer(Modifier.height(32.dp))

        // ---- Sign up ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Don't have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Sign Up",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onSignUpClick),
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
    )
}

/** A rounded text field with a leading icon, styled to match the design. */
@Composable
private fun KinetixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        },
        trailingIcon = trailingIcon,
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * The "continue with Google" button. Now wired to Credential Manager via the
 * ViewModel; the [enabled] flag grays it out while any sign-in is in flight.
 */
@Composable
private fun GoogleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Simple "G" mark. Swap for the multicolor Google logo later if desired.
            Text(
                text = "G",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = "Google",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LoginScreenPreview() {
    KinetixFSLTheme {
        LoginContent(
            state = LoginUiState(email = "test@kinetixfsl.com", password = "secret"),
            onEmailChange = {},
            onPasswordChange = {},
            onToggleVisibility = {},
            onLoginClick = {},
            onForgotPassword = {},
            onGoogleClick = {},
            onSignUpClick = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Login - Dark")
@Composable
private fun LoginScreenDarkPreview() {
    KinetixFSLTheme(darkTheme = true) {
        LoginContent(
            state = LoginUiState(email = "test@kinetixfsl.com", password = "secret"),
            onEmailChange = {},
            onPasswordChange = {},
            onToggleVisibility = {},
            onLoginClick = {},
            onForgotPassword = {},
            onGoogleClick = {},
            onSignUpClick = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Login - Error")
@Composable
private fun LoginScreenErrorPreview() {
    KinetixFSLTheme {
        LoginContent(
            state = LoginUiState(
                email = "test@kinetixfsl.com",
                password = "wrong",
                errorMessage = "Incorrect email or password.",
            ),
            onEmailChange = {},
            onPasswordChange = {},
            onToggleVisibility = {},
            onLoginClick = {},
            onForgotPassword = {},
            onGoogleClick = {},
            onSignUpClick = {},
        )
    }
}