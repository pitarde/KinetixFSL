package com.example.kinetixfsl.community

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons for the community screens: bottom-nav destinations plus the interaction
 * row on each post card (upvote / downvote / comment / share). Drawn as vector
 * paths so we don't pull in an icon library for a handful of glyphs.
 */
internal object CommunityIcons {

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

    // ---- Bottom nav ----

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

    val Profile: ImageVector by lazy {
        ImageVector.Builder(
            name = "Profile",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(21f, 12f)
                arcTo(9f, 9f, 0f, true, true, 3f, 12f)
                arcTo(9f, 9f, 0f, true, true, 21f, 12f)
                close()
                moveTo(16f, 10f)
                arcTo(4f, 4f, 0f, true, true, 8f, 10f)
                arcTo(4f, 4f, 0f, true, true, 16f, 10f)
                close()
                moveTo(5.5f, 19f)
                curveTo(6.5f, 16f, 9f, 15f, 12f, 15f)
                curveTo(15f, 15f, 17.5f, 16f, 18.5f, 19f)
            }
        }.build()
    }

    /** Pencil-on-square — "create a post." */
    val CreatePost: ImageVector by lazy {
        ImageVector.Builder(
            name = "CreatePost",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Note pad
                moveTo(4f, 5f); lineTo(15f, 5f); lineTo(15f, 20f); lineTo(4f, 20f); close()
                // Pencil
                moveTo(17f, 3f); lineTo(21f, 7f); lineTo(13f, 15f); lineTo(9f, 15f); lineTo(9f, 11f); close()
            }
        }.build()
    }

    val Bell: ImageVector by lazy {
        ImageVector.Builder(
            name = "Bell",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Body
                moveTo(6f, 16f)
                curveTo(6f, 12f, 6f, 6f, 12f, 6f)
                curveTo(18f, 6f, 18f, 12f, 18f, 16f)
                lineTo(20f, 18f)
                lineTo(4f, 18f)
                close()
                // Clapper
                moveTo(10f, 20f)
                curveTo(10f, 22f, 14f, 22f, 14f, 20f)
            }
        }.build()
    }

    // ---- Post-card interactions ----

    val ArrowUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowUp",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(12f, 4f); lineTo(12f, 20f)
                moveTo(6f, 10f); lineTo(12f, 4f); lineTo(18f, 10f)
            }
        }.build()
    }

    val ArrowDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowDown",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(12f, 4f); lineTo(12f, 20f)
                moveTo(6f, 14f); lineTo(12f, 20f); lineTo(18f, 14f)
            }
        }.build()
    }

    val Comment: ImageVector by lazy {
        ImageVector.Builder(
            name = "Comment",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Speech bubble
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 17f); lineTo(10f, 17f)
                lineTo(6f, 20f); lineTo(6f, 17f); lineTo(4f, 17f); close()
            }
        }.build()
    }

    // ---- Post detail / comment composer ----

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowBack",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f) {
                moveTo(20f, 12f); lineTo(5f, 12f)
                moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
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

    /** Photo frame with a mountain — "attach an image." */
    val Image: ImageVector by lazy {
        ImageVector.Builder(
            name = "Image",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
                moveTo(4f, 17f); lineTo(9f, 12f); lineTo(12f, 15f); lineTo(16f, 10f); lineTo(20f, 17f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 9f)
                arcTo(1.5f, 1.5f, 0f, true, true, 7f, 9f)
                arcTo(1.5f, 1.5f, 0f, true, true, 10f, 9f)
                close()
            }
        }.build()
    }

    /** Chevron pointing down — expands the collapsed post header in the composer. */
    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronDown",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
            }
        }.build()
    }

    /** Chevron pointing up — collapses the expanded post header. */
    val ChevronUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "ChevronUp",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                moveTo(6f, 15f); lineTo(12f, 9f); lineTo(18f, 15f)
            }
        }.build()
    }

    /** Curved reply arrow, shown on each comment row. */
    val Reply: ImageVector by lazy {
        ImageVector.Builder(
            name = "Reply",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(9f, 8f); lineTo(4f, 12f); lineTo(9f, 16f)
                moveTo(4f, 12f)
                lineTo(15f, 12f)
                curveTo(19f, 12f, 20f, 15f, 20f, 19f)
            }
        }.build()
    }

    /** A share arrow — curving out to the upper right. */
    val Share: ImageVector by lazy {
        ImageVector.Builder(
            name = "Share",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(4f, 12f)
                curveTo(4f, 8f, 8f, 6f, 14f, 6f)
                lineTo(14f, 3f)
                lineTo(21f, 8f)
                lineTo(14f, 13f)
                lineTo(14f, 10f)
                curveTo(9f, 10f, 6f, 12f, 5f, 18f)
                close()
            }
        }.build()
    }
}