package com.example.kinetixfsl.community

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixIndigo10
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
import com.example.kinetixfsl.ui.theme.KinetixSurface
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * The feed content. Shows a search bar, then either a loading spinner, the
 * post list, an empty message, or an error. All post-card interactions are
 * visual only for now — every button will call [onPostClick] or a TODO.
 */
@Composable
fun CommunityFeedContent(
    modifier: Modifier = Modifier,
    viewModel: CommunityFeedViewModel = viewModel(),
    onPostClick: (Post) -> Unit = {},
) {
    val state by viewModel.feedState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(KinetixWhite),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item { SearchBar() }
        item { Spacer(Modifier.height(8.dp)) }

        when (val current = state) {
            is FeedState.Loading -> item { LoadingRow() }
            is FeedState.Error -> item { ErrorRow(message = current.message) }
            is FeedState.Success -> {
                if (current.posts.isEmpty()) {
                    item { EmptyRow() }
                } else {
                    items(current.posts, key = { it.id }) { post ->
                        PostCard(post = post, onClick = { onPostClick(post) })
                        HorizontalDivider(color = KinetixOutline, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

/**
 * A visual-only search field. Wired to the search feature when it's built —
 * for now, tapping it does nothing.
 */
@Composable
private fun SearchBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(KinetixIndigo10)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Search your sign",
            style = MaterialTheme.typography.bodyMedium,
            color = KinetixMuted,
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = KinetixIndigo)
    }
}

@Composable
private fun EmptyRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No posts yet.",
                style = MaterialTheme.typography.titleMedium,
                color = KinetixInk,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Be the first to share something with the community.",
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixMuted,
            )
        }
    }
}

@Composable
private fun ErrorRow(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Couldn't load posts.",
                style = MaterialTheme.typography.titleMedium,
                color = KinetixError,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixMuted,
            )
        }
    }
}

// ---------------------------------------------------------------------------------
// The post card, matching the mockup: author row, title, body, image, interaction row.
// ---------------------------------------------------------------------------------

@Composable
private fun PostCard(
    post: Post,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AuthorRow(post = post)

        Spacer(Modifier.height(8.dp))

        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            color = KinetixInk,
            fontWeight = FontWeight.Bold,
        )
        if (post.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = post.body,
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixInk,
            )
        }

        if (!post.imageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = post.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, KinetixOutline, RoundedCornerShape(12.dp)),
            )
        }

        Spacer(Modifier.height(12.dp))

        InteractionRow(post = post)
    }
}

@Composable
private fun AuthorRow(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Avatar — Coil if we have a URL, else a plain circle placeholder.
        if (!post.authorAvatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = post.authorAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(KinetixSurface),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(KinetixIndigo10)
                    .border(1.dp, KinetixOutline, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = post.authorName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    color = KinetixIndigo,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = post.authorName.ifBlank { "Unknown" },
            style = MaterialTheme.typography.titleMedium,
            color = KinetixInk,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.width(8.dp))

        // "9h · 10k views" — small, muted metadata.
        val timeLabel = post.createdAt.relativeToNow()
        val viewsLabel = if (post.viewCount > 0) "${post.viewCount.compact()} views" else null
        val meta = listOfNotNull(timeLabel.takeIf { it.isNotBlank() }, viewsLabel).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = KinetixMuted,
            )
        }
    }
}

/**
 * The row of interaction pills — upvote/downvote/comment on the left, share on
 * the right. Every button is visual only for now (marked with TODO in the
 * onClick callbacks).
 */
@Composable
private fun InteractionRow(post: Post) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Combined upvote / downvote / comment pill on the left.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, KinetixOutline, RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InteractionButton(
                icon = CommunityIcons.ArrowUp,
                label = post.upvoteCount.compact(),
                onClick = { /* TODO(vote-up) */ },
            )
            VerticalHairline()
            InteractionButton(
                icon = CommunityIcons.ArrowDown,
                label = post.downvoteCount.compact(),
                onClick = { /* TODO(vote-down) */ },
            )
            VerticalHairline()
            InteractionButton(
                icon = CommunityIcons.Comment,
                label = post.commentCount.compact(),
                onClick = { /* TODO(comments) */ },
            )
        }

        // Share pill on the right.
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, KinetixOutline, RoundedCornerShape(50))
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            InteractionButton(
                icon = CommunityIcons.Share,
                label = post.shareCount.compact(),
                onClick = { /* TODO(share) */ },
            )
        }
    }
}

@Composable
private fun InteractionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KinetixInk,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = KinetixInk,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Thin vertical divider between the three left-side interaction buttons. */
@Composable
private fun VerticalHairline() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(KinetixOutline),
    )
}