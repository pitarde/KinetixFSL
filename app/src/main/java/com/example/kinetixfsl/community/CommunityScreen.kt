package com.example.kinetixfsl.community


import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.home.CommunityHomeScreen
import com.example.kinetixfsl.community.inbox.ChatScreen
import com.example.kinetixfsl.community.inbox.InboxScreen
import com.example.kinetixfsl.community.inbox.InboxViewModel
import com.example.kinetixfsl.community.model.Post
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

    /** Opens a post by id as an overlay — used by in-app share links in posts. */
    val openPostById: (String) -> Unit = { postId ->
        overlays.add(CommunityOverlay.PostById(postId))
    }

    /**
     * Opens a chat thread on the same overlay stack. Reached three ways — the
     * inbox list, a message notification, and the Message button on somebody's
     * profile — and all three want the same thing on back: whatever they were
     * looking at, not a reset to the feed.
     */
    val openChat: (String, String) -> Unit = { conversationId, otherUid ->
        overlays.add(CommunityOverlay.Chat(conversationId, otherUid))
    }

    /** Opens the Inbox (chat list + notifications) — reached from the drawer now. */
    val openInbox: () -> Unit = { overlays.add(CommunityOverlay.Inbox) }

    // The home-feed post whose 3-dot sheet is open, and one pending delete.
    var actionsPost: Post? by remember { mutableStateOf(null) }
    var pendingDeletePost: Post? by remember { mutableStateOf(null) }
    var pendingReportPost: Post? by remember { mutableStateOf(null) }
    val reportRepository = remember { ReportRepository() }
    val currentUid = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }

    // Shared list state so the bottom nav can scroll it to top.
    val feedListState = rememberLazyListState()

    /**
     * Hoisted to this level, not owned by the Inbox tab, because the bottom-nav
     * bell has to carry an unread badge whichever tab is showing — a ViewModel
     * created inside the Inbox screen would only exist while the user was
     * already looking at it, which is precisely when the badge is pointless.
     */
    val inboxViewModel = remember { InboxViewModel() }
    val inboxState by inboxViewModel.uiState.collectAsStateWithLifecycle()

    // The ViewModel — kept here so the bottom nav can call refresh().
    val feedViewModel = remember { CommunityFeedViewModel() }
    val feedState by feedViewModel.feedState.collectAsStateWithLifecycle()
    val userVotes by feedViewModel.userVotes.collectAsStateWithLifecycle()
    val communityAvatars by feedViewModel.communityAvatars.collectAsStateWithLifecycle()

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
                        // Already here — treat it as "back to a clean feed":
                        // drop every open overlay and close the drawer.
                        closeFrom(0)
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
                    onRecentCommunityClick = { communityId ->
                        scope.launch {
                            drawerState.close()
                            openCommunity(communityId)
                        }
                    },
                    onInboxClick = {
                        scope.launch {
                            drawerState.close()
                            openInbox()
                        }
                    },
                    inboxUnreadCount = inboxState.totalUnread,
                )
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            CommunityScaffold(
                onMenuClick = { scope.launch { drawerState.open() } },
                onCreateClick = { overlays.add(CommunityOverlay.Create) },
                onProfileClick = {
                    currentUid?.let { overlays.add(CommunityOverlay.Profile(it)) }
                },
                // Tapping the card or the comment button both land on the
                // detail screen — for a community post that's the same full
                // post-and-comments view an individual post gets, just with the
                // community's name and the poster credited underneath it.
                onCommentClick = { post -> overlays.add(CommunityOverlay.Detail(post)) },
                onPostClick = { post -> overlays.add(CommunityOverlay.Detail(post)) },
                onMediaClick = { post -> overlays.add(CommunityOverlay.Immersive(post)) },
                onAuthorClick = { uid -> overlays.add(CommunityOverlay.Profile(uid)) },
                // The community badge/header tap browses the community itself.
                onOpenCommunity = openCommunity,
                // Anything layered over the feed takes it out of the running
                // for bandwidth: no autoplay, no prefetch behind the overlay.
                isFeedActive = overlays.isEmpty(),
                onFeedMenuClick = { post -> actionsPost = post },
                onOpenPostById = openPostById,
                feedListState = feedListState,
                feedViewModel = feedViewModel,
            )

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
                                onMessageClick = {
                                    inboxViewModel.openConversationWithUid(overlay.userId) { id ->
                                        openChat(id, overlay.userId)
                                    }
                                },
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
                                    .statusBarsPadding()
                                    // An overlay has no bottom nav under it, so
                                    // inset the bottom too or the last row sits
                                    // behind the system navigation buttons.
                                    .navigationBarsPadding(),
                            )
                        }

                        is CommunityOverlay.PostById -> SharedPostScreen(
                            postId = overlay.postId,
                            onClose = close,
                            onAuthorClick = openProfile,
                            onEditPost = { post -> overlays.add(CommunityOverlay.Edit(post)) },
                            onCommunityClick = openCommunity,
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
                                communityAvatarUrl = communityAvatars[post.communityId],
                                onEdit = { overlays.add(CommunityOverlay.Edit(post)) },
                                onDelete = { feedViewModel.deletePost(post); close() },
                                onHide = { feedViewModel.hidePost(post.id); close() },
                                onOpenPostLink = openPostById,
                                onOpenCommunityLink = openCommunity,
                                onOpenProfileLink = openProfile,
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
                                    communityAvatarUrl = communityAvatars[post.communityId],
                                    onEdit = { overlays.add(CommunityOverlay.Edit(post)) },
                                    onDelete = { feedViewModel.deletePost(post); close() },
                                    onHide = { feedViewModel.hidePost(post.id); close() },
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
                                    onCommunityClick = openCommunity,
                                    communityAvatarUrl = communityAvatars[post.communityId],
                                    onClose = close,
                                )
                            }
                        }

                        is CommunityOverlay.Chat -> ChatScreen(
                            conversationId = overlay.conversationId,
                            recipientId = overlay.otherUid,
                            onClose = close,
                            onOpenProfile = openProfile,
                        )

                        is CommunityOverlay.Community -> {
                            // CommunityHomeScreen registers its own BackHandler
                            // wired to onClose, so it needs nothing extra here.
                            CommunityHomeScreen(
                                communityId = overlay.communityId,
                                onClose = close,
                            )
                        }

                        // CreatePostScreen registers its own BackHandler wired
                        // to onClose, same as Chat and Community above.
                        is CommunityOverlay.Create -> CreatePostScreen(
                            onClose = close,
                        )

                        is CommunityOverlay.Inbox -> {
                            // Unlike the screens above, InboxScreen was built to
                            // live inside a tab (no back handling of its own),
                            // so this overlay supplies it.
                            BackHandler(onBack = close)
                            InboxScreen(
                                viewModel = inboxViewModel,
                                onOpenConversation = openChat,
                                onOpenPost = openPostById,
                                onOpenProfile = openProfile,
                                // The community drawer is still there underneath
                                // this overlay — this just reopens it.
                                onMenuClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
                                    .statusBarsPadding()
                                    .navigationBarsPadding(),
                            )
                        }
                    }
                    }
                    }
                }
            }

            // The home-feed post's 3-dot sheet: own post gets Copy/Delete/Edit,
            // anyone else's gets Copy/Report/Share/Hide.
            val target = actionsPost
            if (target != null) {
                if (target.authorId == currentUid) {
                    PostActionsSheet(
                        onCopyText = {
                            copyPostText(context, target)
                            actionsPost = null
                        },
                        onDelete = {
                            pendingDeletePost = target
                            actionsPost = null
                        },
                        onEdit = {
                            actionsPost = null
                            overlays.add(CommunityOverlay.Edit(target))
                        },
                        onDismiss = { actionsPost = null },
                    )
                } else {
                    OtherPostActionsSheet(
                        onCopyText = {
                            copyPostText(context, target)
                            actionsPost = null
                        },
                        onReport = {
                            pendingReportPost = target
                            actionsPost = null
                        },
                        onShare = {
                            feedViewModel.share(context, target)
                            actionsPost = null
                        },
                        onHide = {
                            feedViewModel.hidePost(target.id)
                            actionsPost = null
                        },
                        onDismiss = { actionsPost = null },
                    )
                }
            }

            val deleting = pendingDeletePost
            if (deleting != null) {
                ConfirmDeleteDialog(
                    title = deleting.title,
                    onConfirm = {
                        feedViewModel.deletePost(deleting)
                        pendingDeletePost = null
                    },
                    onDismiss = { pendingDeletePost = null },
                )
            }

            val reporting = pendingReportPost
            if (reporting != null) {
                ReportReasonDialog(
                    subject = "post",
                    onSubmit = { reason ->
                        scope.launch { reportRepository.reportPost(reporting, reason) }
                        pendingReportPost = null
                        android.widget.Toast.makeText(
                            context,
                            "Thanks — we'll review this post.",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    },
                    onDismiss = { pendingReportPost = null },
                )
            }
        }
    }
}

@Composable
private fun CommunityScaffold(
    onMenuClick: () -> Unit,
    onCreateClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCommentClick: (Post) -> Unit,
    onPostClick: (Post) -> Unit,
    onMediaClick: (Post) -> Unit,
    onAuthorClick: (String) -> Unit,
    onOpenCommunity: (String) -> Unit,
    isFeedActive: Boolean,
    onFeedMenuClick: (Post) -> Unit,
    onOpenPostById: (String) -> Unit,
    feedListState: LazyListState,
    feedViewModel: CommunityFeedViewModel,
    modifier: Modifier = Modifier,
) {
    // No bottom nav any more — Profile, Create and Inbox each moved to their
    // own entry point (top bar or drawer), so the community screen is just the
    // feed and its own top bar, full height.
    // The search icon toggles the feed's inline search bar rather than it
    // always showing — cleaner default state, matching the reference design.
    var searchActive by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        CommunityTopBar(
            searchActive = searchActive,
            onMenuClick = onMenuClick,
            onSearchClick = {
                searchActive = !searchActive
                // Collapsing search returns the feed to normal — drop any query
                // so the post list isn't left filtered behind a hidden bar.
                if (!searchActive) feedViewModel.onSearchQueryChange("")
            },
            onCreateClick = onCreateClick,
            onProfileClick = onProfileClick,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        CommunityFeedContent(
            modifier = Modifier.weight(1f),
            viewModel = feedViewModel,
            listState = feedListState,
            onCommentClick = onCommentClick,
            onPostClick = onPostClick,
            onMediaClick = onMediaClick,
            onAuthorClick = onAuthorClick,
            showCommunityBadge = true,
            showSearchBar = searchActive,
            onOpenCommunity = onOpenCommunity,
            isFeedActive = isFeedActive,
            onMenuClick = onFeedMenuClick,
            onOpenPostById = onOpenPostById,
            // No bottom nav to cover the last post any more — inset it past
            // the system navigation bar, the same as a community's own feed.
            insetForBottomNav = true,
        )
    }
}

/**
 * Single row, matching the reference design: hamburger + title on the left,
 * then Search / Create / Profile icons together on the trailing edge. Search
 * toggles the feed's inline search bar; Create and Profile open as overlays
 * over the feed, so there's no "which screen am I on" state for this bar to
 * reflect.
 */
@Composable
private fun CommunityTopBar(
    searchActive: Boolean,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onCreateClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    // The whole bar eases in on first show, matching the other community screens.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "topBarEnter",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                alpha = enter
                translationY = (1f - enter) * -30f
            }
            .padding(start = 12.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CommunityIcons.Menu,
            contentDescription = "Open menu",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onMenuClick),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "KinetixFSL Community",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = CommunityIcons.Search,
            contentDescription = if (searchActive) "Hide search" else "Search",
            // Highlights while the search bar is open, so it reads as a toggle.
            tint = if (searchActive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onSearchClick),
        )
        Spacer(Modifier.width(18.dp))
        Icon(
            imageVector = CommunityIcons.CreatePost,
            contentDescription = "Create post",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onCreateClick),
        )
        Spacer(Modifier.width(18.dp))
        Icon(
            imageVector = CommunityIcons.Profile,
            contentDescription = "Your profile",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .size(30.dp)
                .clickable(onClick = onProfileClick),
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

    /**
     * One direct-message thread. Carries the other person's uid alongside the
     * thread id so the chat can watch their presence and mark messages read
     * without first waiting on the conversation document to load.
     */
    data class Chat(val conversationId: String, val otherUid: String) : CommunityOverlay

    /** The composer, reached from the top bar's pencil icon. */
    data object Create : CommunityOverlay

    /** Direct messages and notifications, reached from the drawer. */
    data object Inbox : CommunityOverlay
}
