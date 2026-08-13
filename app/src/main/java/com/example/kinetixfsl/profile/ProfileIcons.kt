package com.example.kinetixfsl.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons for the Profile screen, drawn as 24×24 vector paths in the same
 * lightweight outline style as [com.example.kinetixfsl.ui.home.HomeIcons] so we
 * don't pull in a Material icon dependency. Colour is applied at the call site
 * via [androidx.compose.material3.Icon]'s tint, so every path uses black here.
 */
internal object ProfileIcons {

    private fun icon(
        name: String,
        block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply(block).build()

    private fun ImageVector.Builder.stroke(
        width: Float = 2f,
        draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) = path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = width,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = draw,
    )

    private fun ImageVector.Builder.fill(
        draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ) = path(fill = SolidColor(Color.Black), pathBuilder = draw)

    // ── Tab icons ───────────────────────────────────────────────────

    /** Progress tab — an upward trend line with an arrowhead. */
    val TrendingUp: ImageVector by lazy {
        icon("TrendingUp") {
            stroke(2.2f) {
                moveTo(3f, 16.5f); lineTo(9f, 10.5f); lineTo(13f, 14.5f); lineTo(21f, 6.5f)
            }
            stroke(2.2f) {
                moveTo(15.5f, 6.5f); lineTo(21f, 6.5f); lineTo(21f, 12f)
            }
        }
    }

    /** Weak Spots tab — a warning triangle with an exclamation mark. */
    val AlertTriangle: ImageVector by lazy {
        icon("AlertTriangle") {
            stroke(2.1f) {
                moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
            }
            stroke(2.1f) {
                moveTo(12f, 9f); lineTo(12f, 14f)
            }
            fill {
                moveTo(12f, 16.4f)
                arcTo(1.1f, 1.1f, 0f, true, true, 11.98f, 16.4f)
                close()
            }
        }
    }

    /** Forecast tab — a large four-point sparkle plus a small one. */
    val Sparkles: ImageVector by lazy {
        icon("Sparkles") {
            fill {
                // Big sparkle
                moveTo(10f, 3f)
                curveTo(10.6f, 6.6f, 11.4f, 7.4f, 15f, 8f)
                curveTo(11.4f, 8.6f, 10.6f, 9.4f, 10f, 13f)
                curveTo(9.4f, 9.4f, 8.6f, 8.6f, 5f, 8f)
                curveTo(8.6f, 7.4f, 9.4f, 6.6f, 10f, 3f)
                close()
            }
            fill {
                // Small sparkle
                moveTo(17f, 12f)
                curveTo(17.35f, 14.1f, 17.9f, 14.65f, 20f, 15f)
                curveTo(17.9f, 15.35f, 17.35f, 15.9f, 17f, 18f)
                curveTo(16.65f, 15.9f, 16.1f, 15.35f, 14f, 15f)
                curveTo(16.1f, 14.65f, 16.65f, 14.1f, 17f, 12f)
                close()
            }
        }
    }

    /** Coach's Picks tab — a magic wand with a sparkle at the tip. */
    val Wand: ImageVector by lazy {
        icon("Wand") {
            stroke(2.2f) {
                moveTo(6f, 19f); lineTo(16f, 9f)
            }
            fill {
                // Star at the wand tip
                moveTo(18f, 3f)
                curveTo(18.4f, 5.4f, 18.9f, 5.9f, 21f, 6.3f)
                curveTo(18.9f, 6.7f, 18.4f, 7.2f, 18f, 9.6f)
                curveTo(17.6f, 7.2f, 17.1f, 6.7f, 15f, 6.3f)
                curveTo(17.1f, 5.9f, 17.6f, 5.4f, 18f, 3f)
                close()
            }
        }
    }

    // ── Header / stats ──────────────────────────────────────────────

    /** Settings gear (top-right of the header). */
    val Settings: ImageVector by lazy {
        icon("Settings") {
            stroke(1.8f) {
                // Outer cog: an octagon-ish gear body
                moveTo(12f, 2.5f)
                lineTo(14f, 4f); lineTo(16.5f, 3.5f); lineTo(17.5f, 6f)
                lineTo(20f, 7f); lineTo(19.5f, 9.5f); lineTo(21f, 11.5f)
                lineTo(19.5f, 13.5f); lineTo(20f, 16f); lineTo(17.5f, 17f)
                lineTo(16.5f, 19.5f); lineTo(14f, 19f); lineTo(12f, 20.5f)
                lineTo(10f, 19f); lineTo(7.5f, 19.5f); lineTo(6.5f, 17f)
                lineTo(4f, 16f); lineTo(4.5f, 13.5f); lineTo(3f, 11.5f)
                lineTo(4.5f, 9.5f); lineTo(4f, 7f); lineTo(6.5f, 6f)
                lineTo(7.5f, 3.5f); lineTo(10f, 4f); close()
            }
            stroke(1.8f) {
                moveTo(15f, 11.5f)
                arcTo(3f, 3f, 0f, true, true, 9f, 11.5f)
                arcTo(3f, 3f, 0f, true, true, 15f, 11.5f)
                close()
            }
        }
    }

    /** Flame — streak stat. */
    val Flame: ImageVector by lazy {
        icon("Flame") {
            fill {
                moveTo(12f, 2.5f)
                curveTo(15f, 6f, 17.5f, 8.5f, 17.5f, 13f)
                curveTo(17.5f, 16.6f, 15f, 19.5f, 12f, 19.5f)
                curveTo(9f, 19.5f, 6.5f, 16.6f, 6.5f, 13f)
                curveTo(6.5f, 10.5f, 8f, 9f, 9f, 11f)
                curveTo(9.5f, 8f, 10.5f, 5.5f, 12f, 2.5f)
                close()
            }
        }
    }

    /** Two hands / signing glyph — signs-learned stat. Simplified as a check-hand. */
    val HandStar: ImageVector by lazy {
        icon("HandStar") {
            fill {
                moveTo(12f, 3f)
                lineTo(13.9f, 8.9f)
                lineTo(20f, 8.9f)
                lineTo(15f, 12.6f)
                lineTo(16.9f, 18.5f)
                lineTo(12f, 14.8f)
                lineTo(7.1f, 18.5f)
                lineTo(9f, 12.6f)
                lineTo(4f, 8.9f)
                lineTo(10.1f, 8.9f)
                close()
            }
        }
    }

    /** Clock — study time / best-time insight. */
    val Clock: ImageVector by lazy {
        icon("Clock") {
            stroke(2f) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                close()
            }
            stroke(2f) {
                moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15.5f, 14f)
            }
        }
    }

    /** Target — "fix your weak spot". */
    val Target: ImageVector by lazy {
        icon("Target") {
            stroke(2f) {
                moveTo(20f, 12f)
                arcTo(8f, 8f, 0f, true, true, 4f, 12f)
                arcTo(8f, 8f, 0f, true, true, 20f, 12f)
                close()
            }
            stroke(2f) {
                moveTo(16f, 12f)
                arcTo(4f, 4f, 0f, true, true, 8f, 12f)
                arcTo(4f, 4f, 0f, true, true, 16f, 12f)
                close()
            }
            fill {
                moveTo(13.5f, 12f)
                arcTo(1.5f, 1.5f, 0f, true, true, 10.5f, 12f)
                arcTo(1.5f, 1.5f, 0f, true, true, 13.5f, 12f)
                close()
            }
        }
    }

    /** Lightning bolt — adaptive difficulty / speed. */
    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            fill {
                moveTo(13f, 2f)
                lineTo(5f, 13f)
                lineTo(11f, 13f)
                lineTo(10f, 22f)
                lineTo(19f, 10f)
                lineTo(12.5f, 10f)
                close()
            }
        }
    }

    /** Small upward arrow — trend up. */
    val ArrowUp: ImageVector by lazy {
        icon("ArrowUp") {
            stroke(2.4f) {
                moveTo(12f, 19f); lineTo(12f, 5f)
                moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
            }
        }
    }

    /** Small downward arrow — trend down. */
    val ArrowDown: ImageVector by lazy {
        icon("ArrowDown") {
            stroke(2.4f) {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(6f, 13f); lineTo(12f, 19f); lineTo(18f, 13f)
            }
        }
    }

    /** Chevron right — CTA / list affordance. */
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            stroke(2.4f) {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }
    }
}
