package com.example.kinetixfsl.community.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.example.kinetixfsl.community.hashtagQueryText
import com.example.kinetixfsl.community.PostDetailScreen
import com.example.kinetixfsl.community.SlideUpScreen
import com.example.kinetixfsl.ui.theme.KinetixWhite
import com.example.kinetixfsl.community.ShareLinks
import com.example.kinetixfsl.community.SharedPostScreen
import com.example.kinetixfsl.community.model.Community
import com.example.kinetixfsl.community.model.CommunityCategories
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
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    val editError by viewModel.editError.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // CommunityHomeTopBar paints a scrim-darkened banner behind the status bar
    // when the community has one (always dark, whatever the photo), or a
    // plain surfaceVariant strip when it doesn't (light in light mode, dark in
    // dark mode) — so light icons are only right for a banner or dark theme.
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(
        light = !community?.bannerUrl.isNullOrBlank() || androidx.compose.foundation.isSystemInDarkTheme(),
    )

    fun placeholder(label: String) {
        android.widget.Toast.makeText(context, "$label — coming soon", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Sheets driven from the header and the 3-dot menu.
    var showAddCategory by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // The photo the user picked, waiting to be cropped before upload.
    var cropRequest by remember { mutableStateOf<CropRequest?>(null) }

    // System photo pickers for the banner and profile picture. On pick, hand
    // off to the cropper rather than uploading straight away.
    val bannerPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? -> uri?.let { cropRequest = CropRequest(it, forBanner = true) } }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? -> uri?.let { cropRequest = CropRequest(it, forBanner = false) } }

    androidx.compose.runtime.LaunchedEffect(editError) {
        val message = editError ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.clearEditError()
    }

    // Feed + overlay plumbing, mirroring CommunityScreen so posts stay fully
    // interactive (tap → detail, media → immersive, author → profile). Scoped
    // to this community, so the feed shows only its posts, ranked by vote score.
    val feedViewModel = remember(communityId) { CommunityFeedViewModel(communityId = communityId) }
    val feedState by feedViewModel.feedState.collectAsStateWithLifecycle()
    val userVotes by feedViewModel.userVotes.collectAsStateWithLifecycle()
    val communityAvatars by feedViewModel.communityAvatars.collectAsStateWithLifecycle()
    val feedListState = rememberLazyListState()

    // The top-bar magnifier opens the feed's own inline search section (same
    // as the Home Feed's), which collapses the banner header out of the way
    // to make room for it — see the headerContent AnimatedVisibility below.
    var searchActive by remember { mutableStateOf(false) }
    val searchScope = androidx.compose.runtime.rememberCoroutineScope()

    val overlays = remember { mutableStateListOf<HomeOverlay>() }
    fun closeFrom(index: Int) {
        while (overlays.size > index) overlays.removeAt(overlays.lastIndex)
    }

    // The feed post whose 3-dot sheet is open, and one pending delete/report.
    var actionsPost by remember { mutableStateOf<Post?>(null) }
    var pendingDeletePost by remember { mutableStateOf<Post?>(null) }
    var pendingReportPost by remember { mutableStateOf<Post?>(null) }
    val reportScope = androidx.compose.runtime.rememberCoroutineScope()
    val reportRepository = remember { com.example.kinetixfsl.community.ReportRepository() }
    val currentUid = remember {
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }

    /**
     * Opens another community on this same overlay stack rather than as a
     * separate navigation destination, so back correctly unwinds to whatever
     * was showing before (this community's own profile view, its feed) instead
     * of losing that state — same reasoning as [CommunityScreen]'s equivalent.
     */
    val openCommunity: (String) -> Unit = { id -> overlays.add(HomeOverlay.Community(id)) }

    /** Opens a post by id as an overlay — used by in-app share links in posts. */
    val openPostById: (String) -> Unit = { id -> overlays.add(HomeOverlay.PostById(id)) }

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

    // How far the header has scrolled away, 0 (fully expanded) → 1 (collapsed).
    // Drives the top bar crossfade: as the banner and name card scroll up under
    // the bar, the bar fades in the blurred banner and the community name, so the
    // posts get the whole screen while the community stays identifiable.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val collapseProgress by remember {
        androidx.compose.runtime.derivedStateOf {
            if (feedListState.firstVisibleItemIndex > 0) {
                1f
            } else {
                val rangePx = with(density) { 140.dp.toPx() }
                (feedListState.firstVisibleItemScrollOffset / rangePx).coerceIn(0f, 1f)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Off-white in light mode only, matching the main Home Feed —
                // see KinetixPageBackground and PostCard. Dark mode keeps the
                // normal theme background.
                .background(
                    if (androidx.compose.foundation.isSystemInDarkTheme()) {
                        MaterialTheme.colorScheme.background
                    } else {
                        com.example.kinetixfsl.ui.theme.KinetixPageBackground
                    },
                ),
        ) {
            val current = community
            CommunityHomeTopBar(
                searchActive = searchActive,
                onSearchClick = {
                    searchActive = !searchActive
                    if (!searchActive) {
                        // Collapsing search returns the feed to normal — drop
                        // any query, and scroll back to the top so the header
                        // re-expanding there is actually visible instead of
                        // landing off-screen above wherever search had
                        // scrolled to (typing, browsing results).
                        feedViewModel.onSearchQueryChange("")
                        searchScope.launch { feedListState.animateScrollToItem(0) }
                    }
                },
                onClose = onClose,
                onShare = {
                    community?.let { shareCommunity(context, it.id, it.name) }
                },
                onMenu = { showOptions = true },
                // Only reveal the collapsed name/banner once there's a community
                // to name; while loading the bar stays plain.
                collapseProgress = if (current != null) collapseProgress else 0f,
                communityName = current?.name.orEmpty(),
                bannerUrl = current?.bannerUrl,
            )

            if (current == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                CommunityFeedContent(
                    modifier = Modifier.weight(1f),
                    viewModel = feedViewModel,
                    listState = feedListState,
                    // No bottom nav on a community screen, so the feed runs to
                    // the screen edge — inset the last post clear of the system
                    // navigation buttons.
                    insetForBottomNav = true,
                    onCommentClick = { post -> overlays.add(HomeOverlay.Detail(post)) },
                    onPostClick = { post -> overlays.add(HomeOverlay.Detail(post)) },
                    onMediaClick = { post -> overlays.add(HomeOverlay.Immersive(post)) },
                    onAuthorClick = { uid -> overlays.add(HomeOverlay.Profile(uid)) },
                    isFeedActive = overlays.isEmpty(),
                    // Same built-in search section (with its own auto-focus
                    // and expand/collapse animation) the Home Feed uses,
                    // instead of a field swapped into the top bar itself.
                    showSearchBar = searchActive,
                    onMenuClick = { post -> actionsPost = post },
                    onOpenCommunity = openCommunity,
                    onOpenPostById = openPostById,
                    // Tapping a #hashtag opens this community's own inline
                    // search (the top bar's magnifier), pre-filled with the tag.
                    onHashtagClick = { tag ->
                        searchActive = true
                        feedViewModel.onSearchQueryChange(hashtagQueryText(tag))
                    },
                    // The banner, name card and categories ride inside the feed
                    // so they scroll away on swipe-up — see [collapseProgress].
                    // They also collapse (with the same expand/shrink + fade
                    // the search bar itself uses) the moment search opens, so
                    // the search field lands right below the banner instead of
                    // the header staying pinned above it.
                    headerContent = {
                        AnimatedVisibility(
                            visible = !searchActive,
                            enter = expandVertically(animationSpec = tween(280, easing = FastOutSlowInEasing)) +
                                fadeIn(tween(280)),
                            exit = shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                fadeOut(tween(160)),
                        ) {
                        Column {
                            CommunityHeader(
                                community = current,
                                isMember = isMember,
                                isAdmin = isAdmin,
                                onJoin = viewModel::join,
                                onLeave = viewModel::leave,
                            )

                            CategoryStrip(
                                categories = current.categories,
                                isAdmin = isAdmin,
                                onAdd = { showAddCategory = true },
                                onRemove = viewModel::removeCategory,
                            )
                        }
                        }
                    },
                )
            }
        }

        // ---- Admin sheets ----
        val loaded = community
        if (showAddCategory && loaded != null) {
            AddCategorySheet(
                current = loaded.categories,
                onPick = { category ->
                    viewModel.addCategory(category)
                    showAddCategory = false
                },
                onDismiss = { showAddCategory = false },
            )
        }

        if (showOptions) {
            CommunityOptionsSheet(
                isAdmin = isAdmin,
                onEdit = {
                    showOptions = false
                    showEdit = true
                },
                onShare = {
                    showOptions = false
                    loaded?.let { shareCommunity(context, it.id, it.name) }
                },
                onDelete = {
                    showOptions = false
                    showDeleteConfirm = true
                },
                onDismiss = { showOptions = false },
            )
        }

        if (showDeleteConfirm && loaded != null) {
            DeleteCommunityDialog(
                communityName = loaded.name,
                isDeleting = isDeleting,
                onConfirm = { viewModel.deleteCommunity(onDeleted = onClose) },
                onDismiss = { if (!isDeleting) showDeleteConfirm = false },
            )
        }

        if (showEdit && loaded != null) {
            EditCommunitySheet(
                community = loaded,
                uploading = uploading,
                onChangeBanner = {
                    bannerPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onChangeAvatar = {
                    avatarPicker.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                        ),
                    )
                },
                onSaveName = viewModel::updateName,
                onDismiss = { showEdit = false },
            )
        }

        // Overlays, drawn bottom-first; back pops exactly one.
        overlays.forEachIndexed { index, overlay ->
            key(index) {
                // Fade in on push, fade out before removal — see CommunityScreen
                // for why a fade (not a slide) is used here.
                val visible = remember { MutableTransitionState(false).apply { targetState = true } }
                LaunchedEffect(visible.isIdle) {
                    if (visible.isIdle && !visible.currentState) closeFrom(index)
                }
                val close = { visible.targetState = false }
                val blockPassThrough = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* swallow */ }
                val openProfile: (String) -> Unit = { uid ->
                    overlays.add(HomeOverlay.Profile(uid))
                }

                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(220)),
                    exit = fadeOut(tween(200)),
                ) {
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
                                onOpenCommunity = openCommunity,
                                onClose = close,
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Off-white in light mode, matching the
                                    // Home Feed — see KinetixPageBackground.
                                    .background(
                                        if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                            MaterialTheme.colorScheme.background
                                        } else {
                                            com.example.kinetixfsl.ui.theme.KinetixPageBackground
                                        },
                                    )
                                    .statusBarsPadding()
                                    // Overlay — no bottom nav beneath it, so keep
                                    // its content clear of the nav buttons.
                                    .navigationBarsPadding(),
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
                                onEditPost = { post -> overlays.add(HomeOverlay.Edit(post)) },
                                onCommunityClick = openCommunity,
                            )
                        }


                        // Removes itself the instant its own slide-down
                        // finishes (bypassing `close`/the outer fade-out) —
                        // going through `close` left the invisible full-
                        // screen blockPassThrough touch-blocker mounted for
                        // that fade's whole duration after the sheet had
                        // already finished disappearing, an input dead zone
                        // with nothing visibly happening in it.
                        is HomeOverlay.Edit -> SlideUpScreen(onClose = { closeFrom(index) }) { dismiss ->
                            EditPostScreen(
                                post = liveCopyOf(overlay.post),
                                onClose = dismiss,
                            )
                        }

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
                                communityAvatarUrl = communityAvatars[post.communityId],
                                onEdit = { overlays.add(HomeOverlay.Edit(post)) },
                                onDelete = { feedViewModel.deletePost(post); close() },
                                onHide = { feedViewModel.hidePost(post.id); close() },
                                onOpenPostLink = openPostById,
                                onOpenCommunityLink = openCommunity,
                                onOpenProfileLink = openProfile,
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
                                    communityAvatarUrl = communityAvatars[post.communityId],
                                    onEdit = { overlays.add(HomeOverlay.Edit(post)) },
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
                                    communityAvatarUrl = communityAvatars[post.communityId],
                                    onClose = close,
                                )
                            }
                        }

                        is HomeOverlay.Community -> {
                            // CommunityHomeScreen registers its own BackHandler
                            // wired to onClose, so it needs nothing extra here.
                            // Recursing into itself is exactly what lets this
                            // nest to any depth — community, profile, another
                            // community, another profile — each layer closing
                            // back to the one beneath it.
                            // A slide-in on top of the stack's own fade, so
                            // opening a community reads as a real "push" —
                            // matching the same screen's NavHost route.
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
                    }
                }
                }
            }
        }

        // The feed post's 3-dot sheet: own post gets Copy/Delete/Edit; anyone
        // else's gets Copy/Report only — a community feed has no Share or Hide
        // (there's nowhere else here for a post to go).
        val target = actionsPost
        if (target != null) {
            if (target.authorId == currentUid) {
                com.example.kinetixfsl.community.PostActionsSheet(
                    onCopyText = {
                        com.example.kinetixfsl.community.copyPostText(context, target)
                        actionsPost = null
                    },
                    onDelete = {
                        pendingDeletePost = target
                        actionsPost = null
                    },
                    onEdit = {
                        actionsPost = null
                        overlays.add(HomeOverlay.Edit(target))
                    },
                    onDismiss = { actionsPost = null },
                )
            } else {
                com.example.kinetixfsl.community.OtherPostActionsSheet(
                    onCopyText = {
                        com.example.kinetixfsl.community.copyPostText(context, target)
                        actionsPost = null
                    },
                    onReport = {
                        pendingReportPost = target
                        actionsPost = null
                    },
                    onDismiss = { actionsPost = null },
                )
            }
        }

        val deleting = pendingDeletePost
        if (deleting != null) {
            com.example.kinetixfsl.community.ConfirmDeleteDialog(
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
            com.example.kinetixfsl.community.ReportReasonDialog(
                subject = "post",
                onSubmit = { reason ->
                    reportScope.launch { reportRepository.reportPost(reporting, reason) }
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

        // The photo cropper sits over everything while it's open.
        cropRequest?.let { req ->
            ImageCropScreen(
                imageUri = req.uri,
                aspectRatio = if (req.forBanner) 3f else 1f,
                title = if (req.forBanner) "Crop banner" else "Crop profile picture",
                outputWidth = if (req.forBanner) 1440 else 640,
                circle = !req.forBanner,
                onCancel = { cropRequest = null },
                onDone = { bitmap ->
                    if (req.forBanner) viewModel.updateBanner(bitmap) else viewModel.updateAvatar(bitmap)
                    cropRequest = null
                },
            )
        }
    }
}

/** A picked photo waiting to be cropped, and which slot it's for. */
private data class CropRequest(val uri: android.net.Uri, val forBanner: Boolean)

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

@Composable
private fun CommunityHomeTopBar(
    searchActive: Boolean,
    onSearchClick: () -> Unit,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onMenu: () -> Unit,
    collapseProgress: Float,
    communityName: String,
    bannerUrl: String?,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Full-bleed community banner behind the controls — it fills the whole
        // bar including the status-bar strip, so there's no white band at the
        // very top. The sharp image shows while expanded and crossfades to a
        // blurred version as the header scrolls away; a scrim keeps the white
        // controls and name legible over any banner, in both themes. Stays up
        // while searching too, now that the field lives below it (in the
        // feed's own collapsing header) rather than swapped into this bar.
        val hasBanner = !bannerUrl.isNullOrBlank()
        if (hasBanner) {
            coil.compose.AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - collapseProgress },
            )
            coil.compose.AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .blur(18.dp)
                    .graphicsLayer { alpha = collapseProgress },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(
                            alpha = 0.22f + 0.18f * collapseProgress,
                        ),
                    ),
            )
        } else {
            // No banner set — the same flat color the Home Feed's own top bar
            // uses, not a generic neutral strip, so a bannerless community
            // page still reads as the same bar design.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        if (androidx.compose.foundation.isSystemInDarkTheme()) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            com.example.kinetixfsl.ui.theme.KinetixNavy
                        },
                    ),
            )
        }

        // White controls read cleanly over the banner+scrim or the Home
        // Feed-matching navy fallback; only the neutral surfaceVariant case
        // (now gone) needed the on-surface tint, so it's white either way.
        val controlColor = if (hasBanner || !androidx.compose.foundation.isSystemInDarkTheme()) {
            Color.White
        } else {
            MaterialTheme.colorScheme.onSurface
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The controls clear the notch/status bar, but the blurred
                // banner (or the flat navy fallback) behind them still runs
                // to the very top edge. Ordered before height() so the inset
                // adds *on top of* the bar's own 56dp, matching the Home
                // Feed's own top bar rather than eating into it.
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(CommunityIcons.Close, "Close", onClose, tint = controlColor)
            // The community name slides in as the header collapses, sitting
            // between Close and the actions like the reference design.
            if (collapseProgress > 0f) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = communityName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = controlColor,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = collapseProgress },
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }
            // Toggles the feed's own search section below — see
            // CommunityFeedContent's showSearchBar — rather than swapping a
            // field into this row; stays the same color whether active or
            // not, so it never reads as going dark/mismatched against the bar.
            CircleIconButton(
                icon = CommunityIcons.Search,
                description = if (searchActive) "Hide search" else "Search",
                onClick = onSearchClick,
                tint = controlColor,
            )
            Spacer(Modifier.width(12.dp))
            CircleIconButton(CommunityIcons.Share, "Share", onShare, tint = controlColor)
            Spacer(Modifier.width(12.dp))
            CircleIconButton(CommunityIcons.MoreVertical, "More", onMenu, tint = controlColor)
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

    // The banner now lives full-bleed behind the top bar, so the header card
    // itself starts straight at the avatar/name row.
    Column(
        modifier = Modifier.background(
            // Pure white in light mode, matching the feed's own post cards —
            // dark mode keeps the normal theme surface.
            if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else KinetixWhite,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Community profile picture (falls back to a letter avatar).
                com.example.kinetixfsl.community.Avatar(
                    avatarUrl = community.avatarUrl,
                    name = community.name,
                    size = 56.dp,
                )
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
                    if (community.creatorName.isNotBlank()) {
                        Text(
                            text = "Created by ${community.creatorName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // The admin is always a member and can't leave their own
                // community, so Join/Joined is replaced by a plain badge.
                Spacer(Modifier.width(12.dp))
                if (isAdmin) {
                    YourCommunityBadge()
                } else {
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

/**
 * A category pill. Filled brand-indigo to match the reference design (legible in
 * both themes via primary/onPrimary). For the admin it carries an "x" that
 * removes it, drawn in a translucent-white circle so it reads on the fill.
 */
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
            .background(MaterialTheme.colorScheme.primary)
            .padding(start = 16.dp, end = if (removable) 8.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        if (removable) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CommunityIcons.Close,
                    contentDescription = "Remove $label",
                    tint = MaterialTheme.colorScheme.onPrimary,
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
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(1.5.dp, tint.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
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

    /** A different community, opened from a profile's My Communities sheet. */
    data class Community(val communityId: String) : HomeOverlay
}

/**
 * Stands in for the Join button on the creator's own community — there's
 * nothing to join or leave, so it's a plain, non-interactive label instead of
 * a button pretending to do something.
 */
@Composable
private fun YourCommunityBadge() {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "My Community",
            style = MaterialTheme.typography.labelSmall,
            // The same color a #hashtag uses (see HashtagText) — nothing
            // brand new, just reused for this label too.
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
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

// ---------------------------------------------------------------------------
// Admin sheets: options menu, add category, edit banner/profile
// ---------------------------------------------------------------------------

/** Shared bottom-sheet shell with a dimmed backdrop and a title row. */
@Composable
private fun BottomSheet(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {}
                // Without imePadding the keyboard simply covered sheets that hold
                // a text field — EditCommunitySheet's name field, in particular —
                // with no way to see what was typed. The scroll is what actually
                // lets the pushed-up content reach the field at the top instead
                // of clipping it against the shrunk viewport. Harmless on the
                // menu-only sheets that share this composable: no field, no
                // keyboard, no-op.
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CommunityIcons.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** The 3-dot menu's options. Edit and Delete are admin-only. */
@Composable
private fun CommunityOptionsSheet(
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(title = "Community", subtitle = null, onDismiss = onDismiss) {
        Column {
            if (isAdmin) {
                OptionRow(icon = CommunityIcons.EditPost, label = "Edit community", onClick = onEdit)
            }
            OptionRow(icon = CommunityIcons.Share, label = "Share community", onClick = onShare)
            if (isAdmin) {
                OptionRow(
                    icon = CommunityIcons.Delete,
                    label = "Delete community",
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

/**
 * Confirms deleting the whole community. To avoid an accidental tap wiping
 * everything, Delete only lights up once the creator has typed the exact
 * community name.
 */
@Composable
private fun DeleteCommunityDialog(
    communityName: String,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val canDelete = typed.trim() == communityName.trim() && !isDeleting

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete community?",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                Text(
                    text = "This permanently deletes \"$communityName\" and every post in it — " +
                        "from all members. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Type the community name to confirm:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = typed,
                        onValueChange = { if (!isDeleting) typed = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (typed.isEmpty()) {
                                Text(
                                    text = communityName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = onConfirm,
                enabled = canDelete,
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "Delete",
                        color = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
            }
        },
    )
}

/** Lets the admin add a category the community doesn't already carry. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddCategorySheet(
    current: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val available = CommunityCategories.ALL.filter { it !in current }
    BottomSheet(
        title = "Add a category",
        subtitle = "Tap one to file this community under it",
        onDismiss = onDismiss,
    ) {
        if (available.isEmpty()) {
            Text(
                text = "This community is already in every category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                available.forEach { category ->
                    val shape = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                            .clickable { onPick(category) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Reddit-style editor: change the wide banner and the round profile picture,
 * both imported from the device. Each change uploads and saves immediately, so
 * the header updates live behind the sheet.
 */
@Composable
private fun EditCommunitySheet(
    community: Community,
    uploading: CommunityHomeViewModel.Uploading,
    onChangeBanner: () -> Unit,
    onChangeAvatar: () -> Unit,
    onSaveName: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var nameInput by remember(community.id) { mutableStateOf(community.name) }
    val nameChanged = nameInput.trim().isNotEmpty() && nameInput.trim() != community.name.trim()

    BottomSheet(
        title = "Edit community",
        subtitle = "Name, banner and profile picture",
        onDismiss = onDismiss,
    ) {
        Column {
            // ---- Name ----
            Text(
                text = "Community name",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (nameChanged) {
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onSaveName(nameInput.trim()) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Banner ----
            Text(
                text = "Banner image",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = uploading == CommunityHomeViewModel.Uploading.NONE, onClick = onChangeBanner),
                contentAlignment = Alignment.Center,
            ) {
                if (!community.bannerUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = community.bannerUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (uploading == CommunityHomeViewModel.Uploading.BANNER) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    EditBadge("Change banner")
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---- Profile picture ----
            Text(
                text = "Profile picture",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    com.example.kinetixfsl.community.Avatar(
                        avatarUrl = community.avatarUrl,
                        name = community.name,
                        size = 64.dp,
                    )
                    if (uploading == CommunityHomeViewModel.Uploading.AVATAR) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                        .clickable(enabled = uploading == CommunityHomeViewModel.Uploading.NONE, onClick = onChangeAvatar)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Change profile",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EditBadge(label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CommunityIcons.EditPost,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

