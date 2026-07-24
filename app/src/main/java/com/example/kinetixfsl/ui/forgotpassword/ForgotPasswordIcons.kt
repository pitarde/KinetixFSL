package com.example.kinetixfsl.ui.forgotpassword

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons for the forgot-password flow, drawn as vector paths so the app needs
 * no icon library. Consistent with the Login and Register screens' style.
 */
internal object ForgotPasswordIcons {

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
                moveTo(4f, 6f)
                lineTo(20f, 6f)
                lineTo(20f, 18f)
                lineTo(4f, 18f)
                close()
                moveTo(4f, 7f)
                lineTo(12f, 13f)
                lineTo(20f, 7f)
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
            ) {
                // Arrow shaft
                moveTo(20f, 12f)
                lineTo(5f, 12f)
                // Arrow head
                moveTo(11f, 6f)
                lineTo(5f, 12f)
                lineTo(11f, 18f)
            }
        }.build()
    }

    /**
     * A large "check in a circle" mark used on the confirmation screen. Reads
     * as "success" without needing color to carry meaning.
     */
    val CheckCircle: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckCircle",
            defaultWidth = 96.dp,
            defaultHeight = 96.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
            ) {
                // Circle
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
            ) {
                // Check
                moveTo(8f, 12.5f)
                lineTo(11f, 15.5f)
                lineTo(16f, 9.5f)
            }
        }.build()
    }
}