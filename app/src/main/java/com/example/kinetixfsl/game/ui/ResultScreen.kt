package com.example.kinetixfsl.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.game.model.LevelPlan
import com.example.kinetixfsl.game.model.SIGNS_PER_LEVEL

/**
 * End-of-level summary (the "Quiz_Done" mockup). On a pass it celebrates —
 * "Amazing!", the celebration illustration, time + score stats, and a confetti
 * burst. On a fail it reassures instead ("That's alright, better luck next
 * time.") with no confetti, and offers a retry (§5 — same five, reshuffled).
 */
@Composable
fun ResultScreen(
    plan: LevelPlan,
    correctCount: Int,
    passed: Boolean,
    elapsedSeconds: Int,
    onRetry: () -> Unit,
    onProceed: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onHome)

    val scorePct = correctCount * 100 / SIGNS_PER_LEVEL

    // Pop the illustration in on entry.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val popScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "resultPop",
    )

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (passed) "Amazing!" else "Good try!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (passed) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            ResultIllustration(
                passed = passed,
                modifier = Modifier
                    .size(200.dp)
                    .scale(popScale),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = if (passed) "You completed this lesson!"
                else "That's alright, better luck next time.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatPill(icon = QuizIcons.Clock, label = formatTime(elapsedSeconds))
                StatPill(icon = QuizIcons.Flag, label = "$scorePct%")
            }

            Spacer(Modifier.height(40.dp))
            if (passed) {
                PrimaryAction(text = "Proceed to next lesson", onClick = onProceed)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Back to home page",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onHome)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                PrimaryAction(text = "Try again", onClick = onRetry)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Back to home page",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onHome)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Confetti only on a pass — layered above the content, ignores touches.
        if (passed) {
            ConfettiOverlay(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.secondary,
                    MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatPill(icon: ImageVector, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * The celebration image on a pass (bundled `quiz_celebration`), or the "better
 * luck" illustration on a fail. The fail image is looked up at runtime as
 * `res/drawable/quiz_try_again` so you can drop it in later; until then it falls
 * back to the trophy/replay vector so the screen always renders.
 */
@Composable
private fun ResultIllustration(passed: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawableName = if (passed) "quiz_celebration" else "quiz_try_again"
    val resId = remember(drawableName) {
        context.resources.getIdentifier(drawableName, "drawable", context.packageName)
    }

    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = if (passed) "Celebration" else "Keep practising",
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        // Fallback vector until the drawable is added.
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (passed) QuizIcons.Trophy else QuizIcons.Replay,
                contentDescription = null,
                tint = if (passed) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(96.dp),
            )
        }
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
