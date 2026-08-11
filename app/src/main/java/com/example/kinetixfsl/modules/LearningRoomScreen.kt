package com.example.kinetixfsl.modules

import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.kinetixfsl.modules.model.SignCategory

/** Playback speeds the "slow" toggle cycles through. */
private val SPEEDS = floatArrayOf(1f, 0.75f, 0.5f, 0.25f)

/**
 * Learning Room — a video-first tutorial for one sign.
 *
 * The learner watches a short front-facing clip of the sign and can Loop it,
 * slow it down, or Mirror it before tapping "Practice the sign".
 *
 * ## Offline videos
 * Each clip is bundled in `res/raw`, named exactly as the sign's id
 * (e.g. `alpha_a.mp4` for id `alpha_a`). Because they ship inside the APK,
 * playback is fully offline. A sign with no clip yet shows a placeholder.
 */
@Composable
fun LearningRoomScreen(
    category: SignCategory,
    signIndex: Int,
    onBack: () -> Unit,
    onNext: (() -> Unit)?,
    onPractice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sign = category.signs.getOrNull(signIndex) ?: return
    val displayPrefix = getSignDisplayPrefix(category.id)
    val title = "$displayPrefix ${sign.name}".trim()

    val context = LocalContext.current
    val inspecting = LocalInspectionMode.current

    // Resolve the offline clip: res/raw/<sign.id>.mp4  (0 = not added yet).
    val videoResId = remember(sign.id) {
        context.resources.getIdentifier(sign.id, "raw", context.packageName)
    }
    val hasVideo = videoResId != 0

    // ── Player (skipped in @Preview, which has no real runtime) ──
    val player = remember {
        if (inspecting) null else ExoPlayer.Builder(context).build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var loop by remember { mutableStateOf(true) }
    var speedIndex by remember { mutableIntStateOf(0) }
    var mirror by remember { mutableStateOf(false) }

    // Load the clip once.
    LaunchedEffect(videoResId) {
        val p = player ?: return@LaunchedEffect
        if (!hasVideo) return@LaunchedEffect
        p.setMediaItem(
            MediaItem.fromUri("android.resource://${context.packageName}/$videoResId")
        )
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.prepare()
        p.playWhenReady = true
    }

    // Reflect play state + duration.
    DisposableEffect(player) {
        val p = player
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) durationMs = p?.duration?.coerceAtLeast(0L) ?: 0L
            }
        }
        p?.addListener(listener)
        onDispose {
            p?.removeListener(listener)
            p?.release()
        }
    }

    // Apply loop + speed when toggled.
    LaunchedEffect(loop) {
        player?.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    LaunchedEffect(speedIndex) {
        player?.playbackParameters = PlaybackParameters(SPEEDS[speedIndex])
    }

    // Poll position for the scrubber while playing.
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            player?.let {
                positionMs = it.currentPosition
                if (durationMs <= 0L) durationMs = it.duration.coerceAtLeast(0L)
            }
            kotlinx.coroutines.delay(200)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        // ── Top bar: back + Next ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(
                imageVector = ModulesIcons.ArrowBack,
                contentDescription = "Go back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(28.dp)
                    .clickable(onClick = onBack),
            )
            if (onNext != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(20.dp),
                        )
                        .clickable(onClick = onNext)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Next",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Title + "x of N" ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${signIndex + 1} of ${category.signCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Video card ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (hasVideo && player != null) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { tv -> player.setVideoTextureView(tv) }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        // Mirror horizontally when enabled (TextureView flips reliably).
                        .graphicsLayer { scaleX = if (mirror) -1f else 1f },
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Tutorial video coming soon",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Timer pill (top-right).
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = formatTime(positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Scrubber ──
        val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs) else 0f
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { v ->
                if (durationMs > 0) {
                    val target = (v * durationMs).toLong()
                    positionMs = target
                    player?.seekTo(target)
                }
            },
            enabled = hasVideo,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )

        // ── Transport: restart + play/pause ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            val iconColor = MaterialTheme.colorScheme.onBackground
            ControlButton(enabled = hasVideo, onClick = {
                player?.seekTo(0); player?.play()
            }) { drawRestart(iconColor) }

            Spacer(Modifier.size(28.dp))

            ControlButton(enabled = hasVideo, onClick = {
                val p = player ?: return@ControlButton
                if (p.isPlaying) p.pause() else {
                    if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                    p.play()
                }
            }) { if (isPlaying) drawPause(iconColor) else drawPlay(iconColor) }
        }

        Spacer(Modifier.height(8.dp))

        // ── Toggles: Loop / speed / Mirror ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            ToggleChip(label = "Loop", selected = loop) { loop = !loop }
            ToggleChip(
                label = "${trimZeros(SPEEDS[speedIndex])}x",
                selected = speedIndex != 0,
            ) { speedIndex = (speedIndex + 1) % SPEEDS.size }
            ToggleChip(label = "Mirror", selected = mirror) { mirror = !mirror }
        }

        Spacer(Modifier.height(14.dp))

        // ── Page dots ──
        ProgressDots(
            current = signIndex,
            total = category.signCount,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.weight(1f))

        // ── Practice button ──
        Button(
            onClick = onPractice,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                text = "Practice the sign",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Toggle chip ─────────────────────────────────────────────────

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Transport buttons (Canvas icons, no extra deps) ─────────────

@Composable
private fun ControlButton(
    enabled: Boolean,
    size: Dp = 46.dp,
    onClick: () -> Unit,
    draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.42f)) { draw() }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlay(color: Color) {
    val w = size.width; val h = size.height
    val p = Path().apply {
        moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h); close()
    }
    drawPath(p, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPause(color: Color) {
    val w = size.width; val h = size.height
    drawRect(color, Offset(w * 0.12f, 0f), Size(w * 0.26f, h))
    drawRect(color, Offset(w * 0.62f, 0f), Size(w * 0.26f, h))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRestart(color: Color) {
    val w = size.width; val h = size.height
    // Vertical bar + left-pointing triangle = "restart / skip to start".
    drawRect(color, Offset(0f, 0f), Size(w * 0.16f, h))
    val p = Path().apply {
        moveTo(w, 0f); lineTo(w * 0.22f, h / 2f); lineTo(w, h); close()
    }
    drawPath(p, color)
}

// ── Page dots ───────────────────────────────────────────────────

@Composable
private fun ProgressDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    if (total <= 1) return

    // A fixed, compact cluster of at most MAX_DOTS. For long lists it becomes a
    // sliding window centred on the current sign, with the edge dots shrunk to
    // signal "there are more". So 4 signs and 28 signs look the same — a small
    // row of dots, never a long loading-style bar.
    val maxDots = 7
    val start: Int
    val count: Int
    if (total <= maxDots) {
        start = 0
        count = total
    } else {
        start = (current - maxDots / 2).coerceIn(0, total - maxDots)
        count = maxDots
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (j in 0 until count) {
            val idx = start + j
            val moreBefore = start > 0 && j == 0
            val moreAfter = (start + count) < total && j == count - 1
            Dot(active = idx == current, small = moreBefore || moreAfter)
        }
    }
}

@Composable
private fun Dot(active: Boolean, small: Boolean) {
    when {
        active -> Box(
            modifier = Modifier
                .height(6.dp)
                .width(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        small -> Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)),
        )
        else -> Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)),
        )
    }
}

// ── Helpers ─────────────────────────────────────────────────────

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

private fun trimZeros(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString()
    else v.toString().trimEnd('0').trimEnd('.')

private fun getSignDisplayPrefix(categoryId: String): String = when (categoryId) {
    "alphabet" -> "Letter"
    "numbers" -> "Number"
    else -> ""
}
