package com.example.kinetixfsl.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.game.data.QuizContent
import com.example.kinetixfsl.game.model.LevelPlan
import com.example.kinetixfsl.game.model.SIGNS_PER_LEVEL
import com.example.kinetixfsl.ui.theme.KinetixIndigoLight

/**
 * The tutorial phase: the five signs of a level shown one at a time before the
 * quiz (§3). The sign card slides + fades between steps, and the prev button
 * (left of Next) steps back to the previous sign.
 */
@Composable
fun TutorialScreen(
    plan: LevelPlan,
    index: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val canGoPrev = index > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        QuizTopBar(
            progress = (index + 1f) / SIGNS_PER_LEVEL,
            onClose = onClose,
        )

        // Only a fade between signs — no slide. The slide transition disturbed the
        // Column's layout and dropped the button row ("rewind disappears on Next").
        // A plain Crossfade has no slide or size transform, so the buttons below
        // are never affected and the rewind button always stays put.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            Crossfade(
                targetState = index,
                animationSpec = tween(250),
                label = "tutorialSign",
                modifier = Modifier.fillMaxSize(),
            ) { step ->
                val signId = plan.signIds[step]
                val word = QuizContent.word(signId)
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "New sign:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = word,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SignVideo(signId = signId, word = word)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Previous sign — always visible; just inert on the very first step.
            // The muted state is done with a translucent background COLOR, not a
            // Modifier.alpha() layer: that offscreen layer wasn't being redrawn
            // while the sign card animated, which left the button invisible (but
            // still clickable) after tapping Next.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (canGoPrev) KinetixIndigoLight
                        else KinetixIndigoLight.copy(alpha = 0.5f)
                    )
                    .clickable(enabled = canGoPrev, onClick = onPrev),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = QuizIcons.Rewind,
                    contentDescription = "Previous sign",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = if (index == SIGNS_PER_LEVEL - 1) "Start Quiz" else "Next",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
