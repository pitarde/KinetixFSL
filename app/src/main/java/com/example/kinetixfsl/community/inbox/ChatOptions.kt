package com.example.kinetixfsl.community.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.community.inbox.model.ChatMessage
import com.example.kinetixfsl.community.mediaUrl
import com.example.kinetixfsl.community.openLink

/**
 * The sheets, dialogs and gallery hanging off a conversation: the ⋮ menu, the
 * long-press delete confirmations, and the shared media/links browser.
 *
 * Split out of `ChatScreen` because that file is already the thread, the
 * header and the composer — this is the layer that opens *over* all three, and
 * keeping it separate keeps both readable.
 */

// ---------------------------------------------------------------------------
// The ⋮ menu
// ---------------------------------------------------------------------------

/**
 * What the three-dot button opens.
 *
 * Ordered least to most destructive, with Block and Delete last and tinted —
 * the two entries that are hard or impossible to undo shouldn't sit under a
 * thumb reaching for "View profile".
 */
@Composable
internal fun ChatOptionsSheet(
    isBlocked: Boolean,
    onViewProfile: () -> Unit,
    onViewMedia: () -> Unit,
    onReport: () -> Unit,
    onToggleBlock: () -> Unit,
    onDeleteConversation: () -> Unit,
    onDismiss: () -> Unit,
) {
    SheetScaffold(onDismiss = onDismiss) {
        SheetRow(CommunityIcons.Profile, "View profile", onViewProfile)
        SheetRow(CommunityIcons.Image, "Media, files and links", onViewMedia)
        SheetRow(CommunityIcons.Report, "Report user", onReport)

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        SheetRow(
            icon = CommunityIcons.Hide,
            label = if (isBlocked) "Unblock user" else "Block user",
            onClick = onToggleBlock,
            tint = if (isBlocked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        SheetRow(
            icon = CommunityIcons.Delete,
            label = "Delete conversation",
            onClick = onDeleteConversation,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * The app's own dimmed bottom sheet, matching the post-actions sheet rather
 * than Material's `ModalBottomSheet` — same backdrop, same tap-outside and
 * back-to-close behaviour, and no experimental API.
 */
@Composable
private fun SheetScaffold(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(MaterialTheme.colorScheme.surface)
                // Swallow taps on the sheet, so choosing an item doesn't also
                // register as a tap on the backdrop behind it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(21.dp),
        )
        Spacer(Modifier.width(18.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------------
// Confirmations
// ---------------------------------------------------------------------------

/**
 * A yes/no confirmation. Used for every destructive action in the Inbox so
 * they all read and behave the same way.
 *
 * The confirm button is tinted with the error colour and the dismiss button is
 * plain, so the destructive choice is the one that stands out as unusual rather
 * than the one that looks like the default.
 */
@Composable
internal fun ConfirmDialog(
    title: String,
    body: String? = null,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        },
        text = body?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/**
 * The wording for deleting one thing out of a thread.
 *
 * Named after what the user is actually looking at — a photo, a clip, a link —
 * because "Do you want to delete this message?" over a picture reads as though
 * it might mean something else.
 */
internal fun deletePromptFor(message: ChatMessage): String = when {
    message.isVideo -> "Do you want to delete this video?"
    message.hasMedia -> "Do you want to delete this image?"
    message.text.isLink() -> "Do you want to delete this link?"
    else -> "Do you want to delete this message?"
}

/** True when the text is nothing but a URL — used only to word the dialog. */
private fun String.isLink(): Boolean {
    val trimmed = trim()
    return trimmed.isNotEmpty() &&
        !trimmed.contains(' ') &&
        LINK_HINT.containsMatchIn(trimmed)
}

private val LINK_HINT = Regex("""^(https?://\S+|www\.\S+|[\w-]+\.[a-zA-Z]{2,}(/\S*)?)$""")

// ---------------------------------------------------------------------------
// Media, files and links
// ---------------------------------------------------------------------------

/**
 * Everything shared in this conversation, on two tabs.
 *
 * Built from the messages the chat screen already holds rather than a fresh
 * query — the thread listener has the last hundred messages in memory, and
 * re-reading them to group by type would be paying twice for the same data. The
 * consequence, stated plainly: this covers the recent history the screen knows
 * about, not the entire archive of the thread.
 */
@Composable
internal fun ChatMediaScreen(
    messages: List<ChatMessage>,
    onOpenMedia: (ChatMessage) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    var tab by remember { mutableStateOf(MediaTab.MEDIA) }
    val media = remember(messages) { messages.filter { it.hasMedia } }
    val links = remember(messages) { messages.filter { it.text.containsUrl() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CommunityIcons.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose)
                    .padding(8.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Media, files and links",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            MediaTab.entries.forEach { entry ->
                val selected = entry == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = entry }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(2.dp)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when (tab) {
            MediaTab.MEDIA -> if (media.isEmpty()) {
                EmptyNotice("Nothing shared yet", "Photos and videos appear here.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(media, key = { it.id }) { message ->
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onOpenMedia(message) },
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = mediaUrl(message.previewUrl.orEmpty()),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (message.isVideo) {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = InboxIcons.Play,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            MediaTab.LINKS -> if (links.isEmpty()) {
                EmptyNotice("No links yet", "Links shared in this chat appear here.")
            } else {
                val context = LocalContext.current
                LazyColumn(Modifier.fillMaxSize()) {
                    items(links, key = { it.id }) { message ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    message.text.firstUrl()?.let { openLink(context, it) }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(
                                text = message.text.firstUrl().orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = message.createdAt.inboxTime(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private enum class MediaTab(val label: String) {
    MEDIA("Media"),
    LINKS("Links"),
}

@Composable
private fun EmptyNotice(title: String, subtitle: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val URL_IN_TEXT = Regex(
    """(https?://\S+)|(www\.\S+)|([a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/\S*)?)""",
)

internal fun String.containsUrl(): Boolean = URL_IN_TEXT.containsMatchIn(this)

internal fun String.firstUrl(): String? = URL_IN_TEXT.find(this)?.value
