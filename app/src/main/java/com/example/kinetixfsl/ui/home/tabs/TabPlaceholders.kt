package com.example.kinetixfsl.ui.home.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixMuted

/**
 * Blank labelled placeholders for the four non-Home tabs. Each just says which
 * screen it is so we can navigate around and know where we are while the real
 * features are still to be built.
 */

@Composable
fun ModulesTabPlaceholder(modifier: Modifier = Modifier) =
    LabeledPlaceholder("Modules", "Learning categories: Alphabet, Numbers, ...", modifier)

@Composable
fun CameraTabPlaceholder(modifier: Modifier = Modifier) =
    LabeledPlaceholder("Camera", "Sign detection — the capstone feature.", modifier)

@Composable
fun GameTabPlaceholder(modifier: Modifier = Modifier) =
    LabeledPlaceholder("Game", "Quiz mode.", modifier)

/**
 * Profile placeholder also carries the temporary Sign Out button — the real
 * profile screen will absorb it when we build the account/settings area.
 */
@Composable
fun ProfileTabPlaceholder(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledPlaceholder(
        title = "Profile",
        subtitle = "Account, streak history, achievements.",
        modifier = modifier,
        extra = {
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onSignOut) {
                Text(
                    text = "Sign out",
                    style = MaterialTheme.typography.labelLarge,
                    color = KinetixIndigo,
                )
            }
        },
    )
}

@Composable
private fun LabeledPlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = KinetixIndigo,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixMuted,
                textAlign = TextAlign.Center,
            )
            if (extra != null) extra()
        }
    }
}