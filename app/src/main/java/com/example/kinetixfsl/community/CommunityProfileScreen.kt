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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: CommunityProfileViewModel =
        remember(userId) { CommunityProfileViewModel(userId = userId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userVotes by viewModel.userVotes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { 2 })

    // The post whose 3-dot sheet is open, if any.
    var actionsPost: Post? by remember { mutableStateOf(null) }
    var pendingDelete: Post? by remember { mutableStateOf(null) }

    // Same three states for a comment: menu open, being edited, being deleted.
    var actionsComment: UserComment? by remember { mutableStateOf(null) }
    var editingComment: UserComment? by remember { mutableStateOf(null) }
    var pendingCommentDelete: UserComment? by remember { mutableStateOf(null) }

    // The two header links.
    var isContributionsOpen by remember { mutableStateOf(false) }
    var isFollowersOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ProfileHeader(
                displayName = state.displayName,
                avatarUrl = state.avatarUrl,
                contributions = state.contributions,
                followerCount = state.followerCount,
                accountAge = state.accountAge,
                activeTime = state.activeTime,
                isOwnProfile = state.isOwnProfile,
                isFollowing = state.isFollowing,
                onFollowersClick = { isFollowersOpen = true },
                onContributionsClick = { isContributionsOpen = true },
                onFollowClick = { viewModel.toggleFollow() },
                onMessageClick = onMessageClick,
                onCommunitiesClick = onCommunitiesClick,
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
                onConfirm = { text ->
                    viewModel.editComment(commentToEdit, text)
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
    }

    // Surface load/delete failures rather than failing silently.
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.dismissError()
    }
}

// ---- Header ----

@Composable
private fun ProfileHeader(
    displayName: String,
    avatarUrl: String?,
    contributions: Int,
    followerCount: Long,
    accountAge: String,
    activeTime: String,
    isOwnProfile: Boolean,
    isFollowing: Boolean,
    onFollowersClick: () -> Unit,
    onContributionsClick: () -> Unit,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onCommunitiesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Avatar(avatarUrl = avatarUrl, name = displayName, size = 72.dp)
            Spacer(Modifier.height(14.dp))
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

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Clickable but inert until communities exist. Account Age and
            // Active time are read-only by design and stay untappable.
            StatPill(
                "0",
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
}

@Composable
private fun StatPill(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // "Active now" needs a second line in a quarter-width pill.
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TabLabel("Posts", selectedIndex == 0, Modifier.weight(1f)) { onSelect(0) }
            TabLabel("Comments", selectedIndex == 1, Modifier.weight(1f)) { onSelect(1) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            color = if (selected) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.onBackground
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.comment.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
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

/** Copies the post's title and body together, as the mockup's "Copy text". */
private fun copyPostText(context: Context, post: Post) {
    val text = listOf(post.title, post.body)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Post", text))

    Toast.makeText(context, "Text copied", Toast.LENGTH_SHORT).show()
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
