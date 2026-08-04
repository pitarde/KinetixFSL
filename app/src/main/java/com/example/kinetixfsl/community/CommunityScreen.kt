package com.example.kinetixfsl.community


import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.home.CommunityHomeScreen
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.community.tabs.CommunityProfilePlaceholder
import com.example.kinetixfsl.community.tabs.NotificationsPlaceholder
import com.example.kinetixfsl.ui.home.KinetixDrawerContent
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    onNavigateToDashboard: () -> Unit,
    onStartCommunity: () -> Unit = {},
    onDiscoverCommunities: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(CommunityTab.HOME) }

    /**
     * Screens stacked over the community scaffold, bottom-first.
     *
     * A single nullable variable per screen can't express this: opening a
     * profile from a post, then a post from that profile, then another
     * profile, needs an arbitrary depth. Each entry is drawn over the one
     * before it, and back pops exactly one — so every button keeps working no
     * matter how deep the user goes.
     */
    val overlays = remember { mutableStateListOf<CommunityOverlay>() }

    /** Drops [index] and everything stacked above it. */
    fun closeFrom(index: Int) {
        while (overlays.size > index) overlays.removeAt(overlays.lastIndex)
    }

    /**
     * Opens a community as another layer on this same overlay stack, rather
     * than as a separate navigation destination — so back correctly unwinds to
     * whatever was showing before (a profile, a post, the feed), instead of
     * resetting this screen's own state (selected tab, other open overlays)
     * the way navigating away and back through the NavController would.
     */
    val openCommunity: (String) -> Unit = { communityId ->
        overlays.add(CommunityOverlay.Community(communityId))
    }

    // Shared list state so the bottom nav can scroll it to top.
    val feedListState = rememberLazyListState()

    // The ViewModel — kept here so the bottom nav can call refresh().
    val feedViewModel = remember { CommunityFeedViewModel() }
    val feedState by feedViewModel.feedState.collectAsStateWithLifecycle()
    val userVotes by feedViewModel.userVotes.collectAsStateWithLifecycle()

    /**
     * Swaps in the live copy of a post from the feed so counts keep ticking
     * while the detail screen or the immersive viewer is open.
     */
    fun liveCopyOf(post: Post): Post =
        (feedState as? FeedState.Success)?.posts?.firstOrNull { it.id == post.id } ?: post

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                KinetixDrawerContent(
                    onDashboardClick = {
                        scope.launch {
                            drawerState.close()
                            onNavigateToDashboard()
                        }
                    },
                    onGestureToTextClick = { scope.launch { drawerState.close() } },
                    onTextToGestureClick = { scope.launch { drawerState.close() } },
                    onCommunityClick = {
                        selectedTab = CommunityTab.HOME
                        scope.launch { drawerState.close() }
                    },
                    onStartCommunityClick = {
                        scope.launch {
                            drawerState.close()
                            onStartCommunity()
                        }
                    },
                    onDiscoverCommunitiesClick = {
                        scope.launch {
                            drawerState.close()
                            onDiscoverCommunities()
                        }
                    },
                    onAboutClick = { scope.launch { drawerState.close() } },
                )
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            CommunityScaffold(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onMenuClick = { scope.launch { drawerState.open() } },
                // Tapping the card or the comment button both land on the
                // detail screen — for a community post that's the same full
                // post-and-comments view an individual post gets, just with the
                // community's name and the poster credited underneath it.
                onCommentClick = { post -> overlays.add(CommunityOverlay.Detail(post)) },
                onPostClick = { post -> overlays.add(CommunityOverlay.Detail(post)) },
                onMediaClick = { post -> overlays.add(CommunityOverlay.Immersive(post)) },
                onEditPost = { post -> overlays.add(CommunityOverlay.Edit(post)) },
                onAuthorClick = { uid -> overlays.add(CommunityOverlay.Profile(uid)) },
                // The community badge/header tap browses the community itself.
                onOpenCommunity = openCommunity,
                // Anything layered over the feed takes it out of the running
                // for bandwidth: no autoplay, no prefetch behind the overlay.
                isFeedActive = overlays.isEmpty(),
                onProfileCommentClick = { item ->
                    overlays.add(CommunityOverlay.PostById(item.postId))
                },
                feedListState = feedListState,
                feedViewModel = feedViewModel,
                onScrollToTopAndRefresh = {
                    scope.launch {
                        feedListState.animateScrollToItem(0)
                        feedViewModel.refresh()
                    }
                },
            )

            // Back from any secondary tab returns to the feed rather than
            // leaving the community section entirely.
            BackHandler(enabled = selectedTab != CommunityTab.HOME) {
                selectedTab = CommunityTab.HOME
            }

            // Rendered bottom-first: each entry draws over the one below it,
            // and because Compose runs back handlers in reverse registration
            // order, the topmost screen's back always wins.
            overlays.forEachIndexed { index, overlay ->
                key(index) {
                    // Fades in when pushed, fades out before it's removed — a
                    // crossfade over whatever's beneath (feed or lower overlay),
                    // so opening and closing a screen is smooth rather than an
                    // instant pop. Fade (not a slide) so it never fights the
                    // immersive viewer's own drag-to-dismiss animation.
                    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                    // Once the exit animation settles, actually drop it.
                    LaunchedEffect(visible.isIdle) {
                        if (visible.isIdle && !visible.currentState) closeFrom(index)
                    }
                    val close = { visible.targetState = false }
                    // A background only paints — it doesn't absorb touches. Without
                    // this, any tap that missed an interactive element fell through
                    // the overlay and hit the feed underneath, so tapping a dead
                    // area of a profile opened whatever post was behind it.
                    val blockPassThrough = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* swallow */ }
                    // Explicitly typed: MutableList.add returns Boolean, which
                    // would otherwise infer (String) -> Boolean.
                    val openProfile: (String) -> Unit = { uid ->
                        overlays.add(CommunityOverlay.Profile(uid))
                    }

                    AnimatedVisibility(
                        visibleState = visible,
                        enter = fadeIn(tween(220)),
                        exit = fadeOut(tween(200)),
                    ) {
                    Box(modifier = blockPassThrough) {
                    when (overlay) {
                        is CommunityOverlay.Profile -> {
                            BackHandler(onBack = close)
                            CommunityProfileScreen(
                                userId = overlay.userId,
                                onPostClick = { post ->
                                    overlays.add(CommunityOverlay.Detail(post))
                                },
                                onEditPost = { post ->
                                    overlays.add(CommunityOverlay.Edit(post))
                                },
                                onCommentClick = { item ->
                                    overlays.add(CommunityOverlay.PostById(item.postId))
                                },
                                onUserClick = openProfile,
                                onOpenCommunity = openCommunity,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .statusBarsPadding(),
                            )
                        }

                        is CommunityOverlay.PostById -> SharedPostScreen(
                            postId = overlay.postId,
                            onClose = close,
                            onAuthorClick = openProfile,
                        )

                        is CommunityOverlay.Edit -> EditPostScreen(
                            post = liveCopyOf(overlay.post),
                            onClose = close,
                        )

                        is CommunityOverlay.Detail -> {
                            val post = liveCopyOf(overlay.post)
                            val commentVm = remember(post.id) {
                                CommentViewModel(postId = post.id)
                            }
                            PostDetailScreen(
                                post = post,
                                viewModel = commentVm,
                                userVote = userVotes[post.id],
                                onUpvote = { feedViewModel.vote(post.id, "up") },
                                onDownvote = { feedViewModel.vote(post.id, "down") },
                                onShare = { feedViewModel.share(context, post) },
                                onAuthorClick = openProfile,
                                onCommunityClick = openCommunity,
                                onClose = close,
                            )
                        }

                        is CommunityOverlay.Immersive -> {
                            val post = liveCopyOf(overlay.post)
                            val commentVm = remember(post.id) {
                                CommentViewModel(postId = post.id)
                            }
                            // A post with no media has nothing to show full
                            // screen, so it falls back to the detail screen.
                            if (post.mediaItems.isEmpty()) {
                                PostDetailScreen(
                                    post = post,
                                    viewModel = commentVm,
                                    userVote = userVotes[post.id],
                                    onUpvote = { feedViewModel.vote(post.id, "up") },
                                    onDownvote = { feedViewModel.vote(post.id, "down") },
                                    onShare = { feedViewModel.share(context, post) },
                                    onAuthorClick = openProfile,
                                    onCommunityClick = openCommunity,
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

                        is CommunityOverlay.Community -> {
                            // CommunityHomeScreen registers its own BackHandler
                            // wired to onClose, so it needs nothing extra here.
                            CommunityHomeScreen(
                                communityId = overlay.communityId,
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
}

@Composable
private fun CommunityScaffold(
    selectedTab: CommunityTab,
    onTabSelected: (CommunityTab) -> Unit,
    onMenuClick: () -> Unit,
    onCommentClick: (Post) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (Post) -> Unit,
    onEditPost: (Post) -> Unit,
    onAuthorClick: (String) -> Unit,
    onOpenCommunity: (String) -> Unit,
    isFeedActive: Boolean,
    onProfileCommentClick: (com.example.kinetixfsl.community.model.UserComment) -> Unit,
    feedListState: LazyListState,
    feedViewModel: CommunityFeedViewModel,
    onScrollToTopAndRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        CommunityTopBar(tab = selectedTab, onMenuClick = onMenuClick)

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                CommunityTab.HOME -> CommunityFeedContent(
                    viewModel = feedViewModel,
                    listState = feedListState,
                    onCommentClick = onCommentClick,
                    onPostClick = onPostClick,
                    onMediaClick = onMediaClick,
                    onAuthorClick = onAuthorClick,
                    showCommunityBadge = true,
                    onOpenCommunity = onOpenCommunity,
                    isFeedActive = isFeedActive,
                )
                CommunityTab.PROFILE -> CommunityProfileScreen(
                    onPostClick = onPostClick,
                    onEditPost = onEditPost,
                    onCommentClick = onProfileCommentClick,
                    onUserClick = onAuthorClick,
                    onOpenCommunity = onOpenCommunity,
                )
                CommunityTab.CREATE -> CreatePostScreen(
                    onClose = { onTabSelected(CommunityTab.HOME) },
                )
                CommunityTab.NOTIFICATIONS -> NotificationsPlaceholder()
            }
        }

        CommunityBottomNav(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                if (tab == CommunityTab.HOME && selectedTab == CommunityTab.HOME) {
                    onScrollToTopAndRefresh()
                } else {
                    onTabSelected(tab)
                }
            },
        )
    }
}

@Composable
private fun CommunityTopBar(
    tab: CommunityTab,
    onMenuClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
    ) {
        if (tab == CommunityTab.HOME) {
            Icon(
                imageVector = CommunityIcons.Menu,
                contentDescription = "Open menu",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(32.dp)
                    .clickable(onClick = onMenuClick),
            )
        } else {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}
@Composable
private fun CommunityBottomNav(
    selectedTab: CommunityTab,
    onTabSelected: (CommunityTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CommunityTab.entries.forEach { tab ->
                NavItem(
                    icon = tab.icon,
                    label = tab.label,
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                )
            }
        }
    }
}


@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )
    }
}

/**
 * One screen stacked over the community scaffold.
 *
 * Modelled as data rather than a set of booleans so the stack can nest to any
 * depth — profile, post, profile, post — with back unwinding it one step at a
 * time.
 */
private sealed interface CommunityOverlay {
    /** Somebody's community profile, their own or another user's. */
    data class Profile(val userId: String) : CommunityOverlay

    /** A post we already hold, opened from a feed card or a profile. */
    data class Detail(val post: Post) : CommunityOverlay

    /** A post known only by id, opened from a comment. Loaded on the way in. */
    data class PostById(val postId: String) : CommunityOverlay

    /** Full-screen media with the comments below the fold. */
    data class Immersive(val post: Post) : CommunityOverlay

    /** The editor, reached from your own post's menu. */
    data class Edit(val post: Post) : CommunityOverlay

    /** A community, opened from a post's badge or a profile's My Communities. */
    data class Community(val communityId: String) : CommunityOverlay
}
