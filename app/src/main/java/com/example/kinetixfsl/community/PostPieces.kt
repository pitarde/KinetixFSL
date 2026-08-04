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
import androidx.compose.runtime.LaunchedEffect
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
    /**
     * Reports whether the body was clamped (has a "see more"). The feed card
     * uses it to decide whether to show the link: a clamped body hides the link
     * until the post is opened; a short one shows it inline.
     */
    onOverflowChange: (Boolean) -> Unit = {},
) {
    var isOverflowing by remember(text) { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                isOverflowing = result.hasVisualOverflow
                onOverflowChange(result.hasVisualOverflow)
            },
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

/**
 * The post's links, each on its own line and each opening in the browser when
 * tapped. Renders nothing when the post has no links. Shared by the feed card
 * and the detail screen so a link looks and behaves the same in both.
 */
@Composable
internal fun PostLinks(
    links: List<String>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    /**
     * When a link is one of the app's own share links, these open the target
     * in-app instead of the browser: a post, a community, or a profile. Left
     * null (or on a link the app can't resolve) the link opens in the browser.
     *
     * Routing in-app is what makes such a link open every time and close back
     * to the previous screen — going out through the browser bounces the link
     * back in through App Links, which resets the stack to the dashboard and
     * won't re-fire for the same id.
     */
    onOpenPost: ((String) -> Unit)? = null,
    onOpenCommunity: ((String) -> Unit)? = null,
    onOpenProfile: ((String) -> Unit)? = null,
) {
    if (links.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current

    // Launch the browser off the tap frame, not straight from the click.
    // Calling startActivity inline — right after coming back from the browser —
    // was intermittently dropped: the tap landed while the window was still
    // regaining focus. Bouncing through a state + LaunchedEffect lets the tap
    // and the focus settle first, so the link opens every time.
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pendingUrl) {
        val target = pendingUrl ?: return@LaunchedEffect
        openLink(context, target)
        pendingUrl = null
    }

    fun onLinkTap(link: String) {
        val normalized = if (link.contains("://")) link.trim() else "https://${link.trim()}"
        val uri = runCatching { android.net.Uri.parse(normalized) }.getOrNull()
        val postId = ShareLinks.postIdFrom(uri)
        val communityId = ShareLinks.communityIdFrom(uri)
        val userId = ShareLinks.profileIdFrom(uri)
        when {
            postId != null && onOpenPost != null -> onOpenPost(postId)
            communityId != null && onOpenCommunity != null -> onOpenCommunity(communityId)
            userId != null && onOpenProfile != null -> onOpenProfile(userId)
            // Not one of ours (or no in-app handler): open it in the browser.
            else -> pendingUrl = link
        }
    }

    Column(modifier = modifier) {
        links.forEachIndexed { index, link ->
            if (index > 0) Spacer(Modifier.height(4.dp))
            Text(
                text = link,
                style = MaterialTheme.typography.bodyMedium
                    .copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                color = MaterialTheme.colorScheme.primary,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onLinkTap(link) }
                    .padding(vertical = 2.dp),
            )
        }
    }
}

/**
 * Opens [url] in the browser (or whatever app claims it). A URL the user typed
 * often has no scheme — "example.com" — which ACTION_VIEW can't resolve, so a
 * missing scheme is filled in with https:// first.
 */
internal fun openLink(context: android.content.Context, url: String) {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return
    val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    try {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(normalized),
            )
        )
    } catch (_: Exception) {
        android.widget.Toast.makeText(
            context,
            "Couldn't open this link.",
            android.widget.Toast.LENGTH_SHORT,
        ).show()
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
