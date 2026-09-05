package com.example.kinetixfsl.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixGreen

/**
 * A top-level comment and its replies.
 *
 * Replies stay hidden behind a "View N replies" tap, the way Facebook does it,
 * so a long back-and-forth doesn't push the rest of the thread offscreen.
 */
@Composable
internal fun CommentThreadItem(
    thread: CommentThread,
    isExpanded: Boolean,
    onToggleReplies: () -> Unit,
    onReply: (Comment) -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Opens the commenter's profile — the signed-in user's own included. */
    onAuthorClick: (String) -> Unit = {},
    /** This user's own vote on each comment id, "up"/"down"/absent. */
    commentUserVotes: Map<String, String> = emptyMap(),
    /** (commentId, "up" | "down") — tapping the same direction twice retracts it. */
    onVote: (String, String) -> Unit = { _, _ -> },
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CommentRow(
            comment = thread.comment,
            onReply = { onReply(thread.comment) },
            onImageClick = onImageClick,
            onAuthorClick = onAuthorClick,
            userVote = commentUserVotes[thread.comment.id],
            onUpvote = { onVote(thread.comment.id, "up") },
            onDownvote = { onVote(thread.comment.id, "down") },
        )

        if (thread.replies.isNotEmpty()) {
            RepliesToggle(
                replyCount = thread.replies.size,
                isExpanded = isExpanded,
                onClick = onToggleReplies,
            )

            if (isExpanded) {
                thread.replies.forEach { reply ->
                    CommentRow(
                        comment = reply,
                        onReply = { onReply(reply) },
                        onImageClick = onImageClick,
                        onAuthorClick = onAuthorClick,
                        isReply = true,
                        userVote = commentUserVotes[reply.id],
                        onUpvote = { onVote(reply.id, "up") },
                        onDownvote = { onVote(reply.id, "down") },
                    )
                }
            }
        }
    }
}

/** "View 3 replies" / "Hide replies", indented to line up under the comment. */
@Composable
private fun RepliesToggle(
    replyCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    val label = if (isExpanded) {
        "Hide replies"
    } else {
        "View $replyCount ${if (replyCount == 1) "reply" else "replies"}"
    }

    Row(
        modifier = Modifier
            .padding(start = REPLY_INDENT, bottom = 8.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isExpanded) {
                CommunityIcons.ChevronUp
            } else {
                CommunityIcons.ChevronDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * One comment: avatar, author, relative time, body, the single optional image
 * the author attached, and a Reply action. Tapping the image opens it full
 * screen.
 *
 * [isReply] indents the row and shrinks the avatar so replies read as nested
 * without needing a divider or a rule line.
 *
 * All colors come from MaterialTheme.colorScheme, so the row reads correctly in
 * both light and dark mode.
 */
@Composable
internal fun CommentRow(
    comment: Comment,
    onReply: () -> Unit,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isReply: Boolean = false,
    /** Opens the commenter's profile — the signed-in user's own included. */
    onAuthorClick: (String) -> Unit = {},
    /** This comment's own vote from the signed-in user, "up"/"down"/null. */
    userVote: String? = null,
    onUpvote: () -> Unit = {},
    onDownvote: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isReply) REPLY_INDENT else 16.dp,
                end = 16.dp,
                top = if (isReply) 8.dp else 12.dp,
                bottom = if (isReply) 8.dp else 12.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            avatarUrl = comment.authorAvatarUrl,
            name = comment.authorName,
            size = if (isReply) 24.dp else 30.dp,
            modifier = Modifier.clickable(enabled = comment.authorId.isNotBlank()) {
                onAuthorClick(comment.authorId)
            },
        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = comment.authorId.isNotBlank()) {
                            onAuthorClick(comment.authorId)
                        },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    comment.createdAt.relativeToNow(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (comment.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ExpandableCommentText(comment.body)
            }

            val imageUrl = comment.imageUrl
            if (!imageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                AsyncImage(
                    model = optimizeImageUrl(imageUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { onImageClick(imageUrl) },
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Reply",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        // Pulled back by the same amount as its tap padding so
                        // the label still lines up with the body text above it.
                        .offset(x = (-8).dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onReply)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                // Pulled forward the same way, so the pill's own edge padding
                // still lands flush with the comment's right edge.
                CommentVoteRow(
                    upvoteCount = comment.upvoteCount,
                    downvoteCount = comment.downvoteCount,
                    userVote = userVote,
                    onUpvote = onUpvote,
                    onDownvote = onDownvote,
                    modifier = Modifier.offset(x = 8.dp),
                )
            }
        }
    }
}

/**
 * The same bordered-pill style as the post's own upvote/downvote/comment row
 * (see PostInteractionRow) — just the two vote buttons, no comment count
 * (a reply count already shows via "View N replies"). Each shows its own
 * count rather than a single net number, matching the post's pill exactly.
 *
 * Free will, same as a post's own vote: tapping the same arrow again
 * retracts it, tapping the other one flips it (see
 * CommunityRepository.voteComment).
 */
@Composable
private fun CommentVoteRow(
    upvoteCount: Long,
    downvoteCount: Long,
    userVote: String?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommentVoteButton(
            icon = CommunityIcons.ArrowUp,
            label = upvoteCount.compact(),
            tint = if (userVote == "up") KinetixGreen else MaterialTheme.colorScheme.onBackground,
            onClick = onUpvote,
        )
        CommentVoteHairline()
        CommentVoteButton(
            icon = CommunityIcons.ArrowDown,
            label = downvoteCount.compact(),
            tint = if (userVote == "down") KinetixError else MaterialTheme.colorScheme.onBackground,
            onClick = onDownvote,
        )
    }
}

@Composable
private fun CommentVoteButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CommentVoteHairline() {
    Box(
        Modifier
            .width(1.dp)
            .height(14.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** Replies line up under the parent's text, not under its avatar. */
private val REPLY_INDENT = 56.dp

/**
 * A comment's body, clamped to 3 lines with a "See more" toggle when it
 * overflows — keeps a long paragraph from pushing the rest of the thread
 * offscreen. Unlike a post card's passive "see more" hint, this is the deepest
 * view a comment ever gets, so tapping actually expands it in place (and
 * "See less" collapses it back), rather than pointing anywhere else.
 */
@Composable
private fun ExpandableCommentText(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    var isOverflowing by remember(text) { mutableStateOf(false) }

    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded && result.hasVisualOverflow) isOverflowing = true
            },
        )
        if (isOverflowing) {
            Text(
                text = if (expanded) "See less" else "See more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable { expanded = !expanded },
            )
        }
    }
}

/**
 * "Most relevant ▾" — tapping opens the three-way choice Facebook offers
 * under a post's comment count: Most relevant (highest score first), Newest
 * (reverse-chronological), and All comments (every one, oldest first, no
 * ranking). Shared by [PostDetailScreen] and [ImmersivePostViewer].
 */
@Composable
internal fun CommentSortDropdown(
    selected: CommentSortMode,
    onSelect: (CommentSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = CommunityIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CommentSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                    text = {
                        Text(
                            text = mode.label,
                            color = if (mode == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (mode == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }
    }
}
