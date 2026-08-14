package com.example.kinetixfsl.game.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons for the Quiz Game, drawn by hand as vector paths so the app keeps
 * avoiding `material-icons-extended` (see LoginIcons for the same rationale).
 * Each is a 24x24 outline/fill in Material style.
 */
internal object QuizIcons {

    val Close: ImageVector by lazy {
        ImageVector.Builder("Close", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder("Lock", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // Body
                moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 21f); lineTo(5f, 21f); close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Shackle
                moveTo(8f, 11f); lineTo(8f, 8f)
                arcTo(4f, 4f, 0f, true, true, 16f, 8f)
                lineTo(16f, 11f)
            }
        }.build()
    }

    /** A tick inside an outlined circle — a completed/passed level. */
    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder("CheckCircle", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(8f, 12.5f); lineTo(11f, 15.5f); lineTo(16.5f, 9f)
            }
        }.build()
    }

    /** A bare tick, for correct-answer feedback. */
    val Check: ImageVector by lazy {
        ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.6f) {
                moveTo(5f, 13f); lineTo(10f, 18f); lineTo(19f, 6f)
            }
        }.build()
    }

    /** An empty outlined circle — the current, not-yet-passed level's badge. */
    val Circle: ImageVector by lazy {
        ImageVector.Builder("Circle", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                close()
            }
        }.build()
    }

    /** Two left-pointing triangles — the tutorial's "previous sign" button. */
    val Rewind: ImageVector by lazy {
        ImageVector.Builder("Rewind", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11f, 6f); lineTo(11f, 18f); lineTo(3f, 12f); close()
                moveTo(21f, 6f); lineTo(21f, 18f); lineTo(13f, 12f); close()
            }
        }.build()
    }

    /** A circular arrow — retry (result). */
    val Replay: ImageVector by lazy {
        ImageVector.Builder("Replay", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Three-quarter arc, open at the top-right
                moveTo(19f, 8f)
                arcTo(8f, 8f, 0f, true, false, 20f, 12f)
            }
            path(fill = SolidColor(Color.Black)) {
                // Arrowhead pointing into the gap
                moveTo(19f, 4f); lineTo(20f, 9f); lineTo(15f, 8f); close()
            }
        }.build()
    }

    /** A clock — the result screen's time stat. */
    val Clock: ImageVector by lazy {
        ImageVector.Builder("Clock", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                close()
                moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
            }
        }.build()
    }

    /** A flag — the result screen's score stat. */
    val Flag: ImageVector by lazy {
        ImageVector.Builder("Flag", 24.dp, 24.dp, 24f, 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(6f, 3f); lineTo(6f, 21f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 4f); lineTo(18f, 4f); lineTo(15f, 8f); lineTo(18f, 12f); lineTo(6f, 12f); close()
            }
        }.build()
    }

    /** A game controller — marks the current level's node on the roadmap. */
    val Controller: ImageVector by lazy {
        ImageVector.Builder("Controller", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(6f, 8f)
                lineTo(18f, 8f)
                curveTo(21f, 8f, 22f, 11f, 22f, 13.5f)
                curveTo(22f, 16f, 21f, 18f, 19f, 18f)
                curveTo(18f, 18f, 17f, 17f, 16f, 16f)
                lineTo(8f, 16f)
                curveTo(7f, 17f, 6f, 18f, 5f, 18f)
                curveTo(3f, 18f, 2f, 16f, 2f, 13.5f)
                curveTo(2f, 11f, 3f, 8f, 6f, 8f)
                close()
            }
        }.build()
    }

    /** A simple trophy — the passed-level celebration on the result screen. */
    val Trophy: ImageVector by lazy {
        ImageVector.Builder("Trophy", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                // Cup
                moveTo(7f, 4f); lineTo(17f, 4f); lineTo(17f, 9f)
                curveTo(17f, 12f, 15f, 14f, 12f, 14f)
                curveTo(9f, 14f, 7f, 12f, 7f, 9f)
                close()
                // Stem + base
                moveTo(11f, 14f); lineTo(13f, 14f); lineTo(13f, 18f); lineTo(11f, 18f); close()
                moveTo(8f, 18f); lineTo(16f, 18f); lineTo(16f, 20f); lineTo(8f, 20f); close()
            }
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Handles
                moveTo(7f, 5f); lineTo(4f, 5f); lineTo(4f, 8f); curveTo(4f, 10f, 5.5f, 11f, 7f, 11f)
                moveTo(17f, 5f); lineTo(20f, 5f); lineTo(20f, 8f); curveTo(20f, 10f, 18.5f, 11f, 17f, 11f)
            }
        }.build()
    }
}
