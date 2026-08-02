package com.example.kinetixfsl.community

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Comment

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
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CommentRow(
            comment = thread.comment,
            onReply = { onReply(thread.comment) },
            onImageClick = onImageClick,
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
                        isReply = true,
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
        )

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
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
                Text(
                    comment.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
            Text(
                text = "Reply",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    // Pulled back by the same amount as its tap padding so the
                    // label still lines up with the body text above it.
                    .offset(x = (-8).dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onReply)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Replies line up under the parent's text, not under its avatar. */
private val REPLY_INDENT = 56.dp
