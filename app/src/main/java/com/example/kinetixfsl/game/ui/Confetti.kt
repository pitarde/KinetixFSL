package com.example.kinetixfsl.game.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val startXFraction: Float,
    val color: Color,
    val sizePx: Float,
    val phase: Float,
    val speed: Float,
    val driftAmp: Float,
    val driftFreq: Float,
    val spin: Float,
)

/**
 * A simple looping confetti overlay — colored pieces falling with a little sway
 * and spin. Pure Compose Canvas, no libraries. Drop it in a Box on top of a
 * celebratory screen. [pieceCount] keeps it cheap (~90 is plenty).
 */
@Composable
fun ConfettiOverlay(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    pieceCount: Int = 90,
) {
    val pieces = remember(pieceCount) {
        List(pieceCount) {
            ConfettiPiece(
                startXFraction = Random.nextFloat(),
                color = colors[Random.nextInt(colors.size)],
                sizePx = 14f + Random.nextFloat() * 16f,
                phase = Random.nextFloat(),
                speed = 0.6f + Random.nextFloat() * 0.8f,
                driftAmp = 12f + Random.nextFloat() * 40f,
                driftFreq = 1f + Random.nextFloat() * 2.5f,
                spin = (Random.nextFloat() * 2f - 1f) * 4f,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "confettiTime",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        pieces.forEach { p ->
            val progress = (t * p.speed + p.phase) % 1f
            val x = p.startXFraction * w + sin(progress * p.driftFreq * 2f * PI.toFloat()) * p.driftAmp
            val y = progress * (h + 60f) - 30f
            val angle = progress * 360f * p.spin
            rotate(degrees = angle, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - p.sizePx / 2f, y - p.sizePx / 4f),
                    size = Size(p.sizePx, p.sizePx / 2f),
                )
            }
        }
    }
}
