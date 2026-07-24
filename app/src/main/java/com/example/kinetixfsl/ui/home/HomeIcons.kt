package com.example.kinetixfsl.ui.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * All icons for the dashboard and the side drawer. Drawn as vector paths so we
 * don't pull in a big icon library. Every glyph is a 24x24 outline in Material style.
 */
internal object HomeIcons {

    // ---- Top bar ----

    val Menu: ImageVector by lazy {
        ImageVector.Builder(
            name = "Menu",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f) {
                moveTo(4f, 7f); lineTo(20f, 7f)
                moveTo(4f, 12f); lineTo(20f, 12f)
                moveTo(4f, 17f); lineTo(20f, 17f)
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder(
            name = "Close",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f) {
                moveTo(6f, 6f); lineTo(18f, 18f)
                moveTo(18f, 6f); lineTo(6f, 18f)
            }
        }.build()
    }

    // ---- Streak card ----

    /** A rounded four-pointed star, used for the streak medal in the header card. */
    val StreakStar: ImageVector by lazy {
        ImageVector.Builder(
            name = "StreakStar",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(14f, 10f)
                lineTo(22f, 12f)
                lineTo(14f, 14f)
                lineTo(12f, 22f)
                lineTo(10f, 14f)
                lineTo(2f, 12f)
                lineTo(10f, 10f)
                close()
            }
        }.build()
    }

    // ---- Bottom navigation ----

    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "Home",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                lineTo(3f, 11f)
                lineTo(5f, 11f)
                lineTo(5f, 20f)
                lineTo(10f, 20f)
                lineTo(10f, 14f)
                lineTo(14f, 14f)
                lineTo(14f, 20f)
                lineTo(19f, 20f)
                lineTo(19f, 11f)
                lineTo(21f, 11f)
                close()
            }
        }.build()
    }

    /** A stack of books — represents the learning modules tab. */
    val Modules: ImageVector by lazy {
        ImageVector.Builder(
            name = "Modules",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Bottom book
                moveTo(4f, 17f); lineTo(20f, 17f); lineTo(20f, 20f); lineTo(4f, 20f); close()
                // Middle book
                moveTo(5f, 12f); lineTo(19f, 12f); lineTo(19f, 15f); lineTo(5f, 15f); close()
                // Top book
                moveTo(6f, 7f); lineTo(18f, 7f); lineTo(18f, 10f); lineTo(6f, 10f); close()
            }
        }.build()
    }

    /**
     * A camera framing brackets icon — used for the central "detect" action. This
     * reads as "point your phone at something" rather than "take a photo," which
     * suits the sign-detection use case better than a shutter camera would.
     */
    val Camera: ImageVector by lazy {
        ImageVector.Builder(
            name = "Camera",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                // Corner brackets
                moveTo(4f, 8f); lineTo(4f, 4f); lineTo(8f, 4f)
                moveTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 8f)
                moveTo(20f, 16f); lineTo(20f, 20f); lineTo(16f, 20f)
                moveTo(8f, 20f); lineTo(4f, 20f); lineTo(4f, 16f)
            }
            path(fill = SolidColor(Color.Black)) {
                // Center dot — indicates capture / focus
                moveTo(14f, 12f)
                arcTo(2f, 2f, 0f, true, true, 10f, 12f)
                arcTo(2f, 2f, 0f, true, true, 14f, 12f)
                close()
            }
        }.build()
    }

    /** A game controller silhouette. */
    val Game: ImageVector by lazy {
        ImageVector.Builder(
            name = "Game",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Body: a wide rounded pill
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

    val Profile: ImageVector by lazy {
        ImageVector.Builder(
            name = "Profile",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                // Head
                moveTo(16f, 8f)
                arcTo(4f, 4f, 0f, true, true, 8f, 8f)
                arcTo(4f, 4f, 0f, true, true, 16f, 8f)
                close()
                // Shoulders
                moveTo(4f, 20f)
                curveTo(4f, 16f, 8f, 14f, 12f, 14f)
                curveTo(16f, 14f, 20f, 16f, 20f, 20f)
                close()
            }
        }.build()
    }

    // ---- Drawer ----

    /** A four-square grid, used for "Dashboard" in the drawer. */
    val GridDashboard: ImageVector by lazy {
        ImageVector.Builder(
            name = "GridDashboard",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
                moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
                moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
                moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
            }
        }.build()
    }

    /** A hand + text lines — represents "Gesture to Text." */
    val HandToText: ImageVector by lazy {
        ImageVector.Builder(
            name = "HandToText",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Hand outline (very simplified — a rounded rectangle)
                moveTo(3f, 8f); lineTo(9f, 8f); lineTo(9f, 16f); lineTo(3f, 16f); close()
                // Arrow
                moveTo(11f, 12f); lineTo(15f, 12f)
                moveTo(13f, 10f); lineTo(15f, 12f); lineTo(13f, 14f)
                // Text lines
                moveTo(17f, 10f); lineTo(21f, 10f)
                moveTo(17f, 14f); lineTo(21f, 14f)
            }
        }.build()
    }

    /** Text lines + hand — represents "Text to Gesture" (mirror of HandToText). */
    val TextToHand: ImageVector by lazy {
        ImageVector.Builder(
            name = "TextToHand",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Text lines
                moveTo(3f, 10f); lineTo(7f, 10f)
                moveTo(3f, 14f); lineTo(7f, 14f)
                // Arrow
                moveTo(9f, 12f); lineTo(13f, 12f)
                moveTo(11f, 10f); lineTo(13f, 12f); lineTo(11f, 14f)
                // Hand outline
                moveTo(15f, 8f); lineTo(21f, 8f); lineTo(21f, 16f); lineTo(15f, 16f); close()
            }
        }.build()
    }

    /** A globe — represents Community. */
    val Community: ImageVector by lazy {
        ImageVector.Builder(
            name = "Community",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                close()
                moveTo(3f, 12f); lineTo(21f, 12f)
                moveTo(12f, 3f)
                curveTo(9f, 6f, 9f, 18f, 12f, 21f)
                moveTo(12f, 3f)
                curveTo(15f, 6f, 15f, 18f, 12f, 21f)
            }
        }.build()
    }

    /** A plus sign, used for "Start a community." */
    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "Plus",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f) {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }.build()
    }

    /** A magnifying glass, used for "Discover communities." */
    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "Search",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(17f, 11f)
                arcTo(6f, 6f, 0f, true, true, 5f, 11f)
                arcTo(6f, 6f, 0f, true, true, 17f, 11f)
                close()
                moveTo(15f, 15f); lineTo(20f, 20f)
            }
        }.build()
    }
}