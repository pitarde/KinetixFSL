package com.example.kinetixfsl.community


import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.home.CommunityHomeScreen
import com.example.kinetixfsl.community.inbox.ChatScreen
import com.example.kinetixfsl.community.inbox.InboxScreen
import com.example.kinetixfsl.ui.theme.KinetixNavy
import com.example.kinetixfsl.ui.theme.KinetixPageBackground
import com.example.kinetixfsl.ui.theme.KinetixWhite
import com.example.kinetixfsl.community.inbox.InboxViewModel
import com.example.kinetixfsl.community.model.Community
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

    // Only the hamburger icon opens the drawer (gesturesEnabled below is
    // false while closed) — but once open, back should still close it, same
    // as tapping the scrim or swiping it away.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Icon-only to *open* — a swipe-from-edge (or anywhere, Compose's
        // default) to open the drawer was competing with this screen's own
        // gestures (search, dismissing a sheet). Enabled once it's actually
        // open so swiping it back closed still works, matching tapping the
        // scrim or pressing back.
        gesturesEnabled = drawerState.isOpen,
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
                                onClose = close,
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Off-white in light mode, matching the
                                    // Home Feed — see KinetixPageBackground.
                                    .background(
                                        if (isSystemInDarkTheme()) {
                                            MaterialTheme.colorScheme.background
                                        } else {
                                            KinetixPageBackground
                                        },
                                    )
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

                        // Removes itself the instant its own slide-down
                        // finishes (bypassing `close`/the outer fade-out) —
                        // going through `close` left the invisible full-
                        // screen blockPassThrough touch-blocker mounted for
                        // that fade's whole duration after the sheet had
                        // already finished disappearing, an input dead zone
                        // with nothing visibly happening in it.
                        is CommunityOverlay.Edit -> SlideUpScreen(onClose = { closeFrom(index) }) { dismiss ->
                            EditPostScreen(
                                post = liveCopyOf(overlay.post),
                                onClose = dismiss,
                            )
                        }

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
                            // A slide-in on top of the stack's own fade, so
                            // opening a community reads as a real "push" —
                            // matching the same screen's NavHost route (opened
                            // from Discover) rather than just a plain fade.
                            val slideIn = remember {
                                MutableTransitionState(false).apply { targetState = true }
                            }
                            AnimatedVisibility(
                                visibleState = slideIn,
                                enter = slideInHorizontally(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                                ) { it / 3 } + fadeIn(tween(280)),
                                exit = fadeOut(tween(120)),
                            ) {
                                CommunityHomeScreen(
                                    communityId = overlay.communityId,
                                    onClose = close,
                                )
                            }
                        }

                        // Slides up pop-up style and can be swiped back down —
                        // see SlideUpScreen. Removes itself directly (not via
                        // `close`) — see the comment on the Edit case above.
                        is CommunityOverlay.Create -> SlideUpScreen(onClose = { closeFrom(index) }) { dismiss ->
                            CreatePostScreen(onClose = dismiss)
                        }

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
                                // No statusBarsPadding here — InboxScreen's own
                                // top bar now paints behind the status bar and
                                // insets itself, the same way the Home Feed's does.
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background)
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
    // own entry point (top bar, quick-access row or drawer), so the community
    // screen is just the feed and its own top bar, full height.
    // The search icon toggles the feed's inline search bar rather than it
    // always showing — cleaner default state, matching the reference design.
    var searchActive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // This screen's top bar paints dark (KinetixNavy in light mode,
    // colorScheme.surface in dark mode) behind the status bar — see
    // CommunityTopBar — so it always wants light (white) status bar icons,
    // regardless of the app's own light/dark theme. See StatusBarLightIcons
    // for why this is safe to nest under (a profile or chat opened over this
    // feed asserts its own need without losing what this screen asked for).
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(light = true)

    // The signed-in user's own avatar for the top-bar profile button, live so a
    // picture change shows here at once. Name is only the fallback initial when
    // there's no photo, so Auth's static copy is fine for it.
    val myAvatarUrl by feedViewModel.myAvatarUrl.collectAsStateWithLifecycle()
    val myName = remember {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore('@') ?: "You"
    }

    // The quick-access circle row's communities — the 10 with the most members,
    // so the row surfaces what's actually popular. observeAllCommunities (not
    // observeJoinedCommunities, which only ever carries id + name for the
    // create-post picker) is a live snapshot listener with the full document —
    // avatarUrl and memberCount included — so both the picture and the ranking
    // update in real time the moment a community's profile or membership changes.
    val directoryRepository = remember { CommunityDirectoryRepository() }
    val allCommunities by directoryRepository.observeAllCommunities()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val topCommunities = remember(allCommunities) {
        allCommunities.sortedByDescending { it.memberCount }.take(10)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Off-white in light mode only, so the feed's pure-white cards
            // read as cards — see KinetixPageBackground and PostCard. Dark
            // mode keeps the normal theme background.
            .background(
                if (isSystemInDarkTheme()) MaterialTheme.colorScheme.background else KinetixPageBackground,
            ),
        // No statusBarsPadding here — CommunityTopBar insets its own content but
        // paints its background *behind* the status bar too, so the colored bar
        // and the notification area read as one continuous surface instead of a
        // plain strip above a colored one. See its own doc comment.
    ) {
        CommunityTopBar(
            searchActive = searchActive,
            onMenuClick = onMenuClick,
            onSearchClick = {
                searchActive = !searchActive
                if (!searchActive) {
                    // Collapsing search returns the feed to normal — drop any
                    // query so the post list isn't left filtered behind a
                    // hidden bar. The circle row re-expanding at the top of
                    // the list is invisible if the user had scrolled down
                    // while searching (typing, scrolling results), so this
                    // scrolls back to the top the same moment — otherwise
                    // it's "there" but off-screen until a manual swipe down.
                    feedViewModel.onSearchQueryChange("")
                    scope.launch { feedListState.animateScrollToItem(0) }
                }
            },
            onProfileClick = onProfileClick,
            profileAvatarUrl = myAvatarUrl,
            profileName = myName,
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
            // Tapping a #hashtag opens search for it, pre-filled with "#tag" —
            // the same query text search already matches against a post's raw
            // body (that's literally how the tag was typed) and its dedicated
            // hashtags field.
            onHashtagClick = { tag ->
                searchActive = true
                feedViewModel.onSearchQueryChange(hashtagQueryText(tag))
            },
            // Rendered as the feed's own scrolling header (rather than a fixed
            // sibling above it) so it swipes away with the posts on scroll-up,
            // same as a community's own banner/category strip. Also collapses
            // (with the same expand/shrink + fade the search bar itself uses)
            // the moment search opens, so the search field lands right under
            // the top bar instead of the circles staying pinned above it.
            headerContent = {
                Column {
                    AnimatedVisibility(
                        visible = !searchActive,
                        enter = expandVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                            fadeIn(tween(280)),
                        exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                            fadeOut(tween(160)),
                    ) {
                        CommunityQuickAccessRow(
                            communities = topCommunities,
                            onCreateClick = onCreateClick,
                            onCommunityClick = onOpenCommunity,
                        )
                    }
                }
            },
            // No bottom nav to cover the last post any more — inset it past
            // the system navigation bar, the same as a community's own feed.
            insetForBottomNav = true,
        )
    }
}

/**
 * Single row, matching the reference design: hamburger + title on the left,
 * then Search / Profile icons together on the trailing edge. Search toggles
 * the feed's inline search bar; Profile opens as an overlay over the feed, so
 * there's no "which screen am I on" state for this bar to reflect. Create Post
 * moved to the quick-access circle row below the bar — see
 * [CommunityQuickAccessRow] — so it isn't duplicated here.
 */
@Composable
private fun CommunityTopBar(
    searchActive: Boolean,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    profileAvatarUrl: String?,
    profileName: String,
) {
    // The whole bar eases in on first show, matching the other community screens.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "topBarEnter",
    )

    // KinetixNavy in light mode. Dark mode used to leave this transparent —
    // flush with the page — but that read as too flat, so it now lifts to
    // colorScheme.surface, the same lighter tone the feed's post cards use in
    // dark mode, so the bar reads as a raised surface there too.
    val dark = isSystemInDarkTheme()
    val barBackground = if (dark) MaterialTheme.colorScheme.surface else KinetixNavy
    val barContentColor = if (dark) MaterialTheme.colorScheme.onSurface else KinetixWhite

    // The background is painted here, on a Box that takes the status bar inset
    // as padding rather than being pushed below it — so the color runs all the
    // way to the physical top of the screen and the notification area reads as
    // part of the same bar instead of a plain gap above it.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBackground)
            .statusBarsPadding(),
    ) {
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
            tint = barContentColor,
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onMenuClick),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "KinetixFSL Community",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = barContentColor,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = CommunityIcons.Search,
            contentDescription = if (searchActive) "Hide search" else "Search",
            // Stays the same color as the rest of the bar's icons whether
            // active or not — switching to primary read as the icon going
            // dark/mismatched against the bar's white content.
            tint = barContentColor,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onSearchClick),
        )
        Spacer(Modifier.width(18.dp))
        // The user's own profile photo, not a generic glyph — and it's driven
        // by a live `users/{uid}` listener, so changing the picture updates it
        // here immediately. Falls back to the initial when there's no photo.
        // A white ring in light mode (the bar is dark navy there) and a dark
        // one in dark mode gives the avatar a clean edge against either bar.
        Avatar(
            avatarUrl = profileAvatarUrl,
            name = profileName,
            size = 30.dp,
            modifier = Modifier
                .clip(CircleShape)
                .border(1.5.dp, if (dark) MaterialTheme.colorScheme.background else KinetixWhite, CircleShape)
                .clickable(onClick = onProfileClick),
        )
    }
    }
}

/**
 * The Stories-style row of circles under the top bar: Create Post first (this
 * is where that action moved from the top bar's old pencil icon), then a
 * shortcut straight into each of the app's 10 most-joined communities — more
 * members reads as more popular, so this is what most people are likely
 * looking for, quicker than opening the drawer's community list. Hidden while
 * the feed's search bar is open, same as the feed's own header.
 */
@Composable
private fun CommunityQuickAccessRow(
    communities: List<Community>,
    onCreateClick: () -> Unit,
    onCommunityClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The white "coin" backing only makes sense against the light mode page —
    // dark mode uses the normal theme surface so a pure-white disc doesn't
    // glare against a dark background.
    val circleBase = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else KinetixWhite

    LazyRow(
        modifier = modifier.padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            QuickAccessCircle(label = "Create Post", onClick = onCreateClick) {
                // Same base + ring treatment as every community circle below —
                // Create Post used to be the only solid-filled one, which read
                // as a different kind of thing instead of just the first item
                // in the same row.
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(circleBase)
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CommunityIcons.Plus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
        items(communities, key = { it.id }) { community ->
            QuickAccessCircle(
                label = community.name,
                onClick = { onCommunityClick(community.id) },
            ) {
                // A backing "coin" behind the community's photo, so it shines
                // the same way the feed's cards do, with the ring around it.
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(circleBase)
                        .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Avatar(
                        avatarUrl = community.avatarUrl,
                        name = community.name,
                        size = 64.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessCircle(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(84.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
