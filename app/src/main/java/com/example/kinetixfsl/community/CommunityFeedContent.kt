package com.example.kinetixfsl.community
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Post
import kotlin.math.abs
import kotlinx.coroutines.delay
import com.example.kinetixfsl.ui.theme.KinetixGreen
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixMint
import com.example.kinetixfsl.ui.theme.KinetixPageBackground
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * List key for the optional collapsing header. Deliberately an Int, not a
 * String, so the String-keyed post logic (video autoplay, image prefetch) never
 * mistakes the header for a post.
 */
private const val COMMUNITY_HEADER_KEY = -1

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
    /**
     * The Home Feed shows a search bar at the top; a community's own feed
     * doesn't need one, so its host passes false to hide it.
     */
    showSearchBar: Boolean = true,
    /**
     * True in the Home Feed: community posts show the community name and a Join
     * pill. A community's own feed passes false — it's already that community.
     */
    showCommunityBadge: Boolean = false,
    /** Opens a community from a home-feed post's community header. */
    onOpenCommunity: (communityId: String) -> Unit = {},
    /** Tapping a post's 3-dot menu — the host decides what the sheet shows. */
    onMenuClick: (Post) -> Unit = {},
    /**
     * Reporting a post from the flag icon in its interaction row. The host owns
     * the reason dialog. Not shown on the signed-in user's own posts.
     */
    onReportPost: (Post) -> Unit = {},
    /**
     * Opens a post by id from an in-app share link pasted into a post. Community
     * and profile share links reuse [onOpenCommunity] and [onAuthorClick].
     */
    onOpenPostById: (postId: String) -> Unit = {},
    /**
     * Tapping a #hashtag on a post's body — null (the default) unless the host
     * owns a search bar to open. See [PostCard]'s parameter of the same name.
     */
    onHashtagClick: ((String) -> Unit)? = null,
    /**
     * Optional collapsing header rendered as the very first list item — the
     * community banner, name card and category strip on a community's own feed.
     * Because it lives inside the feed's own list it scrolls away naturally as
     * the user swipes up, letting the posts take over the screen. Null in the
     * home feed, which has no per-community header.
     */
    headerContent: (@Composable () -> Unit)? = null,
    /**
     * Whether to inset the feed's bottom past the system navigation bar. The
     * main feed leaves this false because its bottom nav already covers that
     * area; a community's own feed has no bottom nav, so its host passes true to
     * keep the last post clear of the on-screen back/home/recents buttons.
     *
     * Applied as a `navigationBarsPadding()` modifier on the list rather than a
     * computed contentPadding value — the latter was resolving to zero in some
     * hosts and letting the last card slip under the buttons.
     */
    insetForBottomNav: Boolean = false,
) {
    val state by viewModel.feedState.collectAsStateWithLifecycle()
    // Who's signed in — so a post's flag icon is hidden on the user's own posts.
    val currentUid = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }
    // Guarantees the skeleton is actually seen: on a fast connection (or a
    // warm Firestore cache) the real state can resolve to Success within a
    // frame or two, so without this the skeleton would never visibly show at
    // all. This composable is freshly created every time its host screen is —
    // e.g. navigating into Community from the drawer — so this timer restarts
    // exactly then, not on every recomposition or pull-to-refresh.
    var minSkeletonElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(500)
        minSkeletonElapsed = true
    }
    val userVotes by viewModel.userVotes.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val userResults by viewModel.userResults.collectAsStateWithLifecycle()
    val communityResults by viewModel.communityResults.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val joinedCommunityIds by viewModel.joinedCommunityIds.collectAsStateWithLifecycle()
    val communityAvatars by viewModel.communityAvatars.collectAsStateWithLifecycle()
    val ownedCommunityIds by viewModel.ownedCommunityIds.collectAsStateWithLifecycle()

    LaunchedEffect(actionError) {
        val message = actionError ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.clearActionError()
    }

    // Drives offline vs error wording, and auto-recovery: when the connection
    // returns while the feed is sitting on an error, retry without the user
    // having to tap anything.
    val isOnline by com.example.kinetixfsl.ui.components.rememberIsOnline()
    LaunchedEffect(isOnline, state) {
        if (isOnline && state is FeedState.Error) viewModel.retry()
    }

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
     *
     * Exception: when two or more posts are (near) fully on screen at once —
     * e.g. two short videos that both fit at the top of the feed — the centre
     * of the viewport falls in the gap between them and the lower one would
     * win, leaving the top video sitting there unplayed. Facebook-style, the
     * post the user reads first is the topmost, so a fully-visible post higher
     * up takes focus instead. Mid-scroll, when posts are clipped by the
     * viewport edges, this never triggers and the "nearest the centre" rule
     * still drives the hand-off from one video to the next.
     */
    val activeMediaId by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportStart = info.viewportStartOffset
            val viewportEnd = info.viewportEndOffset
            val centre = (viewportStart + viewportEnd) / 2
            val mediaItems = info.visibleItemsInfo.filter { it.key is String }

            val nearlyFullyVisible = mediaItems.filter { item ->
                if (item.size <= 0) return@filter false
                val shownTop = maxOf(item.offset, viewportStart)
                val shownBottom = minOf(item.offset + item.size, viewportEnd)
                (shownBottom - shownTop).toFloat() / item.size >= 0.9f
            }

            val chosen = if (nearlyFullyVisible.size >= 2) {
                nearlyFullyVisible.minByOrNull { it.offset }
            } else {
                mediaItems.minByOrNull { abs((it.offset + it.size / 2) - centre) }
            }
            chosen?.key as? String
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
                    // Off-white in light mode only — the white PostCards need
                    // something visibly darker underneath them to actually
                    // read as cards there. Dark mode keeps the normal theme
                    // background; KinetixPageBackground is a fixed light
                    // color, not theme-aware, so forcing it unconditionally
                    // would paint a light page behind a dark-mode feed.
                    .background(
                        if (isSystemInDarkTheme()) {
                            MaterialTheme.colorScheme.background
                        } else {
                            KinetixPageBackground
                        },
                    )
                    // Full-size background above paints under the nav bar; this
                    // then insets the list content past it. Only on a screen
                    // without a bottom nav of its own.
                    .then(if (insetForBottomNav) Modifier.navigationBarsPadding() else Modifier),
                contentPadding = PaddingValues(
                    // A collapsing header is full-bleed and sits flush at the top;
                    // the plain feed keeps its usual breathing room.
                    top = if (headerContent != null) 0.dp else 12.dp,
                    bottom = 12.dp,
                ),
            ) {
                // Rendered first so it scrolls away before the posts. The Int key
                // keeps it out of the String-keyed post logic (autoplay, prefetch).
                if (headerContent != null) {
                    item(key = COMMUNITY_HEADER_KEY) { headerContent() }
                }

                // Always present so it can animate in AND out on toggle — an
                // `if (showSearchBar)` would yank it from the list instantly,
                // killing the collapse animation. AnimatedVisibility expands it
                // down when Search is tapped and shrinks it back up when tapped
                // again; on a community's own feed showSearchBar is always false,
                // so it simply stays collapsed (zero height).
                item(key = "search-section") {
                    AnimatedVisibility(
                        visible = showSearchBar,
                        enter = expandVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                            fadeIn(tween(280)),
                        exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            fadeOut(tween(160)),
                    ) {
                        Column {
                            SearchBar(
                                query = searchQuery,
                                onQueryChange = viewModel::onSearchQueryChange,
                            )
                            // Matching users and communities — these aren't
                            // limited to what's in the loaded feed, unlike the
                            // post filter below, so someone who hasn't posted (or
                            // an empty new community) is still findable.
                            if (searchQuery.isNotBlank() && (userResults.isNotEmpty() || communityResults.isNotEmpty())) {
                                SearchDirectoryResults(
                                    users = userResults,
                                    communities = communityResults,
                                    onUserClick = onAuthorClick,
                                    onCommunityClick = onOpenCommunity,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (!minSkeletonElapsed) {
                    // Shown regardless of the real state — Loading, or even an
                    // already-resolved Success/Error — for the first 500ms
                    // after this feed appears, so it's never skipped outright.
                    item(key = "feed-skeleton") {
                        com.example.kinetixfsl.ui.components.FeedSkeleton()
                    }
                } else when (val current = state) {
                    is FeedState.Loading -> item(key = "feed-skeleton") {
                        com.example.kinetixfsl.ui.components.FeedSkeleton()
                    }
                    is FeedState.Error -> item(key = "feed-error") {
                        // No connection reads as offline; anything else is a
                        // generic error. Both offer Retry, and the feed auto-
                        // retries the moment the connection returns (below).
                        if (isOnline) {
                            com.example.kinetixfsl.ui.components.ErrorState(
                                onRetry = { viewModel.retry() },
                                message = current.message,
                            )
                        } else {
                            com.example.kinetixfsl.ui.components.OfflineState(
                                onRetry = { viewModel.retry() },
                            )
                        }
                    }
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
                                    onMenuClick = { onMenuClick(post) },
                                    onReport = if (post.authorId.isNotBlank() && post.authorId != currentUid) {
                                        { onReportPost(post) }
                                    } else {
                                        null
                                    },
                                    // No Follow button in the feed on purpose:
                                    // following happens from a user's profile,
                                    // so a second control here is redundant.
                                    onToggleFollow = null,
                                    // In the home feed a community post advertises
                                    // its community: the header becomes the
                                    // community, with a Join pill beside it.
                                    communityName = if (showCommunityBadge && post.communityId.isNotBlank()) {
                                        post.communityName.ifBlank { "Community" }
                                    } else {
                                        null
                                    },
                                    communityAvatarUrl = communityAvatars[post.communityId],
                                    isCommunityJoined = post.communityId in joinedCommunityIds,
                                    // No Join pill on your own community's posts —
                                    // you can't join a community you created.
                                    onToggleJoinCommunity = if (showCommunityBadge &&
                                        post.communityId.isNotBlank() &&
                                        post.communityId !in ownedCommunityIds
                                    ) {
                                        { viewModel.toggleJoinCommunity(post) }
                                    } else {
                                        null
                                    },
                                    onCommunityClick = if (showCommunityBadge && post.communityId.isNotBlank()) {
                                        { onOpenCommunity(post.communityId) }
                                    } else {
                                        null
                                    },
                                    onOpenPostLink = onOpenPostById,
                                    onOpenCommunityLink = onOpenCommunity,
                                    onOpenProfileLink = onAuthorClick,
                                    onClick = { onPostClick(post) },
                                    onHashtagClick = onHashtagClick,
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
    // Requested the moment this enters composition — which, wrapped in the
    // caller's AnimatedVisibility, is exactly when the search icon is tapped —
    // so the keyboard is already up and the field ready to type into, instead
    // of making the user tap the field a second time.
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

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
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
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
                                "Search posts, users, communities...",
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

/**
 * Matching users and communities, shown above the (already-filtered) post list
 * while a search is active. Each row opens the same profile/community overlay
 * a post's author chip or community badge would.
 */
@Composable
private fun SearchDirectoryResults(
    users: List<com.example.kinetixfsl.community.model.UserProfile>,
    communities: List<com.example.kinetixfsl.community.model.Community>,
    onUserClick: (String) -> Unit,
    onCommunityClick: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (users.isNotEmpty()) {
            Text(
                text = "Users",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            users.forEach { user ->
                SearchResultRow(
                    avatarUrl = user.avatarUrl,
                    title = user.displayName,
                    onClick = { onUserClick(user.uid) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (communities.isNotEmpty()) {
            Text(
                text = "Communities",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            communities.forEach { community ->
                SearchResultRow(
                    avatarUrl = community.avatarUrl,
                    title = community.name,
                    onClick = { onCommunityClick(community.id) },
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun SearchResultRow(avatarUrl: String?, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatarUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
private fun EmptyRow() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No posts yet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Be the first to share something with the community.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    /** Reports the post — a flag icon left of Share. Null on your own posts. */
    onReport: (() -> Unit)? = null,
    /** Opens the author's profile from their avatar or name. */
    onAuthorClick: (() -> Unit)? = null,
    /** Null on your own posts — you can't follow yourself. */
    onToggleFollow: (() -> Unit)? = null,
    isFollowing: Boolean = false,
    /**
     * When set, the card shows this community in place of the author and a Join
     * pill — the home feed uses it so community posts advertise the community.
     */
    communityName: String? = null,
    communityAvatarUrl: String? = null,
    isCommunityJoined: Boolean = false,
    onToggleJoinCommunity: (() -> Unit)? = null,
    onCommunityClick: (() -> Unit)? = null,
    /** In-app openers for the app's own share links pasted into a post. */
    onOpenPostLink: ((String) -> Unit)? = null,
    onOpenCommunityLink: ((String) -> Unit)? = null,
    onOpenProfileLink: ((String) -> Unit)? = null,
    /**
     * Tapping a #hashtag in the body — null (the default) everywhere except
     * the feed contexts that own a search bar, where it opens search for the
     * tag. See [HashtagText] for why a null here changes more than styling.
     */
    onHashtagClick: ((String) -> Unit)? = null,
) {
    val darkCard = isSystemInDarkTheme()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .then(
                if (darkCard) {
                    // A shadow barely reads against a dark background, so dark
                    // mode keeps its usual border-defined card instead.
                    Modifier
                } else {
                    Modifier.shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = KinetixIndigo.copy(alpha = 0.12f),
                        spotColor = KinetixIndigo.copy(alpha = 0.12f),
                    )
                },
            )
            .clip(RoundedCornerShape(20.dp))
            // Pure white in light mode, deliberately not colorScheme.surface —
            // the page behind it is KinetixPageBackground precisely so this
            // reads as a card sitting on top of it. Dark mode keeps the normal
            // theme surface; forcing white there would be jarring.
            .background(if (darkCard) MaterialTheme.colorScheme.surface else KinetixWhite)
            .then(
                if (darkCard) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        PostAuthorRow(
            post = post,
            onAuthorClick = onAuthorClick,
            communityName = communityName,
            communityAvatarUrl = communityAvatarUrl,
            onCommunityClick = onCommunityClick,
            trailing = if (onToggleFollow == null && onMenuClick == null && onToggleJoinCommunity == null) {
                null
            } else {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onToggleJoinCommunity != null) {
                            JoinPill(
                                isJoined = isCommunityJoined,
                                onClick = onToggleJoinCommunity,
                            )
                        } else if (onToggleFollow != null) {
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

        // A clamped body ("see more") hides the link until the post is opened;
        // a short body — or none — shows the link right here, since otherwise a
        // link-only post's link would never be seen in the feed.
        var bodyClamped by remember(post.id) { mutableStateOf(false) }
        if (post.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            // Long bodies get clamped with a "see more" hint; tapping the card
            // opens the detail screen where the full body is shown.
            TruncatedBodyText(
                text = post.body,
                maxLines = 3,
                onOverflowChange = { bodyClamped = it },
            )
        }

        val links = post.allLinks
        if (links.isNotEmpty() && (post.body.isBlank() || !bodyClamped)) {
            Spacer(Modifier.height(4.dp))
            PostLinks(
                links = links,
                maxLines = 1,
                onOpenPost = onOpenPostLink,
                onOpenCommunity = onOpenCommunityLink,
                onOpenProfile = onOpenProfileLink,
            )
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

        // Hashtags from the composer's box — tapping one opens search for it.
        if (post.hashtags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            PostHashtags(
                hashtags = post.hashtags,
                onHashtagClick = onHashtagClick,
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
            onReport = onReport,
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

/** "Join" / "Joined" for a community post's header in the home feed. */
@Composable
private fun JoinPill(
    isJoined: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (isJoined) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(
            text = if (isJoined) "Joined" else "Join",
            style = MaterialTheme.typography.labelMedium,
            color = if (isJoined) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}
