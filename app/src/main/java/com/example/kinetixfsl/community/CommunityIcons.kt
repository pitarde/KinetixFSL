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

    /** A plus sign — "add", used on the category-management strip. */
    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "Plus",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }.build()
    }

    /** A magnifier — the search action in the community top bar. */
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
                moveTo(15.5f, 15.5f); lineTo(20f, 20f)
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

    // ---- Post actions (the 3-dot menu) ----

    /** Three stacked dots — opens the post actions sheet. */
    val MoreVertical: ImageVector by lazy {
        ImageVector.Builder(
            name = "MoreVertical",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.7f, 5f)
                arcTo(1.7f, 1.7f, 0f, true, true, 10.3f, 5f)
                arcTo(1.7f, 1.7f, 0f, true, true, 13.7f, 5f)
                close()
                moveTo(13.7f, 12f)
                arcTo(1.7f, 1.7f, 0f, true, true, 10.3f, 12f)
                arcTo(1.7f, 1.7f, 0f, true, true, 13.7f, 12f)
                close()
                moveTo(13.7f, 19f)
                arcTo(1.7f, 1.7f, 0f, true, true, 10.3f, 19f)
                arcTo(1.7f, 1.7f, 0f, true, true, 13.7f, 19f)
                close()
            }
        }.build()
    }

    /** Filled speech bubble — message this user. */
    val Message: ImageVector by lazy {
        ImageVector.Builder(
            name = "Message",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 3f)
                curveTo(6.5f, 3f, 2f, 6.6f, 2f, 11f)
                curveTo(2f, 13.5f, 3.5f, 15.7f, 5.8f, 17.2f)
                lineTo(5f, 21f)
                lineTo(8.9f, 19f)
                curveTo(9.9f, 19.2f, 10.9f, 19.3f, 12f, 19.3f)
                curveTo(17.5f, 19.3f, 22f, 15.7f, 22f, 11f)
                curveTo(22f, 6.6f, 17.5f, 3f, 12f, 3f)
                close()
            }
        }.build()
    }

    /** Outlined person with a plus — "follow". */
    val Follow: ImageVector by lazy {
        ImageVector.Builder(
            name = "Follow",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Head
                moveTo(13f, 8f)
                arcTo(3.5f, 3.5f, 0f, true, true, 6f, 8f)
                arcTo(3.5f, 3.5f, 0f, true, true, 13f, 8f)
                close()
                // Shoulders
                moveTo(3f, 20f)
                curveTo(3f, 15.5f, 6f, 14f, 9.5f, 14f)
                curveTo(11.5f, 14f, 13.2f, 14.5f, 14.4f, 15.6f)
                // Plus
                moveTo(18f, 8f); lineTo(18f, 14f)
                moveTo(15f, 11f); lineTo(21f, 11f)
            }
        }.build()
    }

    /** Two overlapping sheets — "copy text". */
    val CopyText: ImageVector by lazy {
        ImageVector.Builder(
            name = "CopyText",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Back sheet
                moveTo(8f, 4f); lineTo(19f, 4f); lineTo(19f, 16f)
                // Front sheet
                moveTo(5f, 8f); lineTo(15f, 8f); lineTo(15f, 20f); lineTo(5f, 20f); close()
            }
        }.build()
    }

    /** Bin with a lid — "delete". */
    val Delete: ImageVector by lazy {
        ImageVector.Builder(
            name = "Delete",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Lid
                moveTo(3f, 6f); lineTo(21f, 6f)
                moveTo(9f, 6f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 6f)
                // Body
                moveTo(5f, 6f); lineTo(6f, 20f); lineTo(18f, 20f); lineTo(19f, 6f)
                // Ribs
                moveTo(10f, 10f); lineTo(10f, 17f)
                moveTo(14f, 10f); lineTo(14f, 17f)
            }
        }.build()
    }

    /** Pencil over a line — "edit post". */
    val EditPost: ImageVector by lazy {
        ImageVector.Builder(
            name = "EditPost",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                // Pencil
                moveTo(16.5f, 3.5f)
                lineTo(20.5f, 7.5f)
                lineTo(9f, 19f)
                lineTo(4f, 20f)
                lineTo(5f, 15f)
                close()
                // Nib separator
                moveTo(14f, 6f); lineTo(18f, 10f)
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