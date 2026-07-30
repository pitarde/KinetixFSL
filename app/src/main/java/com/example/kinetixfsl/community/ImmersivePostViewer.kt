package com.example.kinetixfsl.community

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.kinetixfsl.KinetixApplication
import com.example.kinetixfsl.community.model.Post
import kotlinx.coroutines.launch

/**
 * The immersive media experience: tapping a post's image *or* video in the feed
 * opens it full screen on black, with the post and its comments sitting just
 * below the fold. Video behaves exactly like image here — same sheet, same
 * comments, same gestures.
 *
 * - Scroll up  → the sheet rises over the media and the comments come into view.
 * - Scroll down from the top → the whole thing slides away and closes.
 *
 * The sheet uses MaterialTheme colors so it matches light or dark mode; the
 * media area behind it stays black, which is what full-screen media should be.
 */
@Composable
fun ImmersivePostViewer(
    post: Post,
    viewModel: CommentViewModel,
    userVote: String?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Kept fresh so the nested-scroll object (remembered once) always calls the
    // current lambda.
    val currentOnClose by rememberUpdatedState(onClose)

    /** How far the content has been dragged down, in pixels. */
    val dragOffset = remember { Animatable(0f) }

    /** Past this much drag, releasing dismisses instead of springing back. */
    val dismissThresholdPx = remember(density) { with(density) { 130.dp.toPx() } }

    var isComposerOpen by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    /**
     * True once the sheet has started to rise. derivedStateOf keeps this from
     * recomposing on every scroll pixel — only when the boolean actually flips.
     */
    val isScrolled by remember { derivedStateOf { scrollState.value > 0 } }

    BackHandler(onBack = onClose)

    val dismissConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                return when {
                    // Pulling down while the content is already at the top —
                    // take the gesture and turn it into a dismiss drag.
                    dy > 0f && scrollState.value == 0 -> {
                        scope.launch { dragOffset.snapTo(dragOffset.value + dy * DRAG_RESISTANCE) }
                        Offset(0f, dy)
                    }
                    // Pushing back up while mid-drag — unwind the drag first,
                    // and only then let the list scroll.
                    dy < 0f && dragOffset.value > 0f -> {
                        val unwound = (dragOffset.value + dy * DRAG_RESISTANCE).coerceAtLeast(0f)
                        val usedPx = (dragOffset.value - unwound) / DRAG_RESISTANCE
                        scope.launch { dragOffset.snapTo(unwound) }
                        Offset(0f, -usedPx)
                    }
                    else -> Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (dragOffset.value <= 0f) return Velocity.Zero
                val flungDown = available.y > FLING_DISMISS_VELOCITY
                if (dragOffset.value > dismissThresholdPx || flungDown) {
                    currentOnClose()
                } else {
                    dragOffset.animateTo(0f, spring())
                }
                return available
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(dismissConnection),
    ) {
        // The media gets the whole screen — nothing overlaps it until the user
        // scrolls, so opening a post is purely the photo or video.
        val mediaHeight = maxHeight

        // Fade the backdrop out as the user drags the content away. Both reads
        // happen inside graphicsLayer lambdas so dragging never recomposes.
        val dismissProgress = { (dragOffset.value / (dismissThresholdPx * 3f)).coerceIn(0f, 1f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - dismissProgress() * 0.6f }
                .background(Color.Black),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset.value
                    alpha = 1f - dismissProgress() * 0.35f
                }
                .verticalScroll(scrollState),
        ) {
            // ---- The media, full screen, swipeable ----
            val mediaItems = post.mediaItems
            val mediaPager = rememberPagerState(pageCount = { mediaItems.size })

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(mediaHeight),
                contentAlignment = Alignment.Center,
            ) {
                HorizontalPager(
                    state = mediaPager,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val item = mediaItems[page]
                    if (item.isVideo) {
                        // Only the page you're looking at plays.
                        if (mediaPager.currentPage == page) {
                            ImmersiveVideo(videoUrl = item.url)
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // The light feed copy is almost certainly already
                            // cached from scrolling past this post, so it paints
                            // instantly and the full-resolution file replaces it
                            // as it arrives.
                            if (item.thumbUrl != null) {
                                AsyncImage(
                                    model = item.feedUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            AsyncImage(
                                model = item.url,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                if (mediaItems.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "${mediaPager.currentPage + 1}/${mediaItems.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // ---- The sheet: post, then comments ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                // Grab handle.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PostAuthorRow(post = post)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    if (post.body.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        TruncatedBodyText(text = post.body, maxLines = 3)
                    }
                    Spacer(Modifier.height(12.dp))
                    PostInteractionRow(
                        post = post,
                        userVote = userVote,
                        onUpvote = onUpvote,
                        onDownvote = onDownvote,
                        onComment = {
                            viewModel.cancelReply()
                            isComposerOpen = true
                        },
                        onShare = onShare,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = if (state.totalCount == 0) {
                        "Comments"
                    } else {
                        "Comments (${state.totalCount})"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                when {
                    state.isLoading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }

                    state.threads.isEmpty() -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No comments yet. Be the first!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> state.threads.forEach { thread ->
                        key(thread.comment.id) {
                            CommentThreadItem(
                                thread = thread,
                                isExpanded = thread.comment.id in state.expandedThreadIds,
                                onToggleReplies = { viewModel.toggleReplies(thread.comment.id) },
                                onReply = { comment ->
                                    viewModel.startReply(comment)
                                    isComposerOpen = true
                                },
                                onImageClick = { url -> fullScreenImageUrl = url },
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                // Room so the last comment clears the fixed bottom bar, and so
                // there's always something to scroll even on an empty post.
                Spacer(Modifier.height(120.dp))
            }
        }

        // ---- Close button ----
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CommunityIcons.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // ---- Comment bar: only once the sheet is in play ----
        // Hidden at rest so the media is completely unobstructed; slides up as
        // soon as the user starts scrolling toward the comments.
        AnimatedVisibility(
            visible = isScrolled,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        viewModel.cancelReply()
                        isComposerOpen = true
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    "Join the conversation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = CommunityIcons.Image,
                contentDescription = "Comment with an image",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(26.dp)
                    .clickable {
                        viewModel.cancelReply()
                        isComposerOpen = true
                    },
            )
        }
        }
    }

    if (isComposerOpen) {
        AddCommentScreen(
            post = post,
            viewModel = viewModel,
            onClose = { isComposerOpen = false },
        )
    }

    if (fullScreenImageUrl != null) {
        FullScreenMediaViewer(
            imageUrl = fullScreenImageUrl,
            onClose = { fullScreenImageUrl = null },
        )
    }
}

/**
 * Full-screen video for the immersive viewer: sound on, looping, with the
 * standard play/pause/seek controls.
 *
 * The player is released when the viewer closes. Controls are drawn by
 * ExoPlayer's own view, which only claims touches on the control strip itself —
 * dragging anywhere else still scrolls the sheet or dismisses the viewer.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ImmersiveVideo(videoUrl: String) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        buildCachedPlayer(context).apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
            prepare()
            play()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Slows the dismiss drag so it feels weighted rather than slippery. */
private const val DRAG_RESISTANCE = 0.65f

/** A downward fling faster than this dismisses regardless of distance. */
private const val FLING_DISMISS_VELOCITY = 1200f
