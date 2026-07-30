package com.example.kinetixfsl.community

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Comment
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
    val commentText: String = "",
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

    init {
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

    fun onCommentTextChange(value: String) {
        _uiState.update { it.copy(commentText = value, errorMessage = null) }
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

            repository.addComment(postId, text, imageUrl, parentId).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            commentText = "",
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
}
