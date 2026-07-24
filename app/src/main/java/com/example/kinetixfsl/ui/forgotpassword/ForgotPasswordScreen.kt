package com.example.kinetixfsl.ui.forgotpassword

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
<<<<<<< HEAD
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixPrimaryButton
=======
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
import com.example.kinetixfsl.ui.theme.KinetixPrimaryButton
import com.example.kinetixfsl.ui.theme.KinetixWhite
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f

/**
 * Step 1 of password reset: the user types the email tied to their account and
 * asks Firebase to send them a reset link.
 *
 * @param onLinkSent       Firebase accepted the request -> go to confirmation
 * @param onNavigateBack   top-left back arrow -> Login
 */
@Composable
fun ForgotPasswordScreen(
    onLinkSent: (email: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForgotPasswordViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // When the ViewModel reports the email is sent, forward the address to the
    // confirmation screen (so it can display "we sent it to ...") and reset the flag.
    if (state.isRequestSent) {
        onLinkSent(state.sentToEmail)
        viewModel.onRequestHandled()
    }

    ForgotPasswordContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onSendClick = viewModel::sendResetEmail,
        onBackClick = onNavigateBack,
        modifier = modifier,
    )
}

@Composable
private fun ForgotPasswordContent(
    state: ForgotPasswordUiState,
    onEmailChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        // Top-left back arrow — same treatment we'll reuse on other secondary screens.
        Spacer(Modifier.height(8.dp))
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = ForgotPasswordIcons.ArrowBack,
                contentDescription = "Back",
<<<<<<< HEAD
                tint = MaterialTheme.colorScheme.onSurface,
=======
                tint = KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Forgot Password?",
                style = MaterialTheme.typography.displayLarge,
<<<<<<< HEAD
                color = MaterialTheme.colorScheme.primary,
=======
                color = KinetixIndigo,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Enter the email tied to your account and we'll " +
                        "send you a link to reset your password.",
                style = MaterialTheme.typography.bodyMedium,
<<<<<<< HEAD
                color = MaterialTheme.colorScheme.onSurfaceVariant,
=======
                color = KinetixMuted,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        Spacer(Modifier.height(40.dp))

        // ---- Email ----
        FieldLabel("Email")
        ForgotPasswordTextField(
            value = state.email,
            onValueChange = onEmailChange,
            placeholder = "Enter your email",
            leadingIcon = ForgotPasswordIcons.Email,
            isError = state.errorMessage != null,
        )

        if (state.errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.errorMessage,
                style = MaterialTheme.typography.labelMedium,
<<<<<<< HEAD
                color = MaterialTheme.colorScheme.error,
=======
                color = KinetixError,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        // ---- Send button ----
        KinetixPrimaryButton(
            text = if (state.isLoading) "Sending..." else "Send Reset Link",
            onClick = onSendClick,
            enabled = !state.isLoading,
        )

        Spacer(Modifier.height(24.dp))

        // ---- Back to login link ----
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Back to Login",
                style = MaterialTheme.typography.bodyMedium,
<<<<<<< HEAD
                color = MaterialTheme.colorScheme.primary,
=======
                color = KinetixIndigo,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onBackClick),
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
<<<<<<< HEAD
        color = MaterialTheme.colorScheme.onSurface,
=======
        color = KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun ForgotPasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
<<<<<<< HEAD
                color = MaterialTheme.colorScheme.onSurfaceVariant,
=======
                color = KinetixMuted,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
<<<<<<< HEAD
                tint = MaterialTheme.colorScheme.onSurface,
=======
                tint = KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                modifier = Modifier.size(22.dp),
            )
        },
        singleLine = true,
        isError = isError,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        colors = OutlinedTextFieldDefaults.colors(
<<<<<<< HEAD
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            errorContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            errorBorderColor = MaterialTheme.colorScheme.error,
            cursorColor = MaterialTheme.colorScheme.primary,
=======
            focusedContainerColor = KinetixWhite,
            unfocusedContainerColor = KinetixWhite,
            errorContainerColor = KinetixWhite,
            focusedBorderColor = KinetixIndigo,
            unfocusedBorderColor = KinetixOutline,
            errorBorderColor = KinetixError,
            cursorColor = KinetixIndigo,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        ),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ForgotPasswordPreview() {
    KinetixFSLTheme {
        ForgotPasswordContent(
            state = ForgotPasswordUiState(email = "ken@kinetixfsl.com"),
            onEmailChange = {},
            onSendClick = {},
            onBackClick = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "Forgot Password - Error")
@Composable
private fun ForgotPasswordErrorPreview() {
    KinetixFSLTheme {
        ForgotPasswordContent(
            state = ForgotPasswordUiState(
                email = "not-an-email",
                errorMessage = "That email address looks invalid.",
            ),
            onEmailChange = {},
            onSendClick = {},
            onBackClick = {},
        )
    }
}