package com.example.kinetixfsl.community.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.CommentViewModel
import com.example.kinetixfsl.community.CommunityFeedContent
import com.example.kinetixfsl.community.CommunityFeedViewModel
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.community.CommunityProfileScreen
import com.example.kinetixfsl.community.EditPostScreen
import com.example.kinetixfsl.community.FeedState
import com.example.kinetixfsl.community.ImmersivePostViewer
import com.example.kinetixfsl.community.PostDetailScreen
import com.example.kinetixfsl.community.ShareLinks
import com.example.kinetixfsl.community.SharedPostScreen
import com.example.kinetixfsl.community.model.Community
import com.example.kinetixfsl.community.model.Post

/**
 * A single community's home screen — the destination the creator lands on right
 * after "Create Community", and what anyone opens from Discover.
 *
 * Layout follows the design: a top bar (search / share / menu are clickable
 * placeholders for now), the community header with an expandable description,
 * the category-management strip, then the post feed.
 *
 * The category strip is only editable by the admin (the creator): each chip
 * carries an "x" that removes it, and an "Add" chip stands in for the not-yet-
 * designed add flow. Removal is live; Add is a placeholder.
 *
 * Note: the feed reuses the global community feed — posts aren't scoped to a
 * community yet, which is deferred work. The header and category tools are what
 * make this a per-community screen today.
 */
@Composable
fun CommunityHomeScreen(
    communityId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(communityId) { CommunityHomeViewModel(communityId) }
    val community by viewModel.community.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val isMember by viewModel.isMember.collectAsStateWithLifecycle()
    val context = LocalContext.current

    fun placeholder(label: String) {
        android.widget.Toast.makeText(context, "$label — coming soon", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Feed + overlay plumbing, mirroring CommunityScreen so posts stay fully
    // interactive (tap → detail, media → immersive, author → profile). Scoped
    // to this community, so the feed shows only its posts, ranked by vote score.
    val feedViewModel = remember(communityId) { CommunityFeedViewModel(communityId = communityId) }
    val feedState by feedViewModel.feedState.collectAsStateWithLifecycle()
    val userVotes by feedViewModel.userVotes.collectAsStateWithLifecycle()
    val searchQuery by feedViewModel.searchQuery.collectAsStateWithLifecycle()
    val feedListState = rememberLazyListState()

    // The top-bar magnifier opens an inline search field; typing filters this
    // community's feed the same way the Home Feed's search does.
    var searchActive by remember { mutableStateOf(false) }

    val overlays = remember { mutableStateListOf<HomeOverlay>() }
    fun closeFrom(index: Int) {
        while (overlays.size > index) overlays.removeAt(overlays.lastIndex)
    }

    fun liveCopyOf(post: Post): Post =
        (feedState as? FeedState.Success)?.posts?.firstOrNull { it.id == post.id } ?: post

    BackHandler(enabled = overlays.isEmpty()) {
        if (searchActive) {
            searchActive = false
            feedViewModel.onSearchQueryChange("")
        } else {
            onClose()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            CommunityHomeTopBar(
                searchActive = searchActive,
                query = searchQuery,
                onQueryChange = feedViewModel::onSearchQueryChange,
                onOpenSearch = { searchActive = true },
                onCloseSearch = {
                    searchActive = false
                    feedViewModel.onSearchQueryChange("")
                },
                onClose = onClose,
                onShare = {
                    community?.let { shareCommunity(context, it.id, it.name) }
                },
                onMenu = { placeholder("Menu") },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val current = community
            if (current == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                CommunityHeader(
                    community = current,
                    isMember = isMember,
                    isAdmin = isAdmin,
                    onJoin = viewModel::join,
                    onLeave = viewModel::leave,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                CategoryStrip(
                    categories = current.categories,
                    isAdmin = isAdmin,
                    onAdd = { placeholder("Add category") },
                    onRemove = viewModel::removeCategory,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                CommunityFeedContent(
                    modifier = Modifier.weight(1f),
                    viewModel = feedViewModel,
                    listState = feedListState,
                    onCommentClick = { post -> overlays.add(HomeOverlay.Detail(post)) },
                    onPostClick = { post -> overlays.add(HomeOverlay.Detail(post)) },
                    onMediaClick = { post -> overlays.add(HomeOverlay.Immersive(post)) },
                    onAuthorClick = { uid -> overlays.add(HomeOverlay.Profile(uid)) },
                    isFeedActive = overlays.isEmpty(),
                    // Posting happens from the Create tab; this feed needs no
                    // search bar of its own.
                    showSearchBar = false,
                )
            }
        }

        // Overlays, drawn bottom-first; back pops exactly one.
        overlays.forEachIndexed { index, overlay ->
            key(index) {
                val close = { closeFrom(index) }
                val blockPassThrough = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* swallow */ }
                val openProfile: (String) -> Unit = { uid ->
                    overlays.add(HomeOverlay.Profile(uid))
                }

                Box(modifier = blockPassThrough) {
                    when (overlay) {
                        is HomeOverlay.Profile -> {
                            BackHandler(onBack = close)
                            CommunityProfileScreen(
                                userId = overlay.userId,
                                onPostClick = { post -> overlays.add(HomeOverlay.Detail(post)) },
                                onEditPost = { post -> overlays.add(HomeOverlay.Edit(post)) },
                                onCommentClick = { item ->
                                    overlays.add(HomeOverlay.PostById(item.postId))
                                },
                                onUserClick = openProfile,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .statusBarsPadding(),
                            )
                        }

                        is HomeOverlay.PostById -> {
                            // SharedPostScreen has no back handler of its own, so
                            // register one here to pop just this overlay.
                            BackHandler(onBack = close)
                            SharedPostScreen(
                                postId = overlay.postId,
                                onClose = close,
                                onAuthorClick = openProfile,
                            )
                        }


                        is HomeOverlay.Edit -> EditPostScreen(
                            post = liveCopyOf(overlay.post),
                            onClose = close,
                        )

                        is HomeOverlay.Detail -> {
                            val post = liveCopyOf(overlay.post)
                            val commentVm = remember(post.id) { CommentViewModel(postId = post.id) }
                            PostDetailScreen(
                                post = post,
                                viewModel = commentVm,
                                userVote = userVotes[post.id],
                                onUpvote = { feedViewModel.vote(post.id, "up") },
                                onDownvote = { feedViewModel.vote(post.id, "down") },
                                onShare = { feedViewModel.share(context, post) },
                                onAuthorClick = openProfile,
                                onClose = close,
                            )
                        }

                        is HomeOverlay.Immersive -> {
                            val post = liveCopyOf(overlay.post)
                            val commentVm = remember(post.id) { CommentViewModel(postId = post.id) }
                            if (post.mediaItems.isEmpty()) {
                                PostDetailScreen(
                                    post = post,
                                    viewModel = commentVm,
                                    userVote = userVotes[post.id],
                                    onUpvote = { feedViewModel.vote(post.id, "up") },
                                    onDownvote = { feedViewModel.vote(post.id, "down") },
                                    onShare = { feedViewModel.share(context, post) },
                                    onAuthorClick = openProfile,
                                    onClose = close,
                                )
                            } else {
                                ImmersivePostViewer(
                                    post = post,
                                    viewModel = commentVm,
                                    userVote = userVotes[post.id],
                                    onUpvote = { feedViewModel.vote(post.id, "up") },
                                    onDownvote = { feedViewModel.vote(post.id, "down") },
                                    onShare = { feedViewModel.share(context, post) },
                                    onAuthorClick = openProfile,
                                    onClose = close,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun CommunityHomeTopBar(
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onMenu: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchActive) {
            // Search mode: back arrow collapses it, the field fills the bar.
            CircleIconButton(CommunityIcons.ArrowBack, "Close search", onCloseSearch)
            Spacer(Modifier.width(12.dp))
            TopBarSearchField(
                query = query,
                onQueryChange = onQueryChange,
                modifier = Modifier.weight(1f),
            )
        } else {
            CircleIconButton(CommunityIcons.Close, "Close", onClose)
            Spacer(Modifier.weight(1f))
            CircleIconButton(CommunityIcons.Search, "Search", onOpenSearch)
            Spacer(Modifier.width(12.dp))
            CircleIconButton(CommunityIcons.Share, "Share", onShare)
            Spacer(Modifier.width(12.dp))
            CircleIconButton(CommunityIcons.MoreVertical, "More", onMenu)
        }
    }
}

/** The inline search input the top-bar magnifier expands into. */
@Composable
private fun TopBarSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CommunityIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search posts, users...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = CommunityIcons.Close,
                contentDescription = "Clear",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

/**
 * Fires the system share sheet with a link to this community. Opening the link
 * drops the recipient straight into this community — see the `/c/` route in
 * ShareLinks and the deep-link handling in the NavHost.
 */
private fun shareCommunity(context: android.content.Context, communityId: String, name: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, name)
        putExtra(android.content.Intent.EXTRA_TEXT, ShareLinks.communityUrl(communityId))
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share community"))
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun CommunityHeader(
    community: Community,
    isMember: Boolean,
    isAdmin: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CommunityIcons.Profile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = community.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${community.memberCount} " +
                        if (community.memberCount == 1L) "member" else "members",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The admin is always a member and can't leave their own community,
            // so the toggle is only shown to everyone else.
            if (!isAdmin) {
                Spacer(Modifier.width(12.dp))
                JoinButton(isMember = isMember, onJoin = onJoin, onLeave = onLeave)
            }
        }

        if (community.description.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = community.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            // "See more.." only earns its place when there's more to see.
            if (community.description.length > 60 || community.description.contains('\n')) {
                Text(
                    text = if (expanded) "See less" else "See more..",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { expanded = !expanded },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Category management strip
// ---------------------------------------------------------------------------

@Composable
private fun CategoryStrip(
    categories: List<String>,
    isAdmin: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // "Add" chip — a placeholder for the not-yet-designed add flow. Admin only.
        if (isAdmin) {
            item {
                AddChip(onClick = onAdd)
            }
        }
        items(categories, key = { it }) { category ->
            CategoryChip(
                label = category,
                removable = isAdmin,
                onRemove = { onRemove(category) },
            )
        }
    }
}

/** The outlined "＋ Add" pill that opens the (future) add-category UI. */
@Composable
private fun AddChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CommunityIcons.Plus,
            contentDescription = "Add category",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Add",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A category pill. For the admin it carries an "x" that removes it. */
@Composable
private fun CategoryChip(
    label: String,
    removable: Boolean,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(start = 14.dp, end = if (removable) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (removable) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CommunityIcons.Close,
                    contentDescription = "Remove $label",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** One screen stacked over the community home, mirroring CommunityScreen. */
private sealed interface HomeOverlay {
    data class Profile(val userId: String) : HomeOverlay
    data class Detail(val post: Post) : HomeOverlay
    data class PostById(val postId: String) : HomeOverlay
    data class Immersive(val post: Post) : HomeOverlay
    data class Edit(val post: Post) : HomeOverlay
}

/** Join / Joined toggle shown in the header to non-admin viewers. */
@Composable
private fun JoinButton(
    isMember: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    if (isMember) {
        Box(
            modifier = Modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .clickable(onClick = onLeave)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Joined",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
    } else {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onJoin)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Join",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

