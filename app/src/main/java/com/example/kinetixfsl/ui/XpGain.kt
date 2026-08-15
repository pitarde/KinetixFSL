package com.example.kinetixfsl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kinetixfsl.progress.Achievement
import com.example.kinetixfsl.ui.theme.KinetixGreen
import kotlinx.coroutines.delay

/**
 * Shared "you earned it" animations, used by both the Quiz results screen and
 * the Module camera-practice screen so an XP/achievement reward always reads the
 * same way across the app.
 *
 * The point of these is purely feedback: a learner should *see* the reward land,
 * not have it silently added to a store, so they understand that practising and
 * quizzing actually earns XP.
 */

/**
 * A "+N XP" pill that pops in, floats up toward the progress bar above it, then
 * fades out — a one-shot celebration. Set [visible] true to (re)play it.
 *
 * @param riseDistance how far up the badge drifts as it fades (toward the bar).
 */
@Composable
fun FloatingXpBadge(
    xp: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    riseDistance: Dp = 70.dp,
) {
    if (xp <= 0) return

    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }

    // Slow and readable: a gentle pop-in, a long hold at full size/opacity so the
    // learner has time to actually read "+N XP", then an easy float-up-and-fade.
    // Total ~3.2s — noticeably slower than a snappy UI transition on purpose.
    LaunchedEffect(visible) {
        if (visible) {
            progress.snapTo(0f)
            playing = true
            progress.animateTo(1f, animationSpec = tween(3200, easing = LinearEasing))
            playing = false
        }
    }

    if (!playing) return

    val p = progress.value
    // Rise only happens in the back half, and eased, so the badge sits still and
    // legible for the first ~55% of the animation before it drifts upward.
    val riseP = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
    val risePx = with(LocalDensity.current) { -riseDistance.toPx() } * (riseP * riseP)
    val alpha = when {
        p < 0.12f -> p / 0.12f          // fade in
        p > 0.8f -> (1f - p) / 0.2f     // fade out
        else -> 1f
    }.coerceIn(0f, 1f)
    val scale = 0.7f + 0.3f * (if (p < 0.2f) p / 0.2f else 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    translationY = risePx
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
                .background(KinetixGreen, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "+$xp XP",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * A top banner that slides in for each freshly-unlocked achievement in turn,
 * showing its emoji and title, then slides away. Renders nothing when the list
 * is empty, so it can be dropped into any screen's overlay unconditionally.
 */
@Composable
fun AchievementUnlockedPopup(
    achievements: List<Achievement>,
    modifier: Modifier = Modifier,
) {
    if (achievements.isEmpty()) return

    var index by remember(achievements) { mutableIntStateOf(0) }
    var visible by remember(achievements) { mutableStateOf(false) }

    LaunchedEffect(achievements) {
        for (i in achievements.indices) {
            index = i
            visible = true
            delay(2600)
            visible = false
            delay(350)
        }
    }

    val ach = achievements.getOrNull(index) ?: return

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(18.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = ach.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Achievement unlocked!",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = ach.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
