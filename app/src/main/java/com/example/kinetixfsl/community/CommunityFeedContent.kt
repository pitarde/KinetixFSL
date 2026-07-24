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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.model.Post
<<<<<<< HEAD
import com.example.kinetixfsl.ui.theme.KinetixGreen
=======
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixGreen
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixIndigo10
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
import com.example.kinetixfsl.ui.theme.KinetixSurface
import com.example.kinetixfsl.ui.theme.KinetixWhite
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f

@Composable
fun CommunityFeedContent(
    modifier: Modifier = Modifier,
    viewModel: CommunityFeedViewModel = viewModel(),
    onCommentClick: (Post) -> Unit = {},
    onPostClick: (Post) -> Unit = {},
) {
    val state by viewModel.feedState.collectAsStateWithLifecycle()
    val userVotes by viewModel.userVotes.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
<<<<<<< HEAD
            .background(MaterialTheme.colorScheme.background),
=======
            .background(KinetixWhite),
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
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
                        PostCard(
                            post = post,
                            userVote = userVotes[post.id],
                            onUpvote = { viewModel.vote(post.id, "up") },
                            onDownvote = { viewModel.vote(post.id, "down") },
                            onComment = { onCommentClick(post) },
                            onShare = { viewModel.share(context, post) },
                            onClick = { onPostClick(post) },
                        )
<<<<<<< HEAD
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
=======
                        HorizontalDivider(color = KinetixOutline, thickness = 1.dp)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
<<<<<<< HEAD
            .background(MaterialTheme.colorScheme.primaryContainer)
=======
            .background(KinetixIndigo10)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            text = "Search your sign",
            style = MaterialTheme.typography.bodyMedium,
<<<<<<< HEAD
            color = MaterialTheme.colorScheme.onSurfaceVariant,
=======
            color = KinetixMuted,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
<<<<<<< HEAD
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
=======
        CircularProgressIndicator(color = KinetixIndigo)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
    }
}

@Composable
private fun EmptyRow() {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
<<<<<<< HEAD
            Text("No posts yet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Be the first to share something with the community.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
=======
            Text("No posts yet.", style = MaterialTheme.typography.titleMedium, color = KinetixInk, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Be the first to share something with the community.", style = MaterialTheme.typography.bodyMedium, color = KinetixMuted)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        }
    }
}

@Composable
private fun ErrorRow(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
<<<<<<< HEAD
            Text("Couldn't load posts.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
=======
            Text("Couldn't load posts.", style = MaterialTheme.typography.titleMedium, color = KinetixError, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = KinetixMuted)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        }
    }
}

// ---- Post Card ----

@Composable
private fun PostCard(
    post: Post,
    userVote: String?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
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

<<<<<<< HEAD
        Text(post.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

        if (post.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(post.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
=======
        Text(post.title, style = MaterialTheme.typography.titleMedium, color = KinetixInk, fontWeight = FontWeight.Bold)

        if (post.body.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(post.body, style = MaterialTheme.typography.bodyMedium, color = KinetixInk)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        }

        if (!post.linkUrl.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = post.linkUrl,
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
<<<<<<< HEAD
                color = MaterialTheme.colorScheme.primary,
=======
                color = KinetixIndigo,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                maxLines = 1,
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
<<<<<<< HEAD
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
=======
                    .border(1.dp, KinetixOutline, RoundedCornerShape(12.dp)),
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
            )
        }

        Spacer(Modifier.height(12.dp))
        InteractionRow(
            post = post,
            userVote = userVote,
            onUpvote = onUpvote,
            onDownvote = onDownvote,
            onComment = onComment,
            onShare = onShare,
        )
    }
}

@Composable
private fun AuthorRow(post: Post) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!post.authorAvatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = post.authorAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
<<<<<<< HEAD
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            )
        } else {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
=======
                modifier = Modifier.size(32.dp).clip(CircleShape).background(KinetixSurface),
            )
        } else {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(KinetixIndigo10).border(1.dp, KinetixOutline, CircleShape),
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    post.authorName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.labelLarge,
<<<<<<< HEAD
                    color = MaterialTheme.colorScheme.primary,
=======
                    color = KinetixIndigo,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
<<<<<<< HEAD
        Text(post.authorName.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
=======
        Text(post.authorName.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleMedium, color = KinetixInk, fontWeight = FontWeight.SemiBold)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        Spacer(Modifier.width(8.dp))
        val timeLabel = post.createdAt.relativeToNow()
        val viewsLabel = if (post.viewCount > 0) "${post.viewCount.compact()} views" else null
        val meta = listOfNotNull(timeLabel.takeIf { it.isNotBlank() }, viewsLabel).joinToString(" · ")
        if (meta.isNotBlank()) {
<<<<<<< HEAD
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
=======
            Text(meta, style = MaterialTheme.typography.labelMedium, color = KinetixMuted)
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
        }
    }
}

/**
 * Interaction row with functional vote buttons. The arrow that matches the
 * user's current vote is coloured (green for up, red for down); the other is
 * muted. Tapping the same arrow again undoes the vote (toggle).
 */
@Composable
private fun InteractionRow(
    post: Post,
    userVote: String?,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
<<<<<<< HEAD
        val defaultTint = MaterialTheme.colorScheme.onSurface
        val outlineColor = MaterialTheme.colorScheme.outline
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, outlineColor, RoundedCornerShape(50))
=======
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, KinetixOutline, RoundedCornerShape(50))
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InteractionButton(
                icon = CommunityIcons.ArrowUp,
                label = post.upvoteCount.compact(),
<<<<<<< HEAD
                tint = if (userVote == "up") KinetixGreen else defaultTint,
=======
                tint = if (userVote == "up") KinetixGreen else KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                onClick = onUpvote,
            )
            VerticalHairline()
            InteractionButton(
                icon = CommunityIcons.ArrowDown,
                label = post.downvoteCount.compact(),
<<<<<<< HEAD
                tint = if (userVote == "down") MaterialTheme.colorScheme.error else defaultTint,
=======
                tint = if (userVote == "down") KinetixError else KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                onClick = onDownvote,
            )
            VerticalHairline()
            InteractionButton(
                icon = CommunityIcons.Comment,
                label = post.commentCount.compact(),
<<<<<<< HEAD
                tint = defaultTint,
=======
                tint = KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                onClick = onComment,
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
<<<<<<< HEAD
                .border(1.dp, outlineColor, RoundedCornerShape(50))
=======
                .border(1.dp, KinetixOutline, RoundedCornerShape(50))
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            InteractionButton(
                icon = CommunityIcons.Share,
                label = post.shareCount.compact(),
<<<<<<< HEAD
                tint = defaultTint,
=======
                tint = KinetixInk,
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun InteractionButton(
    icon: ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VerticalHairline() {
<<<<<<< HEAD
    Box(Modifier.width(1.dp).height(18.dp).background(MaterialTheme.colorScheme.outline))
=======
    Box(Modifier.width(1.dp).height(18.dp).background(KinetixOutline))
>>>>>>> 9c469b77aa869ad39b82860faa4861e04e46126f
}
