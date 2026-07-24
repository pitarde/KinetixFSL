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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme
<<<<<<< HEAD
=======
import com.example.kinetixfsl.ui.theme.KinetixGreen
import com.example.kinetixfsl.ui.theme.KinetixGreen10
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
import com.example.kinetixfsl.ui.theme.KinetixPrimaryButton

/**
 * Step 2 of password reset: the confirmation. Reached after Firebase accepts
 * the reset request. Tells the user what to do next and gets them back to Login.
 *
 * We do NOT try to open the email app for them — email clients vary widely and
 * a bad Intent gives a worse UX than a clear instruction. The user knows how
 * to check their email.
 *
 * @param email the address we asked Firebase to send to, echoed back to the user
 * @param onBackToLogin the primary action button
 */
@Composable
fun CheckEmailScreen(
    email: String,
    onBackToLogin: () -> Unit,
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
        Spacer(Modifier.height(96.dp))

        // Green success mark on a soft mint circle — reads as "done" instantly.
        Box(
            modifier = Modifier
                .size(120.dp)
<<<<<<< HEAD
                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
=======
                .background(KinetixGreen10, CircleShape),
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = ForgotPasswordIcons.CheckCircle,
                contentDescription = null,
<<<<<<< HEAD
                tint = MaterialTheme.colorScheme.tertiary,
=======
                tint = KinetixGreen,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                modifier = Modifier.size(72.dp),
            )
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Check Your Email",
            style = MaterialTheme.typography.displayLarge,
<<<<<<< HEAD
            color = MaterialTheme.colorScheme.primary,
=======
            color = KinetixIndigo,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        // Body text with the address emphasised — clearer than a plain paragraph.
<<<<<<< HEAD
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface
        Text(
            text = buildAnnotatedString {
                append("We sent a password reset link to ")
                withStyle(SpanStyle(color = onSurfaceColor, fontWeight = FontWeight.SemiBold)) {
=======
        Text(
            text = buildAnnotatedString {
                append("We sent a password reset link to ")
                withStyle(SpanStyle(color = KinetixInk, fontWeight = FontWeight.SemiBold)) {
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                    append(email)
                }
                append(". Tap the link in that email to set a new password.")
            },
            style = MaterialTheme.typography.bodyLarge,
<<<<<<< HEAD
            color = MaterialTheme.colorScheme.onSurfaceVariant,
=======
            color = KinetixMuted,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Didn't get the email? Check your spam folder.",
            style = MaterialTheme.typography.bodyMedium,
<<<<<<< HEAD
            color = MaterialTheme.colorScheme.onSurfaceVariant,
=======
            color = KinetixMuted,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(48.dp))

        KinetixPrimaryButton(
            text = "Back to Login",
            onClick = onBackToLogin,
        )

        Spacer(Modifier.height(16.dp))

        // Secondary path: resend. Sends the user back to the input screen — that's
        // the simplest way to retry, avoids the risk of accidental repeat requests.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try a different email",
                style = MaterialTheme.typography.bodyMedium,
<<<<<<< HEAD
                color = MaterialTheme.colorScheme.primary,
=======
                color = KinetixIndigo,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onBackToLogin)
                    .padding(vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CheckEmailScreenPreview() {
    KinetixFSLTheme {
        CheckEmailScreen(
            email = "ken@kinetixfsl.com",
            onBackToLogin = {},
        )
    }
}