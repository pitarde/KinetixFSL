package com.example.kinetixfsl.community.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.Avatar
import com.example.kinetixfsl.community.CommunityIcons
import com.example.kinetixfsl.community.inbox.model.Conversation
import com.example.kinetixfsl.community.inbox.model.NotificationItem
import com.example.kinetixfsl.community.inbox.model.NotificationType
import com.example.kinetixfsl.ui.theme.KinetixGreen

/**
 * The Inbox — direct messages on one tab, notifications on the other.
 *
 * Both live on one screen because they answer the same question ("what happened
 * while I was away?"), and one [InboxViewModel] holds both streams so the badge
 * on each tab stays correct while the other one is showing.
 */
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    /** Opens a thread. The screen above this one pushes the chat overlay. */
    onOpenConversation: (conversationId: String, otherUid: String) -> Unit,
    /** Tapping a notification that points at a post. */
    onOpenPost: (String) -> Unit,
    /** Tapping a notification that points at a person. */
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isPickerOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        InboxTabRow(
            selected = state.selectedTab,
            unreadChats = state.unreadChats,
            unreadNotifications = state.unreadNotifications,
            onSelect = viewModel::selectTab,
        )

        when (state.selectedTab) {
            InboxTab.CHAT -> ChatListContent(
                state = state,
                onQueryChange = viewModel::onSearchQueryChange,
                onOpenConversation = onOpenConversation,
                onNewMessage = { isPickerOpen = true },
            )

            InboxTab.NOTIFICATION -> NotificationListContent(
                state = state,
                onClearAll = viewModel::clearNotifications,
                onDelete = viewModel::deleteNotification,
                onMarkRead = viewModel::markNotificationRead,
                onOpenConversation = onOpenConversation,
                onOpenPost = onOpenPost,
                onOpenProfile = onOpenProfile,
            )
        }
    }

    if (isPickerOpen) {
        NewMessageSheet(
            viewModel = viewModel,
            onDismiss = { isPickerOpen = false },
            onPick = { candidate ->
                isPickerOpen = false
                viewModel.openConversationWith(
                    uid = candidate.uid,
                    name = candidate.displayName,
                    photo = candidate.avatarUrl,
                ) { conversationId -> onOpenConversation(conversationId, candidate.uid) }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Tab switcher
// ---------------------------------------------------------------------------

/**
 * Chat / Notification, each with its own unread count.
 *
 * An underline rather than a filled pill: the tab bar sits directly under the
 * screen title, and two filled shapes that close together read as buttons the
 * user is meant to press, not as where they already are.
 */
@Composable
private fun InboxTabRow(
    selected: InboxTab,
    unreadChats: Int,
    unreadNotifications: Int,
    onSelect: (InboxTab) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            InboxTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val count = when (tab) {
                    InboxTab.CHAT -> unreadChats
                    InboxTab.NOTIFICATION -> unreadNotifications
                }
                val tint by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "inboxTabTint",
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = tint,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                        if (count > 0) {
                            Spacer(Modifier.width(6.dp))
                            CountBadge(count)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(2.dp)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    androidx.compose.ui.graphics.Color.Transparent
                                },
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** The little "3" / "99+" pill. Shared by the tabs and the bottom-nav bell. */
@Composable
internal fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onError,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------------------------------------------------------------------
// Chat tab
// ---------------------------------------------------------------------------

@Composable
private fun ChatListContent(
    state: InboxUiState,
    onQueryChange: (String) -> Unit,
    onOpenConversation: (String, String) -> Unit,
    onNewMessage: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(
                value = state.searchQuery,
                onValueChange = onQueryChange,
                placeholder = "Search messages",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = InboxIcons.NewMessage,
                contentDescription = "New message",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNewMessage)
                    .padding(8.dp),
            )
        }

        when {
            state.isLoadingChats -> LoadingBlock()

            state.visibleConversations.isEmpty() -> EmptyBlock(
                title = if (state.searchQuery.isBlank()) "No messages yet" else "No matches",
                subtitle = if (state.searchQuery.isBlank()) {
                    "Start a conversation from someone's profile, or tap the pencil above."
                } else {
                    "Nobody by that name in your inbox."
                },
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.visibleConversations, key = { it.id }) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        me = state.currentUid,
                        onClick = {
                            onOpenConversation(conversation.id, conversation.otherId(state.currentUid))
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 78.dp),
                    )
                }
            }
        }
    }
}

/**
 * One inbox row: avatar with a presence dot, name, last-message preview, the
 * time, and an unread badge.
 *
 * Unread rows carry their weight in the name and the preview rather than a
 * background tint — a list where half the rows are highlighted stops reading as
 * a list, and the badge on the right already says which ones are new.
 */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    me: String,
    onClick: () -> Unit,
) {
    val unread = conversation.unreadFor(me)
    val isUnread = unread > 0
    val otherId = conversation.otherId(me)
    // Remembered per uid: without this every recomposition of the row would
    // build a fresh Flow and re-subscribe to the presence node.
    val presenceFlow = remember(otherId) { PresenceRepository.observeOnline(otherId) }
    val isOnline by presenceFlow.collectAsStateWithLifecycle(initialValue = false)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(
                avatarUrl = conversation.otherPhoto(me),
                name = conversation.otherName(me),
                size = 50.dp,
            )
            if (isOnline) {
                // Ringed in the page background so the dot reads as a separate
                // object on top of the avatar rather than a hole punched in it —
                // and the ring follows the theme, so it works on both.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(KinetixGreen),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = conversation.otherName(me),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = when {
                    conversation.isOtherTyping(me) -> "typing…"
                    conversation.preview(me).isBlank() -> "Say hello"
                    else -> conversation.preview(me)
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    conversation.isOtherTyping(me) -> MaterialTheme.colorScheme.primary
                    isUnread -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = conversation.lastMessageTime.inboxTime(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isUnread) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (isUnread) {
                Spacer(Modifier.height(6.dp))
                CountBadge(unread.toInt())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Notification tab
// ---------------------------------------------------------------------------

@Composable
private fun NotificationListContent(
    state: InboxUiState,
    onClearAll: () -> Unit,
    onDelete: (String) -> Unit,
    onMarkRead: (String) -> Unit,
    onOpenConversation: (String, String) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (state.notifications.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        when {
            state.isLoadingNotifications -> LoadingBlock()

            state.notifications.isEmpty() -> EmptyBlock(
                title = "You're all caught up",
                subtitle = "Replies, follows, mentions and community posts land here.",
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.notifications, key = { it.id }) { item ->
                    NotificationRow(
                        item = item,
                        onClick = {
                            // Read first, navigate second. A tap is the user
                            // telling us they've seen this row, whether or not
                            // it has anywhere to take them — which is the only
                            // way an account notice ever gets marked read.
                            onMarkRead(item.id)

                            when (item.kind) {
                                NotificationType.MESSAGE ->
                                    onOpenConversation(item.targetId, item.fromUserId)

                                NotificationType.FOLLOW ->
                                    onOpenProfile(item.targetId.ifBlank { item.fromUserId })

                                NotificationType.LIKE,
                                NotificationType.COMMENT,
                                NotificationType.MENTION,
                                NotificationType.ANNOUNCEMENT,
                                -> if (item.targetId.isNotBlank()) onOpenPost(item.targetId)

                                // Account notices have nowhere to go.
                                NotificationType.SYSTEM -> Unit
                            }
                        },
                        onDismiss = { onDelete(item.id) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 78.dp),
                    )
                }
            }
        }
    }
}

/**
 * One notification: the sender's avatar with a small type badge clipped to its
 * corner, the sentence, and how long ago.
 *
 * Unread rows get a faint primary wash. Unlike the chat list, that reads well
 * here — notifications are read in a batch and then all go quiet, so the tint
 * is a temporary state rather than the list's normal appearance.
 */
@Composable
private fun NotificationRow(
    item: NotificationItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (item.isRead) {
                    androidx.compose.ui.graphics.Color.Transparent
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(
                avatarUrl = item.fromUserPhoto,
                name = item.fromUserName,
                size = 50.dp,
            )
            TypeBadge(
                type = item.kind,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(item.fromUserName.ifBlank { "Kinetix" })
                    if (item.message.isNotBlank()) {
                        append(' ')
                        append(item.message)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.createdAt.inboxTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = CommunityIcons.Close,
            contentDescription = "Dismiss",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(7.dp),
        )
    }
}

/** The circular type marker on the corner of a notification's avatar. */
@Composable
private fun TypeBadge(type: NotificationType, modifier: Modifier = Modifier) {
    val (icon: ImageVector, tint) = when (type) {
        NotificationType.MESSAGE -> CommunityIcons.Message to MaterialTheme.colorScheme.primary
        NotificationType.FOLLOW -> CommunityIcons.Follow to MaterialTheme.colorScheme.primary
        NotificationType.LIKE -> InboxIcons.Like to MaterialTheme.colorScheme.error
        NotificationType.COMMENT -> CommunityIcons.Comment to MaterialTheme.colorScheme.primary
        NotificationType.MENTION -> InboxIcons.Mention to MaterialTheme.colorScheme.tertiary
        NotificationType.ANNOUNCEMENT -> InboxIcons.Announcement to MaterialTheme.colorScheme.tertiary
        NotificationType.SYSTEM -> InboxIcons.System to KinetixGreen
    }

    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(1.5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(11.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/**
 * The rounded search box. Hand-rolled on BasicTextField rather than a Material
 * TextField so it can be a compact pill — a full OutlinedTextField carries
 * label and helper-text space this row has no room for.
 */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CommunityIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyBlock(title: String, subtitle: String) {
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
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// New message picker
// ---------------------------------------------------------------------------

/**
 * Who to start a thread with. Lists the people the user follows and the people
 * following them — not every account in the app, which is how a community's
 * DMs turn into a spam channel.
 */
@Composable
private fun NewMessageSheet(
    viewModel: InboxViewModel,
    onDismiss: () -> Unit,
    onPick: (ChatCandidate) -> Unit,
) {
    var candidates by remember { mutableStateOf<List<ChatCandidate>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { candidates = viewModel.loadCandidates() }

    BackHandler(onBack = onDismiss)

    // Hand-rolled rather than a ModalBottomSheet, to match the post-actions
    // sheet this app already uses — same dimmed backdrop, same tap-outside and
    // back-to-close behaviour, and no experimental Material API.
    Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
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
                // Swallow taps on the sheet itself, so choosing a person doesn't
                // also register as a tap on the backdrop behind it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            Text(
                text = "New message",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search people",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            val list = candidates
            when {
                list == null -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                list.isEmpty() -> Text(
                    text = "Follow someone first — you can message people you follow, and " +
                        "anyone who follows you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )

                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    val filtered = list.filter {
                        query.isBlank() || it.displayName.contains(query.trim(), ignoreCase = true)
                    }
                    items(filtered, key = { it.uid }) { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(candidate) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(
                                avatarUrl = candidate.avatarUrl,
                                name = candidate.displayName,
                                size = 42.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = candidate.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
