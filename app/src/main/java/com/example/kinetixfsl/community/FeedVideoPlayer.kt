package com.example.kinetixfsl.community

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/**
 * How long a video must stay the centred item before it starts downloading.
 * Long enough to skip everything scrolled past, short enough that stopping on
 * a video feels immediate.
 */
private const val SETTLE_BEFORE_LOAD_MS = 180L

/**
 * Rewrites a bucket URL onto the Worker's own domain.
 *
 * Media used to be served from the bucket's `pub-*.r2.dev` hostname, which some
 * mobile carriers block outright — the app worked on WiFi and hung on a blurred
 * placeholder on data, because the request never reached Cloudflare at all.
 * `workers.dev` is not filtered the same way.
 *
 * Applied at read time rather than migrating Firestore, so every post already
 * created keeps working.
 */
fun mediaUrl(url: String): String {
    if (url.isBlank()) return url
    return try {
        val host = android.net.Uri.parse(url).host ?: return url
        if (!host.endsWith(".r2.dev")) return url
        val path = android.net.Uri.parse(url).path?.trimStart('/') ?: return url
        "${ShareLinks.MEDIA_BASE}$path"
    } catch (_: Exception) {
        url
    }
}

private fun optimizeVideoUrl(url: String): String {
    val rewritten = mediaUrl(url)
    val marker = "/video/upload/"
    val index = rewritten.indexOf(marker)
    if (index == -1) return rewritten
    val insertAt = index + marker.length
    return rewritten.substring(0, insertAt) + "q_auto:eco,w_480,f_mp4/" +
        rewritten.substring(insertAt)
}

fun optimizeImageUrl(url: String): String {
    val rewritten = mediaUrl(url)
    val marker = "/image/upload/"
    val index = rewritten.indexOf(marker)
    if (index == -1) return rewritten
    val insertAt = index + marker.length
    return rewritten.substring(0, insertAt) + "q_auto,w_720,f_auto/" +
        rewritten.substring(insertAt)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FeedVideoPlayer(
    videoUrl: String,
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Still shown until the first video frame arrives, so the slot isn't black. */
    posterUrl: String? = null,
    /**
     * Fill the slot with black before playback starts. Turned off when a blur
     * placeholder is drawn underneath, which would otherwise be hidden.
     */
    opaqueBackground: Boolean = true,
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var hasRenderedFrame by remember { mutableStateOf(false) }

    val optimizedUrl = remember(videoUrl) { optimizeVideoUrl(videoUrl) }

    // Note: no prepare() here. LazyColumn composes items beyond the viewport,
    // so preparing on creation meant several off-screen videos all downloading
    // at once. Loading now starts only when the item is actually visible.
    val exoPlayer = remember {
        buildCachedPlayer(context, feedMode = true).apply {
            setMediaItem(MediaItem.fromUri(optimizedUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedFrame = true
                }
            })
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            // Wait for the scroll to settle before touching the network. This
            // effect is cancelled the moment the item stops being the active
            // one, so videos flicked past never start loading at all — only
            // the one the user actually comes to rest on does.
            kotlinx.coroutines.delay(SETTLE_BEFORE_LOAD_MS)
            if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
            exoPlayer.play()
        } else {
            // stop(), not pause() — a paused player keeps filling its buffer,
            // so scrolling past a video would leave it quietly downloading.
            // The cache keeps what it already fetched, so coming back is cheap.
            exoPlayer.stop()
        }
    }

    exoPlayer.volume = if (isMuted) 0f else 1f

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    // Sizing comes from the caller so this works both as a standalone feed
    // player and as one page inside the media carousel.
    Box(
        modifier = modifier
            .then(
                if (opaqueBackground || hasRenderedFrame) {
                    Modifier.background(Color.Black)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
    ) {
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
            modifier = Modifier.fillMaxSize(),
        )

        // Poster sits on top until the decoder produces a real frame, so the
        // feed shows the video's content immediately rather than a black box
        // while it buffers.
        if (!hasRenderedFrame && !posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = mediaUrl(posterUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isBuffering && isVisible) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

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