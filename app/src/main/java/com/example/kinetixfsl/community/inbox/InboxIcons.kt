package com.example.kinetixfsl.community.inbox

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Glyphs the Inbox needs that the post screens didn't: the send arrow, the
 * video attachment, the read receipt, and one badge per notification type.
 *
 * Drawn as vector paths for the same reason as `CommunityIcons` — a handful of
 * shapes isn't worth an icon dependency. Every path is monochrome and tinted at
 * the call site, so they follow light and dark mode automatically.
 */
internal object InboxIcons {

    /** Paper plane — send the message. */
    val Send: ImageVector by lazy {
        ImageVector.Builder(
            name = "Send",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 20f)
                lineTo(21f, 12f)
                lineTo(3f, 4f)
                lineTo(3f, 10f)
                lineTo(15f, 12f)
                lineTo(3f, 14f)
                close()
            }
        }.build()
    }

    /** Film clapper — attach a clip. */
    val Video: ImageVector by lazy {
        ImageVector.Builder(
            name = "Video",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(3f, 7f); lineTo(15f, 7f); lineTo(15f, 17f); lineTo(3f, 17f); close()
                moveTo(15f, 11f); lineTo(21f, 8f); lineTo(21f, 16f); lineTo(15f, 13f); close()
            }
        }.build()
    }

    /** Play triangle in a ring — overlaid on a video message's thumbnail. */
    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(9f, 7f); lineTo(18f, 12f); lineTo(9f, 17f); close()
            }
        }.build()
    }

    /** Two ticks — "seen". One tick would read as "sent", which we don't show. */
    val Seen: ImageVector by lazy {
        ImageVector.Builder(
            name = "Seen",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f) {
                moveTo(1f, 13f); lineTo(6f, 18f); lineTo(15f, 7f)
                moveTo(9f, 15.5f); lineTo(11.5f, 18f); lineTo(21f, 7f)
            }
        }.build()
    }

    /** Pencil in a square — start a new conversation. */
    val NewMessage: ImageVector by lazy {
        ImageVector.Builder(
            name = "NewMessage",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(20f, 13f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                lineTo(4f, 4f)
                lineTo(11f, 4f)
                moveTo(16.5f, 2.5f)
                lineTo(20.5f, 6.5f)
                lineTo(11f, 16f)
                lineTo(7f, 17f)
                lineTo(8f, 13f)
                close()
            }
        }.build()
    }

    // ---- Notification type badges ----

    /** Heart — someone upvoted your post. */
    val Like: ImageVector by lazy {
        ImageVector.Builder(
            name = "Like",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 21f)
                curveTo(4f, 15f, 2f, 11f, 2f, 8.5f)
                curveTo(2f, 5.5f, 4.3f, 3.5f, 7f, 3.5f)
                curveTo(9f, 3.5f, 11f, 4.8f, 12f, 6.5f)
                curveTo(13f, 4.8f, 15f, 3.5f, 17f, 3.5f)
                curveTo(19.7f, 3.5f, 22f, 5.5f, 22f, 8.5f)
                curveTo(22f, 11f, 20f, 15f, 12f, 21f)
                close()
            }
        }.build()
    }

    /** At-sign — someone mentioned you. */
    val Mention: ImageVector by lazy {
        ImageVector.Builder(
            name = "Mention",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(16f, 12f)
                arcTo(4f, 4f, 0f, true, true, 8f, 12f)
                arcTo(4f, 4f, 0f, true, true, 16f, 12f)
                close()
                moveTo(16f, 8f)
                lineTo(16f, 13.5f)
                curveTo(16f, 16f, 20f, 16f, 20f, 12f)
                curveTo(20f, 6.5f, 16f, 4f, 12f, 4f)
                curveTo(7.6f, 4f, 4f, 7.6f, 4f, 12f)
                curveTo(4f, 16.4f, 7.6f, 20f, 12f, 20f)
                curveTo(14f, 20f, 15.5f, 19.6f, 17f, 18.5f)
            }
        }.build()
    }

    /** Megaphone — a community you joined posted something. */
    val Announcement: ImageVector by lazy {
        ImageVector.Builder(
            name = "Announcement",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(4f, 10f)
                lineTo(9f, 10f)
                lineTo(19f, 5f)
                lineTo(19f, 19f)
                lineTo(9f, 14f)
                lineTo(4f, 14f)
                close()
                moveTo(7f, 14f)
                lineTo(8.5f, 20f)
                lineTo(11.5f, 20f)
                lineTo(10f, 14f)
            }
        }.build()
    }

    /** Shield with a tick — account and security notices. */
    val System: ImageVector by lazy {
        ImageVector.Builder(
            name = "System",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
                moveTo(12f, 3f)
                lineTo(20f, 6f)
                lineTo(20f, 12f)
                curveTo(20f, 17f, 16f, 20f, 12f, 21f)
                curveTo(8f, 20f, 4f, 17f, 4f, 12f)
                lineTo(4f, 6f)
                close()
                moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f)
            }
        }.build()
    }
}
