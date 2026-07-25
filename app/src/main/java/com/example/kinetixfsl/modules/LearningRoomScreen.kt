package com.example.kinetixfsl.modules

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kinetixfsl.modules.model.FslSignData
import com.example.kinetixfsl.modules.model.SignCategory
import com.example.kinetixfsl.modules.model.SignEntry
import com.example.kinetixfsl.ui.theme.KinetixFSLTheme

/**
 * Learning Room — the detail screen where a learner studies a specific sign.
 *
 * Matches the Figma "Alphabet" screen: illustration of the sign at top,
 * sign name, numbered step-by-step instructions, and a "Practice the sign"
 * button at the bottom that will eventually open the Camera.
 *
 * @param category   The parent category (needed for display prefix + Next logic).
 * @param signIndex  Index of the current sign within the category.
 * @param onBack     Called when the back arrow is tapped.
 * @param onNext     Called when the "Next" button is tapped. Null if this
 *                   is the last sign in the category.
 * @param onPractice Called when "Practice the sign" is tapped.
 *                   Will navigate to Camera when that feature is built.
 */
@Composable
fun LearningRoomScreen(
    category: SignCategory,
    signIndex: Int,
    onBack: () -> Unit,
    onNext: (() -> Unit)?,
    onPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sign = category.signs.getOrNull(signIndex) ?: return
    val displayPrefix = getSignDisplayPrefix(category.id)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar: back + "Next" button ───────────────────
        LearningTopBar(
            onBack = onBack,
            onNext = onNext,
        )

        // ── Scrollable content ──────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            // Title
            Text(
                text = "Learning Room",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(20.dp))

            // ── Sign illustration placeholder ───────────────
            // This area will eventually hold an offline image or
            // short video showing how the sign is performed.
            // For now, a branded placeholder.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$displayPrefix ${sign.name}",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Illustration coming soon",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Sign name ───────────────────────────────────
            Text(
                text = "$displayPrefix ${sign.name}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(20.dp))

            // ── Steps section ───────────────────────────────
            if (sign.steps.isNotEmpty()) {
                Text(
                    text = "STEPS",
                    style = MaterialTheme.typography.labelLarge.copy(
                        letterSpacing = 1.5.sp,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(12.dp))

                sign.steps.forEachIndexed { index, stepText ->
                    StepItem(
                        stepNumber = index + 1,
                        text = stepText,
                    )
                    if (index < sign.steps.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            } else {
                // No steps authored yet — graceful placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Steps for this sign are being prepared.\nYou can still practice with the camera below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // ── Bottom: Practice button ─────────────────────────
        Button(
            onClick = onPractice,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = "Practice the sign",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Top bar ─────────────────────────────────────────────────────

@Composable
private fun LearningTopBar(
    onBack: () -> Unit,
    onNext: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        // Back arrow
        Icon(
            imageVector = ModulesIcons.ArrowBack,
            contentDescription = "Go back",
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(28.dp)
                .clickable(onClick = onBack),
        )

        // "Next" pill button (only shown if there's a next sign)
        if (onNext != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(20.dp))
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable(onClick = onNext)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Step item ───────────────────────────────────────────────────

@Composable
private fun StepItem(
    stepNumber: Int,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Numbered circle badge
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stepNumber.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Step description
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Returns the display prefix for a sign (e.g. "Letter" for alphabet, "Number" for numbers).
 */
private fun getSignDisplayPrefix(categoryId: String): String = when (categoryId) {
    "alphabet" -> "Letter"
    "numbers" -> "Number"
    else -> ""
}

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, showSystemUi = true, name = "LearningRoom – With Steps")
@Composable
private fun LearningRoomPreviewWithSteps() {
    val cat = remember { FslSignData.findCategory("alphabet")!! }
    KinetixFSLTheme(darkTheme = false) {
        LearningRoomScreen(
            category = cat,
            signIndex = 1, // Letter B — has sample steps
            onBack = {},
            onNext = {},
            onPractice = {},
        )
    }
}

@Preview(showBackground = true, showSystemUi = true, name = "LearningRoom – No Steps")
@Composable
private fun LearningRoomPreviewNoSteps() {
    val cat = remember { FslSignData.findCategory("alphabet")!! }
    KinetixFSLTheme(darkTheme = true) {
        LearningRoomScreen(
            category = cat,
            signIndex = 5, // Letter F — no steps yet
            onBack = {},
            onNext = {},
            onPractice = {},
        )
    }
}