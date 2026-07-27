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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.ui.theme.KinetixError
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
) {
    val state by viewModel.feedState.collectAsStateWithLifecycle()
    val userVotes by viewModel.userVotes.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pullState = rememberPullToRefreshState()

    // Track which items are visible for video autoplay.
    val visibleItemKeys by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }.toSet()
        }
    }

    // Full-screen media state.
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }

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
                                    isVideoVisible = post.id in visibleItemKeys,
                                    onUpvote = { viewModel.vote(post.id, "up") },
                                    onDownvote = { viewModel.vote(post.id, "down") },
                                    onComment = { onCommentClick(post) },
                                    onShare = { viewModel.share(context, post) },
                                    onImageClick = { url -> fullScreenImageUrl = url },
                                    onVideoClick = { url -> fullScreenVideoUrl = url },
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

        // Full-screen overlay (image or video).
        if (fullScreenImageUrl != null || fullScreenVideoUrl != null) {
            FullScreenMediaViewer(
                imageUrl = fullScreenImageUrl,
                videoUrl = fullScreenVideoUrl,
                onClose = {
                    fullScreenImageUrl = null
                    fullScreenVideoUrl = null
                },
            )
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

@Composable
private fun PostCard(
    post: Post,
    userVote: String?,
    isVideoVisible: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AuthorRow(post = post)
        Spacer(Modifier.height(8.dp))

        Text(post.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)

        if (post.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(post.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }

        if (!post.linkUrl.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(post.linkUrl, style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline), color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }

        // ---- Media: image or video ----
        if (!post.videoUrl.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            FeedVideoPlayer(
                videoUrl = post.videoUrl,
                isVisible = isVideoVisible,
                onClick = { onVideoClick(post.videoUrl) },
            )
        } else if (!post.imageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = optimizeImageUrl(post.imageUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onImageClick(post.imageUrl) },
            )

        }

        Spacer(Modifier.height(12.dp))
        InteractionRow(post = post, userVote = userVote, onUpvote = onUpvote, onDownvote = onDownvote, onComment = onComment, onShare = onShare)
    }
}

@Composable
private fun AuthorRow(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!post.authorAvatarUrl.isNullOrBlank()) {
            AsyncImage(model = post.authorAvatarUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        } else {
            Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                Text(post.authorName.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(post.authorName.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        val timeLabel = post.createdAt.relativeToNow()
        val viewsLabel = if (post.viewCount > 0) "${post.viewCount.compact()} views" else null
        val meta = listOfNotNull(timeLabel.takeIf { it.isNotBlank() }, viewsLabel).joinToString(" · ")
        if (meta.isNotBlank()) { Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun InteractionRow(post: Post, userVote: String?, onUpvote: () -> Unit, onDownvote: () -> Unit, onComment: () -> Unit, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            InteractionButton(CommunityIcons.ArrowUp, post.upvoteCount.compact(), if (userVote == "up") KinetixGreen else MaterialTheme.colorScheme.onBackground, onUpvote)
            VerticalHairline()
            InteractionButton(CommunityIcons.ArrowDown, post.downvoteCount.compact(), if (userVote == "down") KinetixError else MaterialTheme.colorScheme.onBackground, onDownvote)
            VerticalHairline()
            InteractionButton(CommunityIcons.Comment, post.commentCount.compact(), MaterialTheme.colorScheme.onBackground, onComment)
        }
        Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)).padding(horizontal = 4.dp, vertical = 4.dp)) {
            InteractionButton(CommunityIcons.Share, post.shareCount.compact(), MaterialTheme.colorScheme.onBackground, onShare)
        }
    }
}

@Composable
private fun InteractionButton(icon: ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VerticalHairline() { Box(Modifier.width(1.dp).height(18.dp).background(MaterialTheme.colorScheme.outlineVariant)) }