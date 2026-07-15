package com.example.kinetixfsl.ui.login

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Small, self-contained icons for the login fields, drawn as vector paths.
 *
 * These are defined by hand so the app doesn't depend on
 * `androidx.compose.material:material-icons-extended` (a large library) just for
 * three glyphs. Each is a 24x24 outline in Material style.
 */
internal object LoginIcons {

    val Email: ImageVector by lazy {
        ImageVector.Builder(
            name = "Email",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                // Envelope body
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                lineTo(20f, 18f)
                lineTo(4f, 18f)
                close()
                // Flap
                moveTo(4f, 7f)
                lineTo(12f, 13f)
                lineTo(20f, 7f)
            }
        }.build()
    }

    val Lock: ImageVector by lazy {
        ImageVector.Builder(
            name = "Lock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                // Body
                moveTo(6f, 11f)
                lineTo(18f, 11f)
                lineTo(18f, 20f)
                lineTo(6f, 20f)
                close()
                // Shackle
                moveTo(8f, 11f)
                lineTo(8f, 8f)
                arcTo(4f, 4f, 0f, true, true, 16f, 8f)
                lineTo(16f, 11f)
            }
        }.build()
    }

    val EyeOpen: ImageVector by lazy {
        ImageVector.Builder(
            name = "EyeOpen",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                // Eye outline
                moveTo(2f, 12f)
                curveTo(4f, 7f, 8f, 5f, 12f, 5f)
                curveTo(16f, 5f, 20f, 7f, 22f, 12f)
                curveTo(20f, 17f, 16f, 19f, 12f, 19f)
                curveTo(8f, 19f, 4f, 17f, 2f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                // Pupil
                moveTo(15f, 12f)
                arcTo(3f, 3f, 0f, true, true, 9f, 12f)
                arcTo(3f, 3f, 0f, true, true, 15f, 12f)
            }
        }.build()
    }

    val EyeClosed: ImageVector by lazy {
        ImageVector.Builder(
            name = "EyeClosed",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                // Lowered lid
                moveTo(3f, 10f)
                curveTo(6f, 14f, 9f, 15f, 12f, 15f)
                curveTo(15f, 15f, 18f, 14f, 21f, 10f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                // Slash
                moveTo(4f, 17f)
                lineTo(20f, 7f)
            }
        }.build()
    }
}