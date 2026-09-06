package com.example.kinetixfsl.community.inbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
    /**
     * Opens the drawer this screen is showing under. Null hides the hamburger
     * — not needed everywhere this screen appears, only where it's the
     * top-level content of a drawer (as opposed to, say, sitting inside
     * another screen's own overlay stack with no drawer of its own to open).
     */
    onMenuClick: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isPickerOpen by remember { mutableStateOf(false) }

    // This screen's own top bar is always dark now (KinetixNavy in light
    // mode, colorScheme.surface in dark) — see InboxTopBar — so it always
    // wants light (white) status bar icons, same as the Home Feed's own bar.
    com.example.kinetixfsl.ui.theme.StatusBarLightIcons(light = true)

    /** The thread a long-press is asking to clear, or null. */
    var pendingClear by remember { mutableStateOf<Conversation?>(null) }

    // Chat <-> Notification, kept in step with the tab row in both
    // directions: tapping a tab animates the pager to it, and swiping the
    // pager (a fling anywhere in the page, not just the tab row) updates
    // which tab reads as selected once the swipe settles.
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = state.selectedTab.ordinal,
    ) { InboxTab.entries.size }

    LaunchedEffect(state.selectedTab) {
        val target = state.selectedTab.ordinal
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = InboxTab.entries[page]
            if (tab != state.selectedTab) viewModel.selectTab(tab)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        InboxTopBar(onMenuClick = onMenuClick)
        InboxTabRow(
            selected = state.selectedTab,
            unreadChats = state.unreadChats,
            unreadNotifications = state.unreadNotifications,
            onSelect = { tab ->
                viewModel.selectTab(tab)
            },
        )

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (InboxTab.entries[page]) {
                InboxTab.CHAT -> ChatListContent(
                    state = state,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onOpenConversation = onOpenConversation,
                    onNewMessage = { isPickerOpen = true },
                    onLongPressConversation = { pendingClear = it },
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
    }

    pendingClear?.let { conversation ->
        ConfirmDialog(
            title = "Delete all chat history?",
            body = "This clears every message with ${conversation.otherName(state.currentUid)} " +
                "for both of you. It can't be undone.",
            onConfirm = {
                viewModel.deleteConversationHistory(conversation.id)
                pendingClear = null
            },
            onDismiss = { pendingClear = null },
        )
    }

    if (isPickerOpen) {
        // Same slide-up-and-swipe-down-to-close sheet as Create/Edit post —
        // see SlideUpScreen.
        com.example.kinetixfsl.community.SlideUpScreen(onClose = { isPickerOpen = false }) { dismiss ->
            NewMessageSheet(
                viewModel = viewModel,
                onDismiss = dismiss,
                onPick = { candidate ->
                    dismiss()
                    viewModel.openConversationWith(
                        uid = candidate.uid,
                        name = candidate.displayName,
                        photo = candidate.avatarUrl,
                    ) { conversationId -> onOpenConversation(conversationId, candidate.uid) }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

/**
 * A slim top bar matching the Home Feed's own: KinetixNavy in light mode, the
 * normal theme surface in dark mode, 56dp tall. The hamburger, when there's a
 * drawer to open, sits at the start rather than replacing the title, so the
 * screen still reads as "Inbox" the same way wherever it's shown.
 */
@Composable
private fun InboxTopBar(onMenuClick: (() -> Unit)?) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val barBackground = if (dark) MaterialTheme.colorScheme.surface else com.example.kinetixfsl.ui.theme.KinetixNavy
    val barContentColor = if (dark) MaterialTheme.colorScheme.onSurface else com.example.kinetixfsl.ui.theme.KinetixWhite

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBackground)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp),
        ) {
            if (onMenuClick != null) {
                Icon(
                    imageVector = CommunityIcons.Menu,
                    contentDescription = "Open menu",
                    tint = barContentColor,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(28.dp)
                        .clickable(onClick = onMenuClick),
                )
            }
            Text(
                text = "Inbox",
                style = MaterialTheme.typography.titleLarge,
                color = barContentColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }
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
    onLongPressConversation: (Conversation) -> Unit,
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
            state.isLoadingChats ->
                com.example.kinetixfsl.ui.components.InboxListSkeleton()

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
                        onLongClick = { onLongPressConversation(conversation) },
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    me: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
    // The notification a tap opened for full reading, or null.
    var detail by remember { mutableStateOf<NotificationItem?>(null) }
    // The notification a delete is asking to confirm, or null.
    var pendingDelete by remember { mutableStateOf<NotificationItem?>(null) }
    // "Clear all" also confirms — it wipes the whole list for good.
    var confirmClearAll by remember { mutableStateOf(false) }

    /** Follows a notification to whatever it points at, if anywhere. */
    fun openTarget(item: NotificationItem) {
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
    }

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
                        .clickable { confirmClearAll = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        when {
            state.isLoadingNotifications ->
                com.example.kinetixfsl.ui.components.InboxListSkeleton()

            state.notifications.isEmpty() -> EmptyBlock(
                title = "You're all caught up",
                subtitle = "Replies, follows, mentions and community posts land here.",
            )

            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.notifications, key = { it.id }) { item ->
                    NotificationRow(
                        item = item,
                        onClick = {
                            // A tap marks the row read and opens the full-text
                            // popup — where the user can read it all and choose
                            // to open it or delete it. Marking read whether or
                            // not it's navigable is the only way an account
                            // notice ever clears the badge.
                            onMarkRead(item.id)
                            detail = item
                        },
                        // The row's X asks to delete — routed through the same
                        // confirm as the popup's Delete, so nothing goes for
                        // good on a single stray tap.
                        onDismiss = { pendingDelete = item },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 78.dp),
                    )
                }
            }
        }
    }

    // Full-text popup for the tapped notification.
    detail?.let { item ->
        NotificationDetailDialog(
            item = item,
            canOpen = item.isNavigable,
            onOpen = {
                val target = item
                detail = null
                openTarget(target)
            },
            onDelete = {
                pendingDelete = item
                detail = null
            },
            onDismiss = { detail = null },
        )
    }

    // "Do you want to delete this notification permanently?" — one row.
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete notification?",
            body = "Do you want to delete this notification permanently?",
            confirmLabel = "Delete",
            onConfirm = {
                onDelete(item.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // "Clear all" — the whole list, for good.
    if (confirmClearAll) {
        ConfirmDialog(
            title = "Clear all notifications?",
            body = "This permanently deletes every notification in your inbox. " +
                "It can't be undone.",
            confirmLabel = "Clear all",
            onConfirm = {
                onClearAll()
                confirmClearAll = false
            },
            onDismiss = { confirmClearAll = false },
        )
    }
}

/**
 * The full-text popup a tapped notification opens — so a long message that the
 * row truncates to three lines can be read in full — with a Delete action and,
 * when the notification points somewhere, an Open action.
 */
@Composable
private fun NotificationDetailDialog(
    item: NotificationItem,
    canOpen: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = item.fromUserName.ifBlank { "Kinetix" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = item.message.ifBlank { "(no details)" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = item.createdAt.inboxTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDelete) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            Row {
                if (canOpen) {
                    androidx.compose.material3.TextButton(onClick = onOpen) {
                        Text(
                            text = "Open",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
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

    // The sheet chrome itself (scrim, rounded top corners, drag handle,
    // slide-up-and-swipe-down-to-dismiss) is SlideUpScreen's ModalBottomSheet
    // — this is just its content.
    Column(
        Modifier
            .fillMaxWidth()
            // Same fix as the Edit Profile sheet: without imePadding the
            // keyboard just covered the search field, so typing a name
            // showed nothing. The scroll is what lets the pushed-up sheet
            // still reach that field instead of clipping it.
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
