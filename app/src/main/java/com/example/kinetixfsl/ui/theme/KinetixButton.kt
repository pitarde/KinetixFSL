package com.example.kinetixfsl.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * The app's primary call-to-action. A wide indigo pill with a soft radius,
 * matching the "Get Started" button in the onboarding design. Reused anywhere
 * we need a main action so buttons stay identical across screens.
 */
@Composable
fun KinetixPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KinetixIndigo,
            contentColor = KinetixWhite,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KinetixPrimaryButtonPreview() {
    KinetixFSLTheme {
        Box(Modifier.padding(24.dp)) {
            KinetixPrimaryButton(text = "Get Started", onClick = {})
        }
    }
}