package com.example.kinetixfsl.community.inbox

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.inbox.model.ChatMessage
import com.example.kinetixfsl.community.inbox.model.Conversation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The single attachment on the message being composed. */
data class PendingAttachment(
    val uri: Uri,
    /** "image" or "video" — decided by which picker the user opened. */
    val type: String,
)

data class ChatUiState(
    val conversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    /**
     * Messages sent from this device that haven't landed in Firestore yet.
     * Rendered after [messages], since they are by definition the newest.
     *
     * Mirrored from [MessageOutbox], which owns them — this is a read-only
     * view, so leaving the screen can't take an upload down with it.
     */
    val outbox: List<PendingMessage> = emptyList(),
    val draft: String = "",
    val pending: PendingAttachment? = null,
    val isLoading: Boolean = true,
    val isOtherOnline: Boolean = false,
    val errorMessage: String? = null,
    val currentUid: String = "",
) {
    /**
     * Never blocked on an in-flight send. Uploads run in the background and the
     * composer stays live throughout, so a slow video doesn't stop the next
     * three messages being typed and sent past it.
     */
    val canSend: Boolean
        get() = draft.isNotBlank() || pending != null

    val otherName: String get() = conversation?.otherName(currentUid) ?: ""
    val otherPhoto: String? get() = conversation?.otherPhoto(currentUid)
    val isOtherTyping: Boolean get() = conversation?.isOtherTyping(currentUid) == true

    /**
     * The last message we sent, if the other side has read it — this is the one
     * the "Seen" receipt sits under. Null when our newest message is still
     * unread, or when they spoke last.
     */
    val lastSeenOutgoingId: String?
        get() = messages.lastOrNull { it.senderId == currentUid }
            ?.takeIf { it.isRead }?.id
}

/**
 * One open conversation.
 *
 * Scoped to a single [conversationId] and rebuilt when the user opens a
 * different thread, so its listeners never outlive the screen showing them.
 */
class ChatViewModel(
    private val conversationId: String,
    private val recipientId: String,
    private val repository: MessagesRepository = MessagesRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(currentUid = repository.currentUid.orEmpty())
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Cancels the pending "stopped typing" write when another keystroke lands. */
    private var typingJob: Job? = null

    /** What we last told the other side, so we only write on a real change. */
    private var isTypingPublished = false

    init {
        repository.observeConversation(conversationId)
            .onEach { conversation -> _uiState.update { it.copy(conversation = conversation) } }
            .launchIn(viewModelScope)

        repository.observeMessages(conversationId)
            .onEach { list ->
                _uiState.update { it.copy(messages = list, isLoading = false) }
                // The screen is open, so anything that just arrived is read by
                // definition — this is what stops a badge appearing for a
                // message the user is looking at.
                repository.markConversationRead(conversationId)
            }
            .launchIn(viewModelScope)

        PresenceRepository.observeOnline(recipientId)
            .onEach { online -> _uiState.update { it.copy(isOtherOnline = online) } }
            .launchIn(viewModelScope)

        // Anything still uploading for this thread, including sends started
        // before this screen was opened — that's what makes a pending video
        // still be there after navigating away and back.
        MessageOutbox.observe(conversationId)
            .onEach { pending -> _uiState.update { it.copy(outbox = pending) } }
            .launchIn(viewModelScope)

        viewModelScope.launch { repository.markConversationRead(conversationId) }
    }

    /**
     * Keystrokes, and the typing indicator that rides on them.
     *
     * The "started typing" write goes out once, on the first keystroke, and the
     * "stopped" write is debounced [TYPING_IDLE_MS] behind the last one — so a
     * paragraph costs two writes rather than one per character.
     */
    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value, errorMessage = null) }

        val typing = value.isNotBlank()
        if (typing && !isTypingPublished) {
            isTypingPublished = true
            viewModelScope.launch { repository.setTyping(conversationId, true) }
        }

        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(TYPING_IDLE_MS)
            if (isTypingPublished) {
                isTypingPublished = false
                repository.setTyping(conversationId, false)
            }
        }
    }

    fun onAttachmentPicked(uri: Uri, type: String) {
        _uiState.update { it.copy(pending = PendingAttachment(uri, type), errorMessage = null) }
    }

    fun clearAttachment() {
        _uiState.update { it.copy(pending = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Puts the message in the thread immediately, then does the slow work
     * behind it.
     *
     * The composer is emptied and re-enabled before the upload even starts, so
     * a user who sends a 30-second clip can carry on typing and send three more
     * messages while it's still going up. Each send owns its own coroutine —
     * nothing here waits on anything else.
     */
    fun send(context: Context) {
        val state = _uiState.value
        if (!state.canSend) return

        val text = state.draft.trim()
        val attachment = state.pending

        // Clear the composer first. This is the whole point: the field is empty
        // and ready for the next message before a single byte has been sent.
        _uiState.update { it.copy(draft = "", pending = null, errorMessage = null) }

        // Sending ends typing regardless of the debounce above, so the other
        // side never sees "typing…" hanging under a message that already landed.
        typingJob?.cancel()
        if (isTypingPublished) {
            isTypingPublished = false
            viewModelScope.launch { repository.setTyping(conversationId, false) }
        }

        // Handed to the app-scoped outbox rather than launched here. A
        // viewModelScope coroutine dies with this screen, which is what used to
        // make an in-flight video disappear the moment the user navigated away.
        MessageOutbox.enqueue(
            context = context,
            conversationId = conversationId,
            recipientId = recipientId,
            text = text,
            uri = attachment?.uri,
            type = attachment?.type,
        )
    }

    /** Retries one failed outbox entry, from the bubble's retry tap. */
    fun retry(context: Context, localId: String) {
        MessageOutbox.retry(context, conversationId, localId)
    }

    /** Drops a failed message the user has given up on. */
    fun discard(localId: String) {
        MessageOutbox.discard(conversationId, localId)
    }

    override fun onCleared() {
        super.onCleared()
        // Leaving the screen mid-sentence must not leave "typing…" stuck on the
        // other person's header.
        //
        // Deliberately outside viewModelScope: that scope is already cancelled
        // by the time this runs, so a coroutine launched into it would never
        // execute. A detached IO scope for one short write is the price of
        // cleaning up after ourselves on the way out.
        if (!isTypingPublished) return
        isTypingPublished = false
        CoroutineScope(Dispatchers.IO).launch {
            repository.setTyping(conversationId, false)
        }
    }

    private companion object {
        /** How long after the last keystroke we call it "stopped typing". */
        const val TYPING_IDLE_MS = 2_500L
    }
}
