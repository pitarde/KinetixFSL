package com.example.kinetixfsl.community.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.inbox.model.Conversation
import com.example.kinetixfsl.community.inbox.model.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two halves of the Inbox screen. */
enum class InboxTab(val label: String) {
    CHAT("Chat"),
    NOTIFICATION("Notification"),
}

data class InboxUiState(
    val selectedTab: InboxTab = InboxTab.CHAT,
    val conversations: List<Conversation> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val isLoadingChats: Boolean = true,
    val isLoadingNotifications: Boolean = true,
    /** Free-text filter over the conversation list. Applied client-side. */
    val searchQuery: String = "",
    val currentUid: String = "",
) {
    /** Threads with at least one message we haven't opened. */
    val unreadChats: Int
        get() = conversations.count { it.unreadFor(currentUid) > 0 }

    val unreadNotifications: Int
        get() = notifications.count { !it.isRead }

    /**
     * What the bell in the bottom nav shows. One number for both halves — the
     * user just wants to know something is waiting, and finds out which kind by
     * opening the screen.
     */
    val totalUnread: Int get() = unreadChats + unreadNotifications

    /**
     * The conversation list after the search box. Matching on the other
     * person's name only: previews are the last line of a private message, and
     * surfacing them through a search box is not what a user expects a name
     * filter to do.
     */
    val visibleConversations: List<Conversation>
        get() {
            val query = searchQuery.trim()
            if (query.isBlank()) return conversations
            return conversations.filter {
                it.otherName(currentUid).contains(query, ignoreCase = true)
            }
        }
}

/**
 * Backs the Inbox screen: both tabs' data, and which one is showing.
 *
 * Both listeners run for the whole life of the screen, not just while their tab
 * is visible. That's what keeps the unread badge on the *other* tab honest — a
 * message arriving while the user reads notifications has to show up on the
 * Chat tab's badge immediately, which it can't do if that listener is torn down
 * whenever the tab loses focus. Two live listeners is a cheap price for that.
 */
class InboxViewModel(
    private val messages: MessagesRepository = MessagesRepository(),
    private val notifications: NotificationRepository = NotificationRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(InboxUiState(currentUid = messages.currentUid.orEmpty()))
    val uiState: StateFlow<InboxUiState> = _uiState.asStateFlow()

    init {
        messages.observeConversations()
            .onEach { list ->
                _uiState.update { it.copy(conversations = list, isLoadingChats = false) }
            }
            .launchIn(viewModelScope)

        notifications.observeNotifications()
            .onEach { list ->
                _uiState.update { it.copy(notifications = list, isLoadingNotifications = false) }
                // Opening the app straight onto the Notification tab has to
                // clear the badge too, not only a later tab switch.
                if (_uiState.value.selectedTab == InboxTab.NOTIFICATION) markNotificationsRead()
            }
            .launchIn(viewModelScope)
    }

    fun selectTab(tab: InboxTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        if (tab == InboxTab.NOTIFICATION) markNotificationsRead()
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    /**
     * Marks everything currently on screen as read.
     *
     * The local list is flipped straight away rather than waiting for the
     * Firestore listener to echo the write back. Two reasons: the badge should
     * disappear the instant the user looks at the list, not a round trip later;
     * and offline, the write may not land for minutes while the user is quite
     * reasonably expecting the badge to go.
     */
    private fun markNotificationsRead() {
        val unread = _uiState.value.notifications.filter { !it.isRead }.map { it.id }
        if (unread.isEmpty()) return

        _uiState.update { state ->
            state.copy(notifications = state.notifications.map { it.copy(isRead = true) })
        }
        viewModelScope.launch { notifications.markRead(unread) }
    }

    /**
     * Marks one row read — what tapping a notification does.
     *
     * Needed on top of [markNotificationsRead] for the rows that go nowhere
     * when tapped, chiefly the account notices: a welcome message the user
     * clearly *has* read should stop counting against the badge, and without
     * this it only cleared as a side effect of the whole-tab sweep.
     */
    fun markNotificationRead(id: String) {
        val item = _uiState.value.notifications.firstOrNull { it.id == id } ?: return
        if (item.isRead) return

        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.id == id) it.copy(isRead = true) else it
                },
            )
        }
        viewModelScope.launch { notifications.markRead(listOf(id)) }
    }

    fun deleteNotification(id: String) {
        viewModelScope.launch { notifications.delete(id) }
    }

    fun clearNotifications() {
        viewModelScope.launch { notifications.clearAll() }
    }

    /**
     * Opens (or creates) the thread with [uid] and hands its id back through
     * [onReady] — the Message button on a profile and the new-message picker
     * both come through here.
     */
    fun openConversationWith(
        uid: String,
        name: String,
        photo: String?,
        onReady: (String) -> Unit,
    ) {
        viewModelScope.launch {
            messages.openConversationWith(uid, name, photo)
                .onSuccess(onReady)
        }
    }

    /**
     * The Message button on somebody's profile, which knows their uid and
     * nothing else. Resolves their name and photo on the way in so the thread
     * document is complete from the first message.
     */
    fun openConversationWithUid(uid: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            messages.openConversationWithUid(uid).onSuccess(onReady)
        }
    }

    /** People the user may start a new thread with — everyone either side follows. */
    suspend fun loadCandidates(): List<ChatCandidate> = messages.messageableUsers()
}
