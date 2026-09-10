package com.example.kinetixfsl.community

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Post

/**
 * The full post: title, complete body, media, interactions, and every comment —
 * the screen you land on after tapping a post in the feed.
 *
 * The composer isn't inline here. Tapping "Join the conversation" opens
 * [AddCommentScreen] on top, matching the flow in the reference screens.
 *
 * [post] should be the live copy from the feed so vote and comment counts stay
 * in sync while this screen is open.
 */
@Composable
fun PostDetailScreen(
    post: Post,
    viewModel: CommentViewModel,
    userVote: String?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    /** Opens the author's profile from their avatar or name in the top bar. */
    onAuthorClick: (String) -> Unit = {},
    /** Opens the post's community from its name in the top bar. No-op if unset. */
    onCommunityClick: (String) -> Unit = {},
    /** The community's profile picture, looked up by the caller. Null shows the
     *  letter-avatar fallback, same as everywhere else a community avatar shows. */
    communityAvatarUrl: String? = null,
    /**
     * The top-bar 3-dot menu. On your own post it offers Edit and Delete; on
     * anyone else's it offers Hide. Each is only shown when its handler is set,
     * so a screen that can't support one (e.g. a shared link) just omits it.
     * [onDelete] and [onHide] should also close this screen — the post is gone.
     */
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    /** Whether the signed-in user has already hidden this post — flips the
     *  3-dot menu's "Hide" to "Unhide". */
    isHidden: Boolean = false,
    /**
     * Open the app's own share links (a post, community, or profile pasted into
     * a post's links) in-app rather than the browser. See [PostLinks].
     */
    onOpenPostLink: ((String) -> Unit)? = null,
    onOpenCommunityLink: ((String) -> Unit)? = null,
    onOpenProfileLink: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Full-screen media opened from inside the detail screen (post media or a
    // comment's image). Kept local so back closes the viewer before the screen.
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenVideoUrl by remember { mutableStateOf<String?>(null) }

    // The comment composer, opened from the bottom bar or the comment button.
    var isComposerOpen by remember { mutableStateOf(false) }

    // The top-bar 3-dot menu and its delete confirmation.
    var isMenuOpen by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }
    var isReporting by remember { mutableStateOf(false) }
    val reportScope = rememberCoroutineScope()
    val reportRepository = remember { ReportRepository() }
    val isOwnPost = remember(post.authorId) {
        post.authorId.isNotBlank() &&
            post.authorId == com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    }

    BackHandler(onBack = onClose)

    // Same dark bar treatment as the Home Feed's own top bar — KinetixNavy in
    // light mode, colorScheme.surface in dark — so the status bar's
    // notification area reads as part of one continuous colored bar instead of
    // a plain white strip above it. Always wants light (white) status bar
    // icons regardless of the app's theme, same reasoning as the feed's own.
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(light = true)
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val barBackground = if (darkTheme) MaterialTheme.colorScheme.surface else com.example.kinetixfsl.ui.theme.KinetixNavy
    val barContentColor = if (darkTheme) MaterialTheme.colorScheme.onSurface else com.example.kinetixfsl.ui.theme.KinetixWhite

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ---- Top bar: back, author, share ----
        // The background is painted on this Box, which takes the status bar
        // inset as padding rather than being pushed below it — so the color
        // runs all the way to the physical top of the screen, matching the
        // Home Feed's own top bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBackground)
                .statusBarsPadding(),
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CommunityIcons.ArrowBack,
                contentDescription = "Back",
                tint = barContentColor,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose),
            )
            Spacer(Modifier.width(12.dp))
            if (post.communityId.isNotBlank()) {
                // Community post: the community up top, "Posted by {author}"
                // underneath — Reddit's pattern, so the post still credits
                // whoever made it even though the community leads the header.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Avatar(
                        avatarUrl = communityAvatarUrl,
                        name = post.communityName.ifBlank { "Community" },
                        size = 30.dp,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .border(1.5.dp, barContentColor.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape)
                            .clickable { onCommunityClick(post.communityId) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = post.communityName.ifBlank { "Community" },
                            style = MaterialTheme.typography.titleMedium,
                            color = barContentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onCommunityClick(post.communityId) },
                        )
                        Text(
                            text = "Posted by " + post.authorName.ifBlank { "Unknown" },
                            style = MaterialTheme.typography.labelMedium,
                            color = barContentColor.copy(alpha = 0.75f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable(enabled = post.authorId.isNotBlank()) {
                                    onAuthorClick(post.authorId)
                                },
                        )
                    }
                }
            } else {
                // Avatar and name together are the tap target for the author's
                // profile — the same gesture as in the feed.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = post.authorId.isNotBlank()) {
                            onAuthorClick(post.authorId)
                        }
                        .padding(vertical = 2.dp),
                ) {
                    Avatar(
                        avatarUrl = post.authorAvatarUrl,
                        name = post.authorName,
                        size = 30.dp,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .border(1.5.dp, barContentColor.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = post.authorName.ifBlank { "Unknown" },
                        style = MaterialTheme.typography.titleMedium,
                        color = barContentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Icon(
                imageVector = CommunityIcons.MoreVertical,
                contentDescription = "Post options",
                tint = barContentColor,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { isMenuOpen = true },
            )
        }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ---- Post + comments ----
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "post-body") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )

                    // The full body — no clamping, no "see more". That's the
                    // whole point of opening the post.
                    if (post.body.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        // Plain text — hashtags live in their own row below
                        // (from the composer's box), not inline in the body.
                        Text(
                            text = post.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }

                    val links = post.allLinks
                    if (links.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        PostLinks(
                            links = links,
                            onOpenPost = onOpenPostLink,
                            onOpenCommunity = onOpenCommunityLink,
                            onOpenProfile = onOpenProfileLink,
                        )
                    }

                    val mediaItems = post.mediaItems
                    if (mediaItems.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        PostMediaCarousel(
                            media = mediaItems,
                            isActive = true,
                            onMediaClick = { index ->
                                val item = mediaItems[index]
                                if (item.isVideo) {
                                    fullScreenVideoUrl = item.url
                                } else {
                                    fullScreenImageUrl = item.url
                                }
                            },
                            height = 260.dp,
                        )
                    }

                    // Hashtags from the composer's box (display-only here —
                    // the detail screen has no search bar of its own).
                    if (post.hashtags.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        PostHashtags(hashtags = post.hashtags)
                    }

                    Spacer(Modifier.height(14.dp))
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
                        // Flag icon left of Share — hidden on your own post,
                        // where the 3-dot menu offers Edit/Delete instead.
                        onReport = if (isOwnPost) null else ({ isReporting = true }),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item(key = "comments-header") {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text(
                        text = if (state.totalCount == 0) {
                            "Comments"
                        } else {
                            "Comments (${state.totalCount})"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    if (state.totalCount > 0) {
                        CommentSortDropdown(
                            selected = state.sortMode,
                            onSelect = viewModel::setSortMode,
                            modifier = Modifier.offset(x = (-8).dp),
                        )
                    }
                }
            }

            when {
                state.isLoading -> item(key = "comments-loading") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                state.threads.isEmpty() -> item(key = "comments-empty") {
                    Box(
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
                }

                else -> items(state.threads, key = { it.comment.id }) { thread ->
                    CommentThreadItem(
                        thread = thread,
                        isExpanded = thread.comment.id in state.expandedThreadIds,
                        onToggleReplies = { viewModel.toggleReplies(thread.comment.id) },
                        onReply = { comment ->
                            viewModel.startReply(comment)
                            isComposerOpen = true
                        },
                        onImageClick = { url -> fullScreenImageUrl = url },
                        onAuthorClick = onAuthorClick,
                        commentUserVotes = state.commentUserVotes,
                        onVote = viewModel::voteComment,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        // ---- "Join the conversation" bar ----
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        JoinConversationBar(
            onClick = {
                viewModel.cancelReply()
                isComposerOpen = true
            },
        )
    }

    // ---- Overlays ----
    if (isComposerOpen) {
        AddCommentScreen(
            post = post,
            viewModel = viewModel,
            onClose = { isComposerOpen = false },
        )
    }

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

    // ---- The 3-dot menu: your own post gets Copy/Delete/Edit, anyone else's
    // gets Copy/Report/Share/Hide — matching the feed's menus. ----
    if (isMenuOpen) {
        if (isOwnPost) {
            PostActionsSheet(
                onCopyText = {
                    copyPostText(context, post)
                    isMenuOpen = false
                },
                onDelete = {
                    isMenuOpen = false
                    if (onDelete != null) isConfirmingDelete = true
                },
                onEdit = {
                    isMenuOpen = false
                    onEdit?.invoke()
                },
                onDismiss = { isMenuOpen = false },
            )
        } else {
            OtherPostActionsSheet(
                onCopyText = {
                    copyPostText(context, post)
                    isMenuOpen = false
                },
                onReport = {
                    isMenuOpen = false
                    isReporting = true
                },
                onShare = {
                    onShare()
                    isMenuOpen = false
                },
                onHide = if (onHide != null) {
                    {
                        onHide()
                        isMenuOpen = false
                    }
                } else {
                    null
                },
                isHidden = isHidden,
                onDismiss = { isMenuOpen = false },
            )
        }
    }

    if (isConfirmingDelete) {
        ConfirmDeleteDialog(
            title = post.title,
            onConfirm = {
                isConfirmingDelete = false
                onDelete?.invoke()
            },
            onDismiss = { isConfirmingDelete = false },
        )
    }

    if (isReporting) {
        ReportReasonDialog(
            subject = "post",
            onSubmit = { reason ->
                reportScope.launch { reportRepository.reportPost(post, reason) }
                isReporting = false
                android.widget.Toast.makeText(
                    context,
                    "Thanks — we'll review this post.",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { isReporting = false },
        )
    }
}

/**
 * The bottom bar of the detail screen — a pill that reads "Join the
 * conversation" plus a shortcut straight to attaching an image.
 */
@Composable
private fun JoinConversationBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
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
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "Join the conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
