package com.example.kinetixfsl.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kinetixfsl.game.ui.ConfettiOverlay
import com.example.kinetixfsl.progress.RankTier
import com.example.kinetixfsl.ui.theme.KinetixGreen
import com.example.kinetixfsl.ui.theme.KinetixIndigo

/**
 * A full-screen celebration when the learner reaches a new rank tier: confetti
 * rains, the new rank badge springs in from nothing behind a pulsing glow, and a
 * "RANK UP!" banner announces it — the kind of moment games use to make levelling
 * feel earned. Shown once per rank increase (see the caller's ack-tracking).
 */
@Composable
fun RankUpDialog(rank: RankTier, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Badge springs in from tiny to full size.
        val badgeScale = remember { Animatable(0.2f) }
        LaunchedEffect(Unit) {
            badgeScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        }
        // Glow ring behind the badge, breathing.
        val glow = rememberInfiniteTransition(label = "rankGlow")
        val glowScale by glow.animateFloat(
            initialValue = 0.92f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
            label = "rankGlowScale",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            ConfettiOverlay(
                colors = listOf(KinetixGreen, KinetixIndigo, MaterialTheme.colorScheme.tertiary, Color(0xFFF4A62A)),
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier = Modifier
                    .padding(horizontal = 36.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 28.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "⭐ RANK UP! ⭐",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = KinetixGreen,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                Box(contentAlignment = Alignment.Center) {
                    // Radial glow behind the badge.
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .graphicsLayer { scaleX = glowScale; scaleY = glowScale }
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(KinetixGreen.copy(alpha = 0.35f), Color.Transparent),
                                ),
                            ),
                    )
                    Image(
                        painter = painterResource(rank.badgeRes),
                        contentDescription = "${rank.title} badge",
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer {
                                scaleX = badgeScale.value
                                scaleY = badgeScale.value
                            },
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = "You're now a",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = rank.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text(
                        text = "Awesome!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
