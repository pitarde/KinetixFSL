package com.example.kinetixfsl.game.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * The single video call site for the whole quiz (§10). Branches on whether a
 * real clip has been bundled for [signId]:
 *  - a clip exists (`res/raw/<signId>.mp4`) → [RealSignVideoPlayer]
 *  - no clip yet                            → [VideoPlaceholderCard]
 *
 * Same signature, same layout, so dropping videos in later changes nothing here
 * or at any caller. Letter A already has `alpha_a.mp4`, so it plays for real.
 *
 * @param aspectRatio width / height of the media area (portrait mockups ≈ 0.82).
 *        Pass `null` to instead fill the size given by [modifier] (used by the
 *        matching grid, whose cells stretch to fill the screen).
 */
@Composable
fun SignVideo(
    signId: String,
    word: String,
    modifier: Modifier = Modifier,
    aspectRatio: Float? = 0.82f,
    autoPlay: Boolean = true,
) {
    val context = LocalContext.current
    val resId = remember(signId) { SignVideoAssets.rawResId(context, signId) }

    // Turn the caller's aspectRatio choice into a concrete size modifier once,
    // so both the real player and the placeholder share identical sizing.
    val sizeModifier = if (aspectRatio != null) {
        modifier.fillMaxWidth().aspectRatio(aspectRatio)
    } else {
        modifier.fillMaxSize()
    }

    if (resId != 0) {
        RealSignVideoPlayer(resId = resId, autoPlay = autoPlay, modifier = sizeModifier)
    } else {
        VideoPlaceholderCard(word = word, modifier = sizeModifier)
    }
}

/** Plays a bundled `res/raw` clip, looping and muted, in a rounded card. */
@Composable
fun RealSignVideoPlayer(
    resId: Int,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
) {
    val context = LocalContext.current
    val exoPlayer = remember(resId) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${context.packageName}/$resId")))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = autoPlay
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp)),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Temporary stand-in for a real sign video: a rounded card in the app's light
 * lavender surface with the sign's word centred in bold. Adapted from the spec's
 * `VideoPlaceholderCard.kt` to use theme tokens (so it works in dark mode) and
 * to be just the media area — the surrounding buttons belong to each screen.
 */
@Composable
fun VideoPlaceholderCard(
    word: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = word,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}
