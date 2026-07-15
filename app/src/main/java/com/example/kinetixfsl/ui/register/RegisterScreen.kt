package com.example.kinetixfsl.ui.register

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
//import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kinetixfsl.R
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
import com.example.kinetixfsl.ui.theme.KinetixPrimaryButton
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * The Create Account screen. Collects name/email/password/confirm + a terms
 * checkbox, validates locally, then creates the account via Firebase.
 *
 * @param onRegisterSuccess  account created & signed in -> go to Home
 * @param onNavigateToLogin  user already has an account -> back to Log in
 */
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.isRegisterSuccessful) {
        onRegisterSuccess()
        viewModel.onRegisterHandled()
    }

    RegisterContent(
        state = state,
        onFullNameChange = viewModel::onFullNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onTogglePassword = viewModel::togglePasswordVisibility,
        onToggleConfirm = viewModel::toggleConfirmVisibility,
        onToggleTerms = viewModel::toggleAgreedToTerms,
        onRegisterClick = viewModel::register,
        onLoginClick = onNavigateToLogin,
        modifier = modifier,
    )
}

@Composable
private fun RegisterContent(
    state: RegisterUiState,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onToggleConfirm: () -> Unit,
    onToggleTerms: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
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
        Spacer(Modifier.height(40.dp))

        Text(
            text = "Create Account",
            style = MaterialTheme.typography.displayLarge,
            color = KinetixIndigo,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Join KinetixFSL and start learning.",
            style = MaterialTheme.typography.bodyMedium,
            color = KinetixMuted,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Image(
            painter = painterResource(R.drawable.register_illustration),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        )

        Spacer(Modifier.height(24.dp))

        // ---- Full name ----
        FieldLabel("Full Name")
        RegisterTextField(
            value = state.fullName,
            onValueChange = onFullNameChange,
            placeholder = "Enter your full name",
            leadingIcon = RegisterIcons.Person,
            keyboardType = KeyboardType.Text,
            isError = state.errorMessage != null,
        )

        Spacer(Modifier.height(16.dp))

        // ---- Email ----
        FieldLabel("Email")
        RegisterTextField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "Enter your email",
            leadingIcon = RegisterIcons.Email,
            keyboardType = KeyboardType.Email,
            isError = state.errorMessage != null,
        )

        Spacer(Modifier.height(16.dp))

        // ---- Password ----
        FieldLabel("Password")
        RegisterTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            placeholder = "Enter your password",
            leadingIcon = RegisterIcons.Lock,
            keyboardType = KeyboardType.Password,
            isError = state.errorMessage != null,
            visualTransformation = if (state.isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = { PasswordEye(state.isPasswordVisible, onTogglePassword) },
        )

        Spacer(Modifier.height(16.dp))

        // ---- Confirm password ----
        FieldLabel("Confirm Password")
        RegisterTextField(
            value = state.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = "Enter your password",
            leadingIcon = RegisterIcons.Lock,
            keyboardType = KeyboardType.Password,
            isError = state.errorMessage != null,
            visualTransformation = if (state.isConfirmVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = { PasswordEye(state.isConfirmVisible, onToggleConfirm) },
        )

        Spacer(Modifier.height(16.dp))

        // ---- Terms checkbox ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.agreedToTerms,
                onCheckedChange = { onToggleTerms() },
                colors = CheckboxDefaults.colors(
                    checkedColor = KinetixIndigo,
                    uncheckedColor = KinetixOutline,
                    checkmarkColor = KinetixWhite,
                ),
            )
            Text(
                text = buildAnnotatedString {
                    append("I agree to the ")
                    withStyle(SpanStyle(color = KinetixIndigo, fontWeight = FontWeight.SemiBold)) {
                        append("Terms of Service")
                    }
                    append(" and ")
                    withStyle(SpanStyle(color = KinetixIndigo, fontWeight = FontWeight.SemiBold)) {
                        append("Privacy Policy.")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixInk,
            )
        }

        // ---- Error ----
        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.labelMedium,
                color = KinetixError,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        // ---- Sign up button ----
        // (Design labels this "Login", but on a Create Account screen the action
        // is signing up — using the accurate label so the button matches the flow.)
        KinetixPrimaryButton(
            text = if (state.isLoading) "Creating account..." else "Sign Up",
            onClick = onRegisterClick,
            enabled = !state.isLoading,
        )

        Spacer(Modifier.height(20.dp))

        // ---- Already have an account ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Already have an account? ",
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixInk,
            )
            Text(
                text = "Login",
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixIndigo,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onLoginClick),
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PasswordEye(visible: Boolean, onToggle: () -> Unit) {
    Icon(
        imageVector = if (visible) RegisterIcons.EyeOpen else RegisterIcons.EyeClosed,
        contentDescription = if (visible) "Hide password" else "Show password",
        tint = KinetixMuted,
        modifier = Modifier
            .size(22.dp)
            .clickable(onClick = onToggle),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = KinetixInk,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun RegisterTextField(
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
                color = KinetixMuted,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = KinetixInk,
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
            focusedContainerColor = KinetixWhite,
            unfocusedContainerColor = KinetixWhite,
            errorContainerColor = KinetixWhite,
            focusedBorderColor = KinetixIndigo,
            unfocusedBorderColor = KinetixOutline,
            errorBorderColor = KinetixError,
            cursorColor = KinetixIndigo,
        ),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun RegisterScreenPreview() {
    KinetixFSLTheme {
        RegisterContent(
            state = RegisterUiState(
                fullName = "Ken Cruz",
                email = "ken@kinetixfsl.com",
                password = "secret1",
                confirmPassword = "secret1",
                agreedToTerms = true,
            ),
            onFullNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePassword = {},
            onToggleConfirm = {},
            onToggleTerms = {},
            onRegisterClick = {},
            onLoginClick = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Register - Error")
@Composable
private fun RegisterScreenErrorPreview() {
    KinetixFSLTheme {
        RegisterContent(
            state = RegisterUiState(
                fullName = "Ken",
                email = "ken@kinetixfsl.com",
                password = "secret1",
                confirmPassword = "different",
                errorMessage = "Passwords do not match.",
            ),
            onFullNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onTogglePassword = {},
            onToggleConfirm = {},
            onToggleTerms = {},
            onRegisterClick = {},
            onLoginClick = {},
        )
    }
}