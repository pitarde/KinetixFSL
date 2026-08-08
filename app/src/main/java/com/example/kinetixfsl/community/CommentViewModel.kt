package com.example.kinetixfsl.community

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Comment
import com.example.kinetixfsl.community.model.FollowUser
import com.example.kinetixfsl.community.upload.R2MediaUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A top-level comment plus the replies hanging off it. Two levels only —
 * replies never have replies of their own.
 */
data class CommentThread(
    val comment: Comment,
    val replies: List<Comment> = emptyList(),
)

data class CommentUiState(
    val threads: List<CommentThread> = emptyList(),
    /** Comments plus replies — what the "Comments (n)" heading shows. */
    val totalCount: Int = 0,
    val isLoading: Boolean = true,
    /**
     * The composer's contents *and* its cursor.
     *
     * A plain String isn't enough once there's an @mention autocomplete: the
     * suggestions depend on which `@token` the caret is currently sitting in,
     * which the text alone can't tell you.
     */
    val commentField: TextFieldValue = TextFieldValue(""),
    /**
     * Everyone the author has picked from the autocomplete so far. Pruned on
     * send to whoever is still named in the final text, so deleting a mention
     * before posting also stops the notification.
     */
    val mentionedUsers: List<FollowUser> = emptyList(),
    /** The `@…` fragment under the caret, or null when not mentioning. */
    val mentionQuery: String? = null,
    /** Who to offer for [mentionQuery]. Empty hides the strip. */
    val mentionSuggestions: List<FollowUser> = emptyList(),
    /**
     * The single local image the user attached to the comment they're writing.
     * One image per comment — picking another replaces this one.
     */
    val pendingImageUri: Uri? = null,
    /** Set when the composer is writing a reply rather than a new comment. */
    val replyingTo: Comment? = null,
    /** Threads whose replies are currently revealed. */
    val expandedThreadIds: Set<String> = emptySet(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
) {
    /** What the composer currently reads, without its cursor. */
    val commentText: String get() = commentField.text

    /** A comment needs text or an image (or both) before it can be posted. */
    val canSend: Boolean
        get() = !isSending && (commentText.isNotBlank() || pendingImageUri != null)
}

class CommentViewModel(
    private val postId: String,
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

    /**
     * Everyone this user may mention, fetched once when the screen opens.
     *
     * Held outside the UI state because it never renders directly — only the
     * filtered subset does — and re-reading the follow graph on every keystroke
     * would be a query per character typed.
     */
    private var mentionCandidates: List<FollowUser> = emptyList()

    init {
        viewModelScope.launch {
            mentionCandidates = repository.mentionCandidates()
        }

        repository.commentsForPost(postId)
            .onEach { comments ->
                _uiState.update {
                    it.copy(
                        threads = buildThreads(comments),
                        totalCount = comments.size,
                        isLoading = false,
                    )
                }
            }
            .catch { t ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.localizedMessage ?: "Couldn't load comments.",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Splits the flat stream into threads. The repository hands them over
     * newest-first, which is the order we want for top-level comments; replies
     * get flipped to oldest-first so a back-and-forth reads top to bottom.
     */
    private fun buildThreads(all: List<Comment>): List<CommentThread> {
        val repliesByParent = all
            .filter { !it.parentId.isNullOrBlank() }
            .groupBy { it.parentId!! }

        return all
            .filter { it.parentId.isNullOrBlank() }
            .map { parent ->
                CommentThread(
                    comment = parent,
                    replies = repliesByParent[parent.id]
                        .orEmpty()
                        .sortedBy { it.createdAt },
                )
            }
    }

    /**
     * Every keystroke, plus the @mention state that rides on the caret's
     * position.
     */
    fun onCommentTextChange(value: TextFieldValue) {
        _uiState.update { state ->
            val query = activeMentionQuery(value)
            val suggestions = suggestionsFor(query)
            state.copy(
                commentField = value,
                errorMessage = null,
                // The strip hides itself when nothing matches, which is what
                // lets a query contain spaces without getting stuck open: keep
                // typing past a name and the matches run out, so "@Juan is
                // right" stops offering suggestions after "Juan".
                mentionQuery = if (suggestions.isEmpty()) null else query,
                mentionSuggestions = suggestions,
            )
        }
    }

    /**
     * The `@…` fragment the caret is inside, or null.
     *
     * Walks back from the cursor to the nearest `@` that starts a word, and
     * gives up at a newline or after [MAX_MENTION_QUERY] characters — so an
     * email address mid-sentence or a stray `@` far above doesn't put the
     * composer into mention mode.
     */
    private fun activeMentionQuery(value: TextFieldValue): String? {
        val cursor = value.selection.start
        if (cursor <= 0 || cursor > value.text.length) return null

        val at = value.text.lastIndexOf('@', startIndex = cursor - 1)
        if (at < 0) return null
        // Must begin a word: "user@example.com" is an address, not a mention.
        if (at > 0 && !value.text[at - 1].isWhitespace()) return null

        val fragment = value.text.substring(at + 1, cursor)
        if (fragment.length > MAX_MENTION_QUERY || fragment.contains('\n')) return null
        return fragment
    }

    private fun suggestionsFor(query: String?): List<FollowUser> {
        if (query == null) return emptyList()
        val trimmed = query.trim()
        // A bare "@" offers everyone — which is the discovery affordance: the
        // user types one character and sees who they can mention.
        if (trimmed.isEmpty()) return mentionCandidates.take(MAX_SUGGESTIONS)
        return mentionCandidates
            .filter { it.displayName.contains(trimmed, ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
    }

    /**
     * Swaps the `@fragment` under the caret for [user]'s full name and
     * remembers who was meant.
     *
     * Remembering is the part that matters: the name goes into the text purely
     * so the comment reads naturally, while the notification is addressed from
     * the recorded uid — which is why a name with spaces in it works at all.
     */
    fun applyMention(user: FollowUser) {
        _uiState.update { state ->
            val field = state.commentField
            val cursor = field.selection.start
            if (cursor <= 0 || cursor > field.text.length) return@update state

            val at = field.text.lastIndexOf('@', startIndex = cursor - 1)
            if (at < 0) return@update state

            val inserted = "@${user.displayName} "
            val newText = field.text.substring(0, at) + inserted + field.text.substring(cursor)
            val newCursor = at + inserted.length

            state.copy(
                commentField = TextFieldValue(newText, TextRange(newCursor)),
                mentionedUsers = (state.mentionedUsers + user).distinctBy { it.uid },
                mentionQuery = null,
                mentionSuggestions = emptyList(),
            )
        }
    }

    /** Attaches an image to the comment being written, replacing any previous one. */
    fun onImagePicked(uri: Uri) {
        _uiState.update { it.copy(pendingImageUri = uri, errorMessage = null) }
    }

    /** Removes the attached image (user tapped the x on the preview). */
    fun clearImage() {
        _uiState.update { it.copy(pendingImageUri = null) }
    }

    /** Points the composer at [comment] so the next send becomes a reply. */
    fun startReply(comment: Comment) {
        _uiState.update { it.copy(replyingTo = comment, errorMessage = null) }
    }

    /** Back to writing a plain top-level comment. */
    fun cancelReply() {
        _uiState.update { it.copy(replyingTo = null) }
    }

    /** Show or hide a thread's replies. */
    fun toggleReplies(threadId: String) {
        _uiState.update { state ->
            val expanded = if (threadId in state.expandedThreadIds) {
                state.expandedThreadIds - threadId
            } else {
                state.expandedThreadIds + threadId
            }
            state.copy(expandedThreadIds = expanded)
        }
    }

    /**
     * Uploads the attached image (if any) and writes the comment or reply.
     * [onSent] fires only on success so the composer can close itself.
     */
    fun sendComment(context: Context, onSent: () -> Unit = {}) {
        val state = _uiState.value
        val text = state.commentText.trim()
        if (text.isBlank() && state.pendingImageUri == null) return
        if (state.isSending) return

        // Replying to a reply attaches to that reply's parent, which is what
        // keeps threads exactly two levels deep.
        val parentId = state.replyingTo?.let { target ->
            target.parentId?.takeIf { it.isNotBlank() } ?: target.id
        }

        _uiState.update { it.copy(isSending = true, errorMessage = null) }

        viewModelScope.launch {
            var imageUrl: String? = null

            val uri = state.pendingImageUri
            if (uri != null) {
                when (val result = R2MediaUploader.upload(context, uri, "image")) {
                    is R2MediaUploader.UploadResult.Success -> imageUrl = result.secureUrl
                    is R2MediaUploader.UploadResult.Error -> {
                        _uiState.update {
                            it.copy(isSending = false, errorMessage = result.message)
                        }
                        return@launch
                    }
                }
            }

            // Only the people still named in the final text. Picking someone
            // and then deleting the name should cancel the mention, not send a
            // notification about a comment that doesn't mention them.
            val mentioned = state.mentionedUsers
                .filter { text.contains("@${it.displayName}", ignoreCase = true) }
                .map { it.uid }

            repository.addComment(postId, text, imageUrl, parentId, mentioned).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            commentField = TextFieldValue(""),
                            mentionedUsers = emptyList(),
                            mentionQuery = null,
                            mentionSuggestions = emptyList(),
                            pendingImageUri = null,
                            replyingTo = null,
                            // Open the thread so the user sees the reply land.
                            expandedThreadIds = if (parentId != null) {
                                it.expandedThreadIds + parentId
                            } else {
                                it.expandedThreadIds
                            },
                        )
                    }
                    onSent()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            errorMessage = error.localizedMessage ?: "Couldn't send comment.",
                        )
                    }
                },
            )
        }
    }

    private companion object {
        /** How far past an `@` we keep treating the text as a mention query. */
        const val MAX_MENTION_QUERY = 30

        /** Rows in the suggestion strip. More than this and it's a directory. */
        const val MAX_SUGGESTIONS = 8
    }
}
