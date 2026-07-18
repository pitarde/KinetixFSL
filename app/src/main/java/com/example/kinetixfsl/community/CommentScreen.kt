package com.example.kinetixfsl.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.ui.theme.KinetixError
import com.example.kinetixfsl.ui.theme.KinetixIndigo
import com.example.kinetixfsl.ui.theme.KinetixIndigo10
import com.example.kinetixfsl.ui.theme.KinetixInk
import com.example.kinetixfsl.ui.theme.KinetixMuted
import com.example.kinetixfsl.ui.theme.KinetixOutline
import com.example.kinetixfsl.ui.theme.KinetixWhite

/**
 * The comment screen — a full-screen overlay showing the post's title at the top,
 * the list of existing comments, and an input bar at the bottom.
 *
 * [viewModel] must be created by the caller with the correct postId, because
 * Compose's default `viewModel()` doesn't support constructor args without a factory.
 */
@Composable
fun CommentScreen(
    post: Post,
    viewModel: CommentViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KinetixWhite)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // ---- Top bar ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ArrowBackIcon,
                contentDescription = "Back",
                tint = KinetixInk,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(onClick = onClose),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleLarge,
                color = KinetixInk,
                fontWeight = FontWeight.Bold,
            )
        }

        HorizontalDivider(color = KinetixOutline)

        // ---- Post summary ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(KinetixIndigo10)
                .padding(16.dp),
        ) {
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
                    color = KinetixMuted,
                    maxLines = 2,
                )
            }
        }

        HorizontalDivider(color = KinetixOutline)

        // ---- Comments list ----
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = KinetixIndigo)
                    }
                }
                state.comments.isEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No comments yet. Be the first!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetixMuted,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Top,
                    ) {
                        items(state.comments, key = { it.id }) { comment ->
                            CommentRow(comment = comment)
                            HorizontalDivider(color = KinetixOutline)
                        }
                    }
                }
            }
        }

        // ---- Error ----
        if (state.errorMessage != null) {
            Text(
                text = state.errorMessage!!,
                style = MaterialTheme.typography.labelMedium,
                color = KinetixError,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ---- Input bar ----
        HorizontalDivider(color = KinetixOutline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KinetixWhite)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, KinetixOutline, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = state.commentText,
                    onValueChange = viewModel::onCommentTextChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = KinetixInk),
                    cursorBrush = SolidColor(KinetixIndigo),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (state.commentText.isEmpty()) {
                                Text(
                                    "Write a comment...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = KinetixMuted,
                                )
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.width(8.dp))

            // Send button
            val canSend = state.commentText.isNotBlank() && !state.isSending
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (canSend) KinetixIndigo else KinetixOutline)
                    .clickable(enabled = canSend) { viewModel.sendComment() },
                contentAlignment = Alignment.Center,
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        color = KinetixWhite,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        imageVector = SendIcon,
                        contentDescription = "Send",
                        tint = KinetixWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: Comment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(KinetixIndigo10)
                .border(1.dp, KinetixOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                comment.authorName.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.labelMedium,
                color = KinetixIndigo,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    comment.authorName.ifBlank { "Unknown" },
                    style = MaterialTheme.typography.labelLarge,
                    color = KinetixInk,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    comment.createdAt.relativeToNow(),
                    style = MaterialTheme.typography.labelMedium,
                    color = KinetixMuted,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                comment.body,
                style = MaterialTheme.typography.bodyMedium,
                color = KinetixInk,
            )
        }
    }
}

// ---- Small inline icons ----

private val ArrowBackIcon: ImageVector by lazy {
    ImageVector.Builder("ArrowBack", 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2.4f) {
            moveTo(20f, 12f); lineTo(5f, 12f)
            moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
        }
    }.build()
}

private val SendIcon: ImageVector by lazy {
    ImageVector.Builder("Send", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 4f); lineTo(21f, 12f); lineTo(3f, 20f); lineTo(5f, 12f); close()
        }
    }.build()
}
