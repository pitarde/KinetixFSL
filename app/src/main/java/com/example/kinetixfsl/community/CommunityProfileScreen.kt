package com.example.kinetixfsl.community

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.model.Community
import com.example.kinetixfsl.community.model.Post
import com.google.firebase.auth.FirebaseAuth
import com.example.kinetixfsl.community.model.UserComment
import kotlinx.coroutines.launch

/**
 * The signed-in user's community profile: their details, a few stats, and two
 * swipeable tabs — every post they've made, and every comment they've left.
 *
 * Only the post/comment lists and the 3-dot actions are wired up; Edit,
 * followers and the community stat are display-only for now.
 */
@Composable
fun CommunityProfileScreen(
    onPostClick: (Post) -> Unit,
    onEditPost: (Post) -> Unit,
    onCommentClick: (UserComment) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whose profile to show. Null is the signed-in user; anything else turns
     * this into a visitor view — Message and Follow replace Edit, and the post
     * menu drops the actions only an author should have.
     */
    userId: String? = null,
    /** Tapping Message. Not implemented yet, so this is a no-op by default. */
    onMessageClick: () -> Unit = {},
    /** Tapping a user row (e.g. in the followers list) to visit their profile. */
    onUserClick: ((String) -> Unit)? = null,
    /** Tapping My Communities. Also a placeholder until communities exist. */
    onCommunitiesClick: () -> Unit = {},
    /** Tapping a community in the My Communities sheet opens it. */
    onOpenCommunity: (String) -> Unit = {},
    viewModel: CommunityProfileViewModel =
        remember(userId) { CommunityProfileViewModel(userId = userId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userVotes by viewModel.userVotes.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Own-profile editing: an Edit sheet to swap the profile picture and banner,
    // each picked from the device then cropped before upload — the same flow the
    // community's Edit uses.
    var showEditProfile by remember { mutableStateOf(false) }
    var cropRequest by remember { mutableStateOf<ProfileCropRequest?>(null) }
    val bannerPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? -> uri?.let { cropRequest = ProfileCropRequest(it, forBanner = true) } }
    val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri: android.net.Uri? -> uri?.let { cropRequest = ProfileCropRequest(it, forBanner = false) } }

    // Whose profile this is — the visitor arg, or the signed-in user for the
    // own-profile tab. Backs the share link.
    val shareUid = userId ?: FirebaseAuth.getInstance().currentUser?.uid

    val pagerState = rememberPagerState(pageCount = { 2 })

    // The post whose 3-dot sheet is open, if any.
    var actionsPost: Post? by remember { mutableStateOf(null) }
    var pendingDelete: Post? by remember { mutableStateOf(null) }

    // Same three states for a comment: menu open, being edited, being deleted.
    var actionsComment: UserComment? by remember { mutableStateOf(null) }
    var editingComment: UserComment? by remember { mutableStateOf(null) }
    var pendingCommentDelete: UserComment? by remember { mutableStateOf(null) }

    // The header links.
    var isContributionsOpen by remember { mutableStateOf(false) }
    var isFollowersOpen by remember { mutableStateOf(false) }
    var isCommunitiesOpen by remember { mutableStateOf(false) }

    // Swipe-up collapse: the identity card (avatar, name, followers) slides up
    // and out while the stat pills and Posts/Comments tabs stay pinned, so the
    // posts and comments take over the screen. The card's real height is measured
    // once, then the nested-scroll connection drives its offset in step with the
    // active tab's list — see the reference design.
    val density = LocalDensity.current
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    var cardOffsetPx by remember { mutableFloatStateOf(0f) }
    val headerAlpha = if (cardHeightPx > 0f) {
        (1f + cardOffsetPx / cardHeightPx).coerceIn(0f, 1f)
    } else {
        1f
    }
    val collapseConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Swiping up (negative) collapses the card before the list scrolls.
                val delta = available.y
                if (delta < 0f && cardHeightPx > 0f) {
                    val previous = cardOffsetPx
                    cardOffsetPx = (cardOffsetPx + delta).coerceIn(-cardHeightPx, 0f)
                    return Offset(0f, cardOffsetPx - previous)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Swiping down (positive) re-expands only once the list is back
                // at the top and hands the leftover scroll up to us.
                val delta = available.y
                if (delta > 0f && cardHeightPx > 0f) {
                    val previous = cardOffsetPx
                    cardOffsetPx = (cardOffsetPx + delta).coerceIn(-cardHeightPx, 0f)
                    return Offset(0f, cardOffsetPx - previous)
                }
                return Offset.Zero
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(collapseConnection),
        ) {
            // Reserves the shrinking space the overlaid identity card occupies.
            Spacer(
                modifier = Modifier.height(
                    with(density) { (cardHeightPx + cardOffsetPx).coerceAtLeast(0f).toDp() },
                ),
            )

            StatPillsRow(
                contributions = state.contributions,
                communityCount = state.myCommunityCount,
                accountAge = state.accountAge,
                activeTime = state.activeTime,
                onContributionsClick = { isContributionsOpen = true },
                onCommunitiesClick = { isCommunitiesOpen = true },
            )

            ProfileTabs(
                selectedIndex = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

            // Swiping left/right moves between Posts and Comments.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> PostsTab(
                        posts = state.posts,
                        userVotes = userVotes,
                        isLoading = state.isLoadingPosts,
                        onPostClick = onPostClick,
                        onMenuClick = { actionsPost = it },
                        onUpvote = { viewModel.vote(it.id, "up") },
                        onDownvote = { viewModel.vote(it.id, "down") },
                        onShare = { viewModel.share(context, it) },
                        onOpenCommunityLink = onOpenCommunity,
                        onOpenProfileLink = onUserClick,
                    )
                    else -> CommentsTab(
                        comments = state.comments,
                        isLoading = state.isLoadingComments,
                        errorMessage = state.commentsError,
                        onCommentClick = onCommentClick,
                        // No menu on someone else's comments — there's nothing
                        // there a visitor is allowed to do.
                        onMenuClick = if (state.isOwnProfile) {
                            { actionsComment = it }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        // The collapsible identity card, drawn over the reserved space and
        // sliding up out of view as the lists are swiped up.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, cardOffsetPx.roundToInt()) }
                .onSizeChanged { cardHeightPx = it.height.toFloat() }
                .graphicsLayer { alpha = headerAlpha },
        ) {
            ProfileCard(
                displayName = state.displayName,
                avatarUrl = state.avatarUrl,
                bannerUrl = state.bannerUrl,
                followerCount = state.followerCount,
                isOwnProfile = state.isOwnProfile,
                isFollowing = state.isFollowing,
                onFollowersClick = { isFollowersOpen = true },
                onFollowClick = { viewModel.toggleFollow() },
                onMessageClick = onMessageClick,
                onEditClick = { showEditProfile = true },
            )
        }

        // Share this profile — a link that opens the same profile in-app, or a
        // public web page for people without the app. Fades away with the card so
        // it never floats over the pinned tabs once collapsed.
        if (shareUid != null && headerAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 16.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .graphicsLayer { alpha = headerAlpha }
                    .clickable { shareProfile(context, shareUid, state.displayName) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CommunityIcons.Share,
                    contentDescription = "Share profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        val target = actionsPost
        if (target != null) {
            if (state.isOwnProfile) {
                PostActionsSheet(
                    onCopyText = {
                        copyPostText(context, target)
                        actionsPost = null
                    },
                    onDelete = {
                        pendingDelete = target
                        actionsPost = null
                    },
                    onEdit = {
                        actionsPost = null
                        onEditPost(target)
                    },
                    onDismiss = { actionsPost = null },
                )
            } else {
                VisitorPostActionsSheet(
                    onCopyText = {
                        copyPostText(context, target)
                        actionsPost = null
                    },
                    onFollow = {
                        viewModel.followAuthor(target)
                        Toast.makeText(
                            context,
                            "Following ${target.authorName}",
                            Toast.LENGTH_SHORT,
                        ).show()
                        actionsPost = null
                    },
                    onDismiss = { actionsPost = null },
                )
            }
        }

        val deleting = pendingDelete
        if (deleting != null) {
            ConfirmDeleteDialog(
                title = deleting.title,
                onConfirm = {
                    viewModel.deletePost(deleting)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }

        val commentTarget = actionsComment
        if (commentTarget != null) {
            CommentActionsSheet(
                onEdit = {
                    editingComment = commentTarget
                    actionsComment = null
                },
                onDelete = {
                    pendingCommentDelete = commentTarget
                    actionsComment = null
                },
                onDismiss = { actionsComment = null },
            )
        }

        val commentToEdit = editingComment
        if (commentToEdit != null) {
            EditCommentDialog(
                initialText = commentToEdit.comment.body,
                initialImageUrl = commentToEdit.comment.imageUrl,
                onConfirm = { text, newImageUri, removeImage ->
                    viewModel.editComment(commentToEdit, text, context, newImageUri, removeImage)
                    editingComment = null
                },
                onDismiss = { editingComment = null },
            )
        }

        if (isContributionsOpen) {
            ContributionsSheet(
                postCount = state.posts.size,
                commentCount = state.comments.size,
                onDismiss = { isContributionsOpen = false },
            )
        }

        if (isCommunitiesOpen) {
            MyCommunitiesSheet(
                communities = state.myCommunities,
                joinedIds = state.viewerJoinedCommunityIds,
                // Tags any community *this profile's* user created, so a
                // visitor knows they can visit and join it too.
                profileUid = state.profileUid,
                onToggleJoin = { viewModel.toggleJoinCommunity(it) },
                onOpenCommunity = { community ->
                    isCommunitiesOpen = false
                    onOpenCommunity(community.id)
                },
                onDismiss = { isCommunitiesOpen = false },
            )
        }

        if (isFollowersOpen) {
            FollowersScreen(
                onClose = { isFollowersOpen = false },
                uid = userId ?: FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                onUserClick = onUserClick,
            )
        }

        val commentToDelete = pendingCommentDelete
        if (commentToDelete != null) {
            ConfirmDeleteDialog(
                title = commentToDelete.comment.body,
                heading = "Delete comment?",
                subject = "comment",
                onConfirm = {
                    viewModel.deleteComment(commentToDelete)
                    pendingCommentDelete = null
                },
                onDismiss = { pendingCommentDelete = null },
            )
        }

        // Own-profile Edit: pick + change the profile picture and cover banner.
        if (showEditProfile && state.isOwnProfile) {
            EditProfileSheet(
                displayName = state.displayName,
                avatarUrl = state.avatarUrl,
                bannerUrl = state.bannerUrl,
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
                onSaveName = viewModel::updateDisplayName,
                onDismiss = { showEditProfile = false },
            )
        }

        // The cropper sits over everything while it's open.
        cropRequest?.let { req ->
            com.example.kinetixfsl.community.home.ImageCropScreen(
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

    // Surface load/delete failures rather than failing silently.
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.dismissError()
    }
}

// ---- Header ----

/**
 * The identity card at the top of a profile — avatar, name, Edit/Follow and the
 * follower count. This is the piece that collapses away on swipe-up; the stat
 * pills and tabs below it stay pinned.
 */
@Composable
private fun ProfileCard(
    displayName: String,
    avatarUrl: String?,
    bannerUrl: String?,
    followerCount: Long,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    onFollowersClick: () -> Unit,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bannerHeight = 130.dp
    val avatarSize = 72.dp
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ---- Cover banner: the user's image, or a plain colored strip ----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (!bannerUrl.isNullOrBlank()) {
                        coil.compose.AsyncImage(
                            model = bannerUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Content sits below the banner, leaving room for the avatar that
                // straddles the banner's bottom edge.
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 44.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(14.dp))

                        if (isOwnProfile) {
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onEditClick)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        } else {
                            // Message is a placeholder button for now — deliberately
                            // clickable so the layout is final, with nothing behind it.
                            Icon(
                                imageVector = CommunityIcons.Message,
                                contentDescription = "Message",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onMessageClick),
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isFollowing) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable(onClick = onFollowClick)
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$followerCount followers  ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onFollowersClick)
                            .padding(vertical = 2.dp),
                    )
                }
            }

            // Avatar straddling the banner's bottom edge, drawn last so it sits
            // above both the banner and the content below it.
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .offset(y = bannerHeight - avatarSize / 2)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(3.dp),
            ) {
                Avatar(avatarUrl = avatarUrl, name = displayName, size = avatarSize)
            }
        }
    }
}

/**
 * The four stat pills. Pinned below the collapsing card, so they — together with
 * the tabs — stay put as the posts and comments take over the screen.
 */
@Composable
private fun StatPillsRow(
    contributions: Int,
    communityCount: Int,
    accountAge: String,
    activeTime: String,
    onContributionsClick: () -> Unit,
    onCommunitiesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Account Age and Active time are read-only by design and stay
        // untappable; My Communities and Contributions open a sheet.
        StatPill(
            "$communityCount",
            "My Communities",
            Modifier
                .weight(1f)
                .clickable(onClick = onCommunitiesClick),
        )
        StatPill(
            "$contributions",
            "Contributions ›",
            Modifier
                .weight(1f)
                .clickable(onClick = onContributionsClick),
        )
        StatPill(accountAge, "Account Age", Modifier.weight(1f))
        StatPill(activeTime, "Active time", Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    // Filled brand-indigo pill (onPrimary text) to match the reference design;
    // primary/onPrimary keep it legible in both light and dark themes.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // "Active now" needs a second line in a quarter-width pill.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Tabs ----

@Composable
private fun ProfileTabs(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Filled indigo tab bar from the reference design; onPrimary text and
    // underline keep it readable in both themes.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        TabLabel("Posts", selectedIndex == 0, Modifier.weight(1f)) { onSelect(0) }
        TabLabel("Comments", selectedIndex == 1, Modifier.weight(1f)) { onSelect(1) }
    }
}

@Composable
private fun TabLabel(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimary.copy(
                alpha = if (selected) 1f else 0.7f,
            ),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    }
                ),
        )
    }
}

// ---- Posts tab ----

@Composable
private fun PostsTab(
    posts: List<Post>,
    userVotes: Map<String, String>,
    isLoading: Boolean,
    onPostClick: (Post) -> Unit,
    onMenuClick: (Post) -> Unit,
    onUpvote: (Post) -> Unit,
    onDownvote: (Post) -> Unit,
    onShare: (Post) -> Unit,
    /** In-app openers for the app's own share links pasted into a post. */
    onOpenCommunityLink: ((String) -> Unit)? = null,
    onOpenProfileLink: ((String) -> Unit)? = null,
) {
    when {
        isLoading -> CenteredSpinner()
        posts.isEmpty() -> EmptyMessage("You haven't posted anything yet.")
        else -> {
            val listState = rememberLazyListState()
            // Same visibility rule as the feed, so videos here also only load
            // while they're actually on screen.
            val visibleKeys by remember {
                derivedStateOf {
                    listState.layoutInfo.visibleItemsInfo
                        .mapNotNull { it.key as? String }
                        .toSet()
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(posts, key = { it.id }) { post ->
                    // The feed's own card, so a post looks identical here.
                    PostCard(
                        post = post,
                        userVote = userVotes[post.id],
                        isVideoVisible = post.id in visibleKeys,
                        onUpvote = { onUpvote(post) },
                        onDownvote = { onDownvote(post) },
                        onComment = { onPostClick(post) },
                        onShare = { onShare(post) },
                        onMediaClick = { onPostClick(post) },
                        onClick = { onPostClick(post) },
                        onMenuClick = { onMenuClick(post) },
                        onOpenCommunityLink = onOpenCommunityLink,
                        onOpenProfileLink = onOpenProfileLink,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

// ---- Comments tab ----

@Composable
private fun CommentsTab(
    comments: List<UserComment>,
    isLoading: Boolean,
    errorMessage: String?,
    onCommentClick: (UserComment) -> Unit,
    /** Null on a visitor view — a visitor can't act on these comments. */
    onMenuClick: ((UserComment) -> Unit)?,
) {
    when {
        isLoading -> CenteredSpinner()
        errorMessage != null -> CommentsErrorMessage(errorMessage)
        comments.isEmpty() -> EmptyMessage("You haven't commented on anything yet.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(comments, key = { it.comment.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCommentClick(item) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.postTitle.ifBlank { "Post" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.comment.authorName.ifBlank { "You" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "• ${item.comment.createdAt.relativeToNow()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.comment.body.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        // Passive "see more" hint — tapping this row already opens
                        // the full post-and-comment view, so this isn't an
                        // interactive toggle the way the thread view's is.
                        var isOverflowing by remember(item.comment.id) { mutableStateOf(false) }
                        Text(
                            text = item.comment.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { result -> isOverflowing = result.hasVisualOverflow },
                        )
                        if (isOverflowing) {
                            Text(
                                text = "see more",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    // A comment made of just a photo (or a photo alongside text)
                    // otherwise leaves no hint here that it carries an image —
                    // the thumbnail itself only shows in the full post view.
                    if (!item.comment.imageUrl.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = CommunityIcons.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Photo",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                  }
                  if (onMenuClick != null) {
                      Icon(
                          imageVector = CommunityIcons.MoreVertical,
                          contentDescription = "Comment options",
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier
                              .size(22.dp)
                              .clickable { onMenuClick(item) },
                      )
                  }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

// ---- Shared bits ----

/**
 * Firestore's setup errors carry the console URL that fixes them, but it's a
 * wall of text nobody can retype off a phone screen. Pull the link out and give
 * it a tap target — copy it, or open it straight in a browser.
 */
@Composable
private fun CommentsErrorMessage(errorMessage: String) {
    val context = LocalContext.current
    val link = remember(errorMessage) {
        Regex("https://\\S+").find(errorMessage)?.value?.trimEnd('.', ',')
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Couldn't load your comments.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (link != null) {
                "Firestore needs a one-off index for this query. " +
                    "Open the link below and press Create — it takes a minute " +
                    "or two to build, then pull back here."
            } else {
                errorMessage
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (link != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ErrorAction("Open in browser") {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(link),
                            )
                        )
                    }
                }
                ErrorAction("Copy link") {
                    val clipboard = context
                        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Index link", link))
                    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
private fun ErrorAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Copies the post's title, body and any links together, as the mockup's
 * "Copy text". Links go last, one per line, so pasting keeps them clickable.
 */
internal fun copyPostText(context: Context, post: Post) {
    val text = buildList {
        add(post.title)
        add(post.body)
        addAll(post.allLinks)
    }.filter { it.isNotBlank() }.joinToString("\n\n")

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Post", text))

    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
}

/**
 * Fires the system share sheet with a link to this profile. Opening the link
 * drops the recipient on the same profile — see the `/u/` route in ShareLinks
 * and the deep-link handling in the NavHost.
 */
private fun shareProfile(context: Context, userId: String, displayName: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, displayName)
        putExtra(android.content.Intent.EXTRA_TEXT, ShareLinks.profileUrl(userId))
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share profile"))
}

/**
 * The breakdown behind the Contributions stat: how much of it is posts and how
 * much is comments.
 */
@Composable
private fun ContributionsSheet(
    postCount: Int,
    commentCount: Int,
    onDismiss: () -> Unit,
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
                // Swallow taps so they don't reach the dismiss backdrop.
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Contributions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Total post and comments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
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

            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                CountBlock("$postCount", "Post", Modifier.weight(1f))
                Box(
                    Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                CountBlock("$commentCount", "Comments", Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CountBlock(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The communities this profile's user has joined — same bottom-sheet shell as
 * [ContributionsSheet]. Each row is name + description with a Join/Joined pill
 * that reflects the *viewer's* membership, so you can join straight from here.
 */
@Composable
private fun MyCommunitiesSheet(
    communities: List<Community>,
    joinedIds: Set<String>,
    /** Whose sheet this is — a community whose creatorId matches gets tagged
     *  "Your community", so a visitor knows they can visit and join it. */
    profileUid: String?,
    onToggleJoin: (Community) -> Unit,
    onOpenCommunity: (Community) -> Unit,
    onDismiss: () -> Unit,
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
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Communities",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Communities joined",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
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

            if (communities.isEmpty()) {
                Text(
                    text = "No communities joined yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    communities.forEach { community ->
                        MyCommunityRow(
                            community = community,
                            isJoined = community.id in joinedIds,
                            isOwnedByProfile = profileUid != null && community.creatorId == profileUid,
                            onToggleJoin = { onToggleJoin(community) },
                            onClick = { onOpenCommunity(community) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MyCommunityRow(
    community: Community,
    isJoined: Boolean,
    /** True when this profile's own user is the one who created it. */
    isOwnedByProfile: Boolean,
    onToggleJoin: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarUrl = community.avatarUrl,
            name = community.name,
            size = 44.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = community.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${community.memberCount} " +
                    if (community.memberCount == 1L) "member" else "members",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (community.description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = community.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        val shape = RoundedCornerShape(50)
        if (isOwnedByProfile) {
            // No Join/Joined on your own community — a tag only, matching
            // Discover's "Your community" pill on the same kind of card.
            Box(
                modifier = Modifier
                    .clip(shape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Your community",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
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
                    .clickable(onClick = onToggleJoin)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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
    }
}

// ---- Edit profile (own profile only) ----

/** A picked photo waiting to be cropped, and which slot it's for. */
private data class ProfileCropRequest(val uri: android.net.Uri, val forBanner: Boolean)

/**
 * The profile's Edit sheet: change the wide cover banner and the round profile
 * picture, both imported from the device. Each change uploads and saves right
 * away, so the card updates live behind the sheet — mirrors the community editor.
 */
@Composable
private fun EditProfileSheet(
    displayName: String,
    avatarUrl: String?,
    bannerUrl: String?,
    uploading: CommunityProfileViewModel.Uploading,
    onChangeBanner: () -> Unit,
    onChangeAvatar: () -> Unit,
    onSaveName: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val idle = uploading == CommunityProfileViewModel.Uploading.NONE
    var nameInput by remember(displayName) { mutableStateOf(displayName) }
    val nameChanged = nameInput.trim().isNotEmpty() && nameInput.trim() != displayName.trim()
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
                // Swallow taps so they don't reach the dismiss backdrop.
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Name, banner and profile picture",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
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

            // ---- Name ----
            Text(
                text = "Your name",
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
                    .clickable(enabled = idle, onClick = onChangeBanner),
                contentAlignment = Alignment.Center,
            ) {
                if (!bannerUrl.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = bannerUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (uploading == CommunityProfileViewModel.Uploading.BANNER) {
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
                    Avatar(avatarUrl = avatarUrl, name = displayName, size = 64.dp)
                    if (uploading == CommunityProfileViewModel.Uploading.AVATAR) {
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
                        .clickable(enabled = idle, onClick = onChangeAvatar)
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
