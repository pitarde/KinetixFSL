package com.example.kinetixfsl.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler

/**
 * The sheet behind a post's 3-dot button: copy the text, delete the post, or
 * open it in the editor.
 *
 * Dimmed backdrop with a close button top-right, matching the mockup. Tapping
 * the backdrop or pressing back also closes it.
 */
@Composable
internal fun PostActionsSheet(
    onCopyText: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.surface)
                // Swallow taps so they don't fall through to the backdrop.
                .clickable(enabled = false) {}
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 16.dp, top = 8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
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

            Spacer(Modifier.height(28.dp))

            ActionRow(
                icon = CommunityIcons.CopyText,
                label = "Copy text",
                onClick = onCopyText,
            )
            ActionRow(
                icon = CommunityIcons.Delete,
                label = "Delete",
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
            )
            ActionRow(
                icon = CommunityIcons.EditPost,
                label = "Edit post",
                onClick = onEdit,
            )

            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Deleting a post can't be undone, so it gets a confirmation step rather than
 * firing straight off the menu.
 */
@Composable
internal fun ConfirmDeleteDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete post?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                text = "\"$title\" will be removed for everyone. This can't be undone.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Text(
                text = "Delete",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        dismissButton = {
            Text(
                text = "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}
