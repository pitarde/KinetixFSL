package com.example.kinetixfsl.community
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Post
import kotlin.math.abs
import com.example.kinetixfsl.ui.theme.KinetixGreen
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixMint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityFeedContent(
    modifier: Modifier = Modifier,
    viewModel: CommunityFeedViewModel = viewModel(),
    listState: LazyListState = rememberLazyListState(),
    onCommentClick: (Post) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
    /**
     * Tapping a post's image or video opens the immersive viewer (full-screen
     * media that reveals the comments on scroll), so the host screen owns that
     * overlay.
     */
    onMediaClick: (Post) -> Unit = {},
    /** Tapping a post author's avatar or name opens their profile. */
    onAuthorClick: (String) -> Unit = {},
    /**
     * False while a post detail, viewer or editor is open over the feed. The
     * feed then stops playing and stops prefetching, so the whole connection
     * goes to whatever the user actually opened.
     */
    isFeedActive: Boolean = true,
) {
    val state by viewModel.feedState.collectAsStateWithLifecycle()
    val userVotes by viewModel.userVotes.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val followingIds by viewModel.followingIds.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    LaunchedEffect(actionError) {
        val message = actionError ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.clearActionError()
    }
    val currentUid = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }

    val pullState = rememberPullToRefreshState()

    // Warm the next few posts' images so scrolling doesn't hit the network.
    FeedImagePrefetcher(
        listState = listState,
        posts = (state as? FeedState.Success)?.posts.orEmpty(),
        enabled = isFeedActive,
    )

    // Track which items are visible for video autoplay.
    /**
     * The single post nearest the middle of the viewport — the one the user is
     * actually looking at.
     *
     * Previously every partially-visible post counted as active, so two or
     * three videos loaded at once and fought over the connection. Exactly one
     * is active now, and it changes as the centre of the screen moves.
     */
    val activeMediaId by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .filter { it.key is String }
                .minByOrNull { abs((it.offset + it.size / 2) - centre) }
                ?.key as? String
        }
    }

    val visibleItemKeys by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
        }
    }


    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                KinetixRefreshIndicator(
                    isRefreshing = isRefreshing,
                    pullProgress = pullState.distanceFraction,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                item {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = viewModel::onSearchQueryChange,
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }

                when (val current = state) {
                    is FeedState.Loading -> item { LoadingRow() }
                    is FeedState.Error -> item { ErrorRow(message = current.message) }
                    is FeedState.Success -> {
                        if (current.posts.isEmpty()) {
                            item { EmptyRow() }
                        } else {
                            items(
                                items = current.posts,
                                key = { it.id },
                            ) { post ->
                                PostCard(
                                    post = post,
                                    userVote = userVotes[post.id],
                                    isVideoVisible = isFeedActive && post.id == activeMediaId,
                                    onUpvote = { viewModel.vote(post.id, "up") },
                                    onDownvote = { viewModel.vote(post.id, "down") },
                                    onComment = { onCommentClick(post) },
                                    onShare = { viewModel.share(context, post) },
                                    onMediaClick = { onMediaClick(post) },
                                    onAuthorClick = { onAuthorClick(post.authorId) },
                                    // Inert for now — the actions behind it
                                    // are a later piece of work.
                                    onMenuClick = {},
                                    isFollowing = post.authorId in followingIds,
                                    onToggleFollow = if (post.authorId == currentUid ||
                                        post.authorId.isBlank()
                                    ) {
                                        null
                                    } else {
                                        { viewModel.toggleFollow(post) }
                                    },
                                    onClick = { onPostClick(post) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    thickness = 1.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Pull-to-refresh indicator ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KinetixRefreshIndicator(
    isRefreshing: Boolean,
    pullProgress: Float,
    modifier: Modifier = Modifier,
) {
    if (pullProgress <= 0f && !isRefreshing) return

    val maxTravel = 80f
    val travel = if (isRefreshing) maxTravel else (pullProgress.coerceIn(0f, 1f) * maxTravel)

    Box(
        modifier = modifier
            .offset { IntOffset(0, travel.toInt().dp.roundToPx()) }
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isRefreshing) {
                BouncingDots()
            } else {
                StaticDots(scale = pullProgress.coerceIn(0f, 1f))
            }
        }
    }
}

@Composable
private fun BouncingDots() {
    val transition = rememberInfiniteTransition(label = "bounce")
    val dot1 by transition.animateFloat(0.5f, 1.2f, infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d1")
    val dot2 by transition.animateFloat(0.5f, 1.2f, infiniteRepeatable(tween(400, delayMillis = 150, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d2")
    val dot3 by transition.animateFloat(0.5f, 1.2f, infiniteRepeatable(tween(400, delayMillis = 300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "d3")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(KinetixIndigo, dot1); Dot(KinetixGreen, dot2); Dot(KinetixMint, dot3)
    }
}

@Composable
private fun StaticDots(scale: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(KinetixIndigo, scale); Dot(KinetixGreen, scale); Dot(KinetixMint, scale)
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color, scale: Float) {
    Box(Modifier.size(8.dp).scale(scale).clip(CircleShape).background(color))
}

// ---- Search, Loading, Empty, Error ----

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = SearchIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "Search posts, users...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            imageVector = ClearIcon,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onQueryChange("") },
                        )
                    }
                }
            },
        )
    }
}

private val SearchIcon: ImageVector by lazy {
    ImageVector.Builder("Search", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 2.2f,
        ) {
            // Circle: two half-arcs forming a full circle (center 11,11 r=7)
            moveTo(18f, 11f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 11f)
            arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, 18f, 11f)
            // Handle
            moveTo(16f, 16f)
            lineTo(21f, 21f)
        }
    }.build()
}

private val ClearIcon: ImageVector by lazy {
    ImageVector.Builder("Clear", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(androidx.compose.ui.graphics.Color.Black), strokeLineWidth = 2.2f) {
            moveTo(7f, 7f); lineTo(17f, 17f)
            moveTo(17f, 7f); lineTo(7f, 17f)
        }
    }.build()
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyRow() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No posts yet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Be the first to share something with the community.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorRow(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Couldn't load posts.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Post Card ----

/**
 * One post as the feed renders it. Shared with the profile so a user's own
 * posts look exactly the same there — [onMenuClick] adds the 3-dot control the
 * profile needs, and is absent in the feed.
 */
@Composable
internal fun PostCard(
    post: Post,
    userVote: String?,
    isVideoVisible: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onMediaClick: () -> Unit,
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null,
    /** Opens the author's profile from their avatar or name. */
    onAuthorClick: (() -> Unit)? = null,
    /** Null on your own posts — you can't follow yourself. */
    onToggleFollow: (() -> Unit)? = null,
    isFollowing: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        PostAuthorRow(
            post = post,
            onAuthorClick = onAuthorClick,
            trailing = if (onToggleFollow == null && onMenuClick == null) {
                null
            } else {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onToggleFollow != null) {
                            FollowPill(
                                isFollowing = isFollowing,
                                onClick = onToggleFollow,
                            )
                        }
                        if (onMenuClick != null) {
                            Spacer(Modifier.width(10.dp))
                            Icon(
                                imageVector = CommunityIcons.MoreVertical,
                                contentDescription = "Post options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable(onClick = onMenuClick),
                            )
                        }
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))

        Text(
            post.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (post.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            // Long bodies get clamped with a "see more" hint; tapping the card
            // opens the detail screen where the full body is shown.
            TruncatedBodyText(text = post.body, maxLines = 3)
        }

        if (!post.linkUrl.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(post.linkUrl, style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline), color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }

        // ---- Media: one item, or a swipeable carousel ----
        val mediaItems = post.mediaItems
        if (mediaItems.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PostMediaCarousel(
                media = mediaItems,
                isActive = isVideoVisible,
                onMediaClick = { onMediaClick() },
                posterUrl = post.previewUrl,
                blurData = post.previewBlur,
                height = 240.dp,
            )
        }

        Spacer(Modifier.height(12.dp))
        PostInteractionRow(
            post = post,
            userVote = userVote,
            onUpvote = onUpvote,
            onDownvote = onDownvote,
            onComment = onComment,
            onShare = onShare,
        )
    }
}
/**
 * "Follow" / "Followed" on a post's author row.
 *
 * Filled while you can still follow, outlined once you do — the same read-at-a-
 * glance convention every social app uses, so the state is obvious without
 * reading the label.
 */
@Composable
private fun FollowPill(
    isFollowing: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (isFollowing) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = if (isFollowing) "Followed" else "Follow",
            style = MaterialTheme.typography.labelMedium,
            color = if (isFollowing) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}
