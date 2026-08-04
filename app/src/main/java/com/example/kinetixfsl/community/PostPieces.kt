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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixGreen

/**
 * Pieces shared by the feed card and the post detail screen so both render a
 * post the same way. Everything reads from MaterialTheme.colorScheme, so these
 * follow the system light/dark setting.
 */

/**
 * Avatar + display name + "3h · 1.2k views" metadata line.
 *
 * When [communityName] is set (a community post in the home feed), the row
 * instead shows the community on top and a "Posted by {author}" byline
 * underneath — the same two-line pattern Reddit uses so a community post still
 * credits the person who made it.
 */
@Composable
internal fun PostAuthorRow(
    post: Post,
    modifier: Modifier = Modifier,
    /** Optional control pinned to the right — the profile's 3-dot menu. */
    trailing: @Composable (() -> Unit)? = null,
    /**
     * Opens the author's profile. Only the avatar and the name are the tap
     * target — the rest of the card still opens the post.
     */
    onAuthorClick: (() -> Unit)? = null,
    /**
     * When set, the row shows this community (letter avatar + name) instead of
     * the post's author. The home feed uses this so community posts surface the
     * community, matching the design. Tapping opens the community.
     */
    communityName: String? = null,
    communityAvatarUrl: String? = null,
    onCommunityClick: (() -> Unit)? = null,
) {
    val timeLabel = post.createdAt.relativeToNow()
    val viewsLabel = if (post.viewCount > 0) "${post.viewCount.compact()} views" else null

    if (communityName != null) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            val communityClickable = if (onCommunityClick != null) {
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onCommunityClick)
            } else {
                Modifier
            }
            Box(modifier = communityClickable) {
                Avatar(
                    avatarUrl = communityAvatarUrl,
                    name = communityName,
                    size = 32.dp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        communityName.ifBlank { "Community" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = communityClickable,
                    )
                    if (timeLabel.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val byline = "Posted by " + post.authorName.ifBlank { "Unknown" } +
                    (viewsLabel?.let { " · $it" } ?: "")
                Text(
                    byline,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (onAuthorClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onAuthorClick)
                    } else {
                        Modifier
                    },
                )
            }
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                trailing()
            }
        }
        return
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val headerModifier = if (onAuthorClick != null) {
            Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onAuthorClick)
        } else {
            Modifier
        }

        Row(
            modifier = headerModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                avatarUrl = post.authorAvatarUrl,
                name = post.authorName,
                size = 32.dp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                post.authorName.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.width(8.dp))
        val meta = listOfNotNull(timeLabel.takeIf { it.isNotBlank() }, viewsLabel)
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * Round avatar that falls back to the first letter of [name] when the user
 * has no photo. Used by post cards and comment rows alike.
 */
@Composable
internal fun Avatar(
    avatarUrl: String?,
    name: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Body text that clamps to [maxLines] and shows a "see more" hint when there's
 * more to read — the feed behaviour. Tapping anywhere on the card opens the
 * detail screen, which renders the same body unclamped, so the hint is a label
 * rather than an interactive toggle.
 */
@Composable
internal fun TruncatedBodyText(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
) {
    var isOverflowing by remember(text) { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = maxLines,
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
}

/** Upvote / downvote / comment pill on the left, share pill on the right. */
@Composable
internal fun PostInteractionRow(
    post: Post,
    userVote: String?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InteractionButton(
                CommunityIcons.ArrowUp,
                post.upvoteCount.compact(),
                if (userVote == "up") KinetixGreen else MaterialTheme.colorScheme.onBackground,
                onUpvote,
            )
            VerticalHairline()
            InteractionButton(
                CommunityIcons.ArrowDown,
                post.downvoteCount.compact(),
                if (userVote == "down") KinetixError else MaterialTheme.colorScheme.onBackground,
                onDownvote,
            )
            VerticalHairline()
            InteractionButton(
                CommunityIcons.Comment,
                post.commentCount.compact(),
                MaterialTheme.colorScheme.onBackground,
                onComment,
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            InteractionButton(
                CommunityIcons.Share,
                post.shareCount.compact(),
                MaterialTheme.colorScheme.onBackground,
                onShare,
            )
        }
    }
}

@Composable
private fun InteractionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        Modifier
            .width(1.dp)
            .height(18.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
