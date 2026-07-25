package com.example.kinetixfsl.modules

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons used across the modules feature: sign list, learning room, etc.
 * Same vector-path approach as [com.example.kinetixfsl.ui.home.HomeIcons].
 */
internal object ModulesIcons {

    /** ← Back arrow, used in top bars. */
    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                moveTo(15f, 4f); lineTo(7f, 12f); lineTo(15f, 20f)
            }
        }.build()
    }

    /** ▶ Play circle — used for unstarted signs in the sign list. */
    val PlayCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "PlayCircle",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
                // Circle
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                // Play triangle
                moveTo(10f, 8f)
                lineTo(16f, 12f)
                lineTo(10f, 16f)
                close()
            }
        }.build()
    }

    /** ✓ Checkmark inside a filled circle — used for completed signs. */
    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Filled circle
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 12f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, 21f, 12f)
                close()
            }
            path(stroke = SolidColor(Color.White), strokeLineWidth = 2.4f) {
                // Checkmark
                moveTo(8f, 12f)
                lineTo(11f, 15f)
                lineTo(16f, 9f)
            }
        }.build()
    }

    /** → Forward arrow — used in the "Next" button. */
    val ArrowForward: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowForward",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                moveTo(9f, 4f); lineTo(17f, 12f); lineTo(9f, 20f)
            }
        }.build()
    }
}