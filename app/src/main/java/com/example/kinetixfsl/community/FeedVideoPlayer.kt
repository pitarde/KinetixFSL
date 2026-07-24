package com.example.kinetixfsl.community

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * Transforms a Cloudinary video URL to serve a compressed, mobile-optimized version.
 *
 * Original:  https://res.cloudinary.com/xxx/video/upload/v123/file.mp4
 * Optimized: https://res.cloudinary.com/xxx/video/upload/q_auto,w_480,f_mp4/v123/file.mp4
 *
 * This typically shrinks a 50MB phone video to 2-5MB — the single biggest
 * speed improvement possible. If the URL isn't from Cloudinary, returns it unchanged.
 */
private fun optimizeVideoUrl(url: String): String {
    val marker = "/video/upload/"
    val index = url.indexOf(marker)
    if (index == -1) return url // not a Cloudinary URL

    val insertAt = index + marker.length
    // q_auto = auto quality, w_480 = 480px wide (plenty for a feed card),
    // f_mp4 = ensure MP4 container for compatibility.
    return url.substring(0, insertAt) + "q_auto,w_480,f_mp4/" + url.substring(insertAt)
}

/**
 * Also optimize image URLs from Cloudinary for faster feed loading.
 */
fun optimizeImageUrl(url: String): String {
    val marker = "/image/upload/"
    val index = url.indexOf(marker)
    if (index == -1) return url

    val insertAt = index + marker.length
    // q_auto = auto quality, w_720 = 720px wide, f_auto = best format for device.
    return url.substring(0, insertAt) + "q_auto,w_720,f_auto/" + url.substring(insertAt)
}

/**
 * A video player for the community feed. Features:
 * - Cloudinary URL optimization (serves a ~90% smaller file).
 * - Loading spinner while the video buffers.
 * - Auto-plays when [isVisible] is true, pauses when false (scroll-driven).
 * - Muted by default — tap speaker icon to toggle.
 * - Loops continuously.
 * - Releases the ExoPlayer when the composable leaves composition.
 */
@Composable
fun FeedVideoPlayer(
    videoUrl: String,
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }

    // Optimize the URL before feeding it to ExoPlayer.
    val optimizedUrl = remember(videoUrl) { optimizeVideoUrl(videoUrl) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(optimizedUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                }
            })
            prepare()
        }
    }

    // Play/pause based on scroll visibility.
    if (isVisible) {
        exoPlayer.play()
    } else {
        exoPlayer.pause()
    }

    exoPlayer.volume = if (isMuted) 0f else 1f

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .clickable(onClick = onClick),
    ) {
        // Video surface.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        )

        // Loading spinner — visible while the video is buffering.
        if (isBuffering && isVisible) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        // Mute/unmute toggle.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { isMuted = !isMuted },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isMuted) SpeakerMutedIcon else SpeakerOnIcon,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ---- Speaker icons ----

private val SpeakerOnIcon: ImageVector by lazy {
    ImageVector.Builder("SpeakerOn", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 5f); lineTo(12f, 19f)
            lineTo(7f, 15f); lineTo(3f, 15f); close()
        }
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(15f, 9f)
            curveTo(16.5f, 10f, 16.5f, 14f, 15f, 15f)
            moveTo(17f, 6f)
            curveTo(20f, 8f, 20f, 16f, 17f, 18f)
        }
    }.build()
}

private val SpeakerMutedIcon: ImageVector by lazy {
    ImageVector.Builder("SpeakerMuted", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 5f); lineTo(12f, 19f)
            lineTo(7f, 15f); lineTo(3f, 15f); close()
        }
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f) {
            moveTo(16f, 9f); lineTo(22f, 15f)
            moveTo(22f, 9f); lineTo(16f, 15f)
        }
    }.build()
}