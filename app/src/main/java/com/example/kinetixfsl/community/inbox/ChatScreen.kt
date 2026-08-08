package com.example.kinetixfsl.community.inbox

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.kinetixfsl.community.Avatar
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.community.FullScreenMediaViewer
import com.example.kinetixfsl.community.inbox.model.ChatMessage
import com.example.kinetixfsl.community.mediaUrl
import com.example.kinetixfsl.community.openLink
import com.example.kinetixfsl.ui.theme.KinetixGreen

/**
 * One open conversation: the thread, the composer, and the header that says who
 * you're talking to and whether they're around.
 *
 * Rendered as an overlay over the community scaffold (same stack the post
 * detail and profile screens use), so backing out lands wherever the user came
 * from — the inbox, a profile, or a notification.
 */
@Composable
fun ChatScreen(
    conversationId: String,
    recipientId: String,
    onClose: () -> Unit,
    /** Opens the other person's profile from the header. */
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    /**
     * The chat's ViewModel, in a store of its own that is cleared when this
     * screen leaves the composition.
     *
     * A plain `remember { ChatViewModel(...) }` would never be cleared —
     * `onCleared` only runs when a ViewModelStore drops it — so every thread
     * the user opened would leave its message and presence listeners running
     * for the rest of the session. A private store makes closing the chat
     * actually close the chat.
     */
    val store = remember(conversationId) { ViewModelStore() }
    DisposableEffect(store) { onDispose { store.clear() } }
    val storeOwner = remember(store) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    val viewModel: ChatViewModel = viewModel(
        viewModelStoreOwner = storeOwner,
        factory = viewModelFactory {
            initializer { ChatViewModel(conversationId, recipientId) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    BackHandler(onBack = onClose)

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.dismissError()
    }

    /** The media a tapped bubble opened full screen, or null. */
    var viewing by remember { mutableStateOf<ChatMessage?>(null) }

    // Follow the conversation down as it grows. Keyed on the counts rather than
    // the lists so an edit to read receipts (which rewrites message objects
    // without adding any) doesn't yank the user away from where they scrolled.
    val totalRows = state.messages.size + state.outbox.size
    LaunchedEffect(totalRows, state.isOtherTyping) {
        if (totalRows > 0) listState.animateScrollToItem(totalRows - 1)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        ChatHeader(
            name = state.otherName,
            photo = state.otherPhoto,
            isOnline = state.isOtherOnline,
            isTyping = state.isOtherTyping,
            onBack = onClose,
            onOpenProfile = { onOpenProfile(recipientId) },
        )

        Box(Modifier.weight(1f)) {
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                state.messages.isEmpty() && state.outbox.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Say hello to ${state.otherName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(
                        items = state.messages,
                        key = { index, message -> message.id.ifBlank { index.toString() } },
                    ) { index, message ->
                        val previous = state.messages.getOrNull(index - 1)
                        // A separator whenever the calendar day changes —
                        // including before the very first message.
                        val dayChanged = previous == null ||
                            previous.createdAt.dayLabel() != message.createdAt.dayLabel()

                        if (dayChanged) {
                            DaySeparator(message.createdAt.dayLabel())
                        }

                        MessageBubble(
                            message = message,
                            isMine = message.senderId == state.currentUid,
                            // Only the newest message in a run from the same
                            // sender shows its time, so a burst of five doesn't
                            // repeat the same clock five times.
                            showTime = state.messages.getOrNull(index + 1)
                                ?.senderId != message.senderId,
                            showSeen = message.id == state.lastSeenOutgoingId,
                            onMediaClick = { viewing = message },
                        )
                    }

                    // Messages still uploading, always ours and always newest.
                    items(state.outbox, key = { it.localId }) { entry ->
                        PendingBubble(
                            entry = entry,
                            onRetry = { viewModel.retry(context, entry.localId) },
                            onDiscard = { viewModel.discard(entry.localId) },
                        )
                    }

                    if (state.isOtherTyping) {
                        item(key = "typing") { TypingBubble() }
                    }
                }
            }
        }

        Composer(
            state = state,
            onDraftChange = viewModel::onDraftChange,
            onPickImage = { viewModel.onAttachmentPicked(it, "image") },
            onPickVideo = { viewModel.onAttachmentPicked(it, "video") },
            onClearAttachment = viewModel::clearAttachment,
            onSend = { viewModel.send(context) },
        )
    }

    // Drawn outside the Column so it covers the header and composer too — a
    // full-screen viewer that stopped short of the status bar would read as a
    // panel rather than as the photo taking over.
    viewing?.let { message ->
        FullScreenMediaViewer(
            imageUrl = message.mediaUrl?.takeIf { !message.isVideo },
            videoUrl = message.mediaUrl?.takeIf { message.isVideo },
            onClose = { viewing = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun ChatHeader(
    name: String,
    photo: String?,
    isOnline: Boolean,
    isTyping: Boolean,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val context = LocalContext.current

    Column {
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
                    .clickable(onClick = onBack)
                    .padding(8.dp),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenProfile)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Avatar(avatarUrl = photo, name = name, size = 38.dp)
                    if (isOnline) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(KinetixGreen),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = name.ifBlank { "Chat" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    val status = when {
                        isTyping -> "typing…"
                        isOnline -> "Active now"
                        else -> null
                    }
                    if (status != null) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isTyping) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                KinetixGreen
                            },
                        )
                    }
                }
            }

            // Block and report both belong to a moderation queue that lives on
            // the admin web app, not in the phone — until that exists, saying so
            // is more honest than a button that quietly does nothing.
            Icon(
                imageVector = CommunityIcons.MoreVertical,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        Toast.makeText(
                            context,
                            "Block and report — coming soon.",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    .padding(10.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

@Composable
private fun DaySeparator(label: String) {
    if (label.isBlank()) return
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * One bubble. Mine sit right in the primary colour, theirs sit left on the
 * surface colour — both drawn from the theme, so the whole thread inverts
 * correctly in dark mode without a second palette.
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    showTime: Boolean,
    showSeen: Boolean,
    onMediaClick: () -> Unit,
) {
    val bubbleColor = if (isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // The corner nearest the sender is squared off — the standard cue for which
    // side a message came from, readable even with the colours stripped out.
    val shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isMine) 16.dp else 4.dp,
        bottomEnd = if (isMine) 4.dp else 16.dp,
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(if (message.hasMedia) 4.dp else 0.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            if (message.hasMedia) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onMediaClick),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        // A video draws its uploaded still, not the clip —
                        // Coil can't turn an MP4 URL into a bitmap, so pointing
                        // it at the video gives an empty box. Bucket URLs are
                        // rewritten onto the Worker's domain, which some
                        // carriers don't block, the same treatment feed media
                        // already gets.
                        model = mediaUrl(message.previewUrl.orEmpty()),
                        contentDescription = if (message.isVideo) "Video" else "Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .heightIn(max = 320.dp),
                    )
                    if (message.isVideo) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = InboxIcons.Play,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            if (message.text.isNotBlank()) {
                LinkifiedText(
                    text = message.text,
                    color = textColor,
                    // On a filled bubble the usual link blue disappears against
                    // the primary colour, so links there stay the bubble's own
                    // text colour and rely on the underline instead.
                    linkColor = if (isMine) textColor else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }

        if (showTime || showSeen) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showTime) {
                    Text(
                        text = message.createdAt.clockTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showSeen) {
                    if (showTime) Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = InboxIcons.Seen,
                        contentDescription = "Seen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "Seen",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * A message that hasn't reached Firestore yet — always ours, always at the
 * bottom.
 *
 * Dimmed while it's in flight so it reads as provisional without moving or
 * resizing when it lands; a bubble that changed shape on delivery would make
 * the whole thread twitch. A failed one turns into a retry/discard pair rather
 * than vanishing, because silently dropping something the user wrote is the
 * one outcome worth avoiding entirely.
 */
@Composable
private fun PendingBubble(
    entry: PendingMessage,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(shape)
                .background(
                    MaterialTheme.colorScheme.primary
                        .copy(alpha = if (entry.failed) 0.35f else 0.55f)
                )
                .padding(if (entry.uri != null) 4.dp else 0.dp),
            horizontalAlignment = Alignment.End,
        ) {
            if (entry.uri != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    // Rendered straight from the local content:// uri — this is
                    // what puts the photo on screen before a byte has uploaded.
                    // A video uri won't decode to a bitmap here either, so it
                    // shows as an empty tile behind the spinner until the real
                    // message arrives with its uploaded still.
                    AsyncImage(
                        model = entry.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .heightIn(max = 320.dp)
                            .then(
                                // A video has no local preview to size the box,
                                // so give it one rather than collapsing to zero.
                                if (entry.isVideo) Modifier.size(200.dp) else Modifier
                            ),
                    )
                    if (!entry.failed) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            if (entry.text.isNotBlank()) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.failed) {
                Text(
                    text = "Not sent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = "Discard",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDiscard)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    text = "Sending…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Message text with any URLs in it turned into tappable links.
 *
 * Uses [ClickableText] rather than the newer `LinkAnnotation` API so the
 * behaviour doesn't depend on which Compose version the BOM resolves to, and
 * routes taps through the app's existing [openLink], which fills in a missing
 * `https://` before handing the URL to the system — a pasted "example.com"
 * would otherwise resolve to nothing.
 */
@Composable
private fun LinkifiedText(
    text: String,
    color: Color,
    linkColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val annotated = remember(text, color, linkColor) {
        buildAnnotatedString {
            var cursor = 0
            for (match in URL_PATTERN.findAll(text)) {
                if (match.range.first > cursor) {
                    append(text.substring(cursor, match.range.first))
                }
                val url = match.value
                pushStringAnnotation(tag = URL_TAG, annotation = url)
                withStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                    )
                ) { append(url) }
                pop()
                cursor = match.range.last + 1
            }
            if (cursor < text.length) append(text.substring(cursor))
        }
    }

    // No links: a plain Text, so ordinary messages keep normal text selection
    // and don't pay for gesture handling they never use.
    if (annotated.getStringAnnotations(URL_TAG, 0, annotated.length).isEmpty()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = modifier,
        )
        return
    }

    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(color = color),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()
                ?.let { openLink(context, it.item) }
        },
    )
}

private const val URL_TAG = "url"

/**
 * Matches a URL with a scheme, a bare `www.` address, or a plain
 * `domain.tld/path`.
 *
 * The last case is why the trailing character class excludes `.` and `,` — a
 * link at the end of a sentence would otherwise swallow the full stop and
 * 404. Two-letter minimum on the TLD keeps it from linkifying "e.g".
 */
private val URL_PATTERN = Regex(
    """(https?://[^\s]+)|(www\.[^\s]+)|([a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?)""",
)

/** Three dots in a bubble on the left — the other side is writing. */
@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Composer
// ---------------------------------------------------------------------------

@Composable
private fun Composer(
    state: ChatUiState,
    onDraftChange: (String) -> Unit,
    onPickImage: (android.net.Uri) -> Unit,
    onPickVideo: (android.net.Uri) -> Unit,
    onClearAttachment: () -> Unit,
    onSend: () -> Unit,
) {
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPickImage) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onPickVideo) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            // This exact chain, in this order: navigationBarsPadding consumes
            // the nav-bar inset, so imePadding then adds only the *remaining*
            // keyboard height. Applying imePadding higher up instead leaves the
            // nav-bar gap stacked on top of the keyboard, floating the composer
            // an extra bar's height above it.
            .navigationBarsPadding()
            .imePadding(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // The attachment sits above the input rather than inside it, so a photo
        // never squeezes the text field down to a sliver.
        state.pending?.let { pending ->
            Box(Modifier.padding(start = 16.dp, top = 10.dp)) {
                AsyncImage(
                    model = pending.uri,
                    contentDescription = "Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Icon(
                    imageVector = CommunityIcons.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = onClearAttachment)
                        .padding(3.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Icon(
                imageVector = CommunityIcons.Image,
                contentDescription = "Attach photo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .padding(8.dp),
            )
            Icon(
                imageVector = InboxIcons.Video,
                contentDescription = "Attach video",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    }
                    .padding(8.dp),
            )

            Spacer(Modifier.width(6.dp))

            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (state.draft.isEmpty()) {
                    Text(
                        text = "Message…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    textStyle = LocalTextStyle.current.merge(
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.primary
                    ),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.width(8.dp))

            val sendEnabled = state.canSend
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (sendEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable(enabled = sendEnabled, onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                // No spinner state: a send never occupies this button. The
                // message moves to the thread as a pending bubble immediately
                // and uploads from there, so the composer is free for the next
                // one before this one has finished.
                Icon(
                    imageVector = InboxIcons.Send,
                    contentDescription = "Send",
                    tint = if (sendEnabled) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}
