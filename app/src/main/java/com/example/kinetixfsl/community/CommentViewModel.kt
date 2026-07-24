package com.example.kinetixfsl.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Comment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = true,
    val commentText: String = "",
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

class CommentViewModel(
    private val postId: String,
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

    init {
        repository.commentsForPost(postId)
            .onEach { comments ->
                _uiState.update { it.copy(comments = comments, isLoading = false) }
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

    fun onCommentTextChange(value: String) {
        _uiState.update { it.copy(commentText = value, errorMessage = null) }
    }

    fun sendComment() {
        val text = _uiState.value.commentText.trim()
        if (text.isBlank()) return

        _uiState.update { it.copy(isSending = true, errorMessage = null) }

        viewModelScope.launch {
            repository.addComment(postId, text).fold(
                onSuccess = {
                    _uiState.update { it.copy(isSending = false, commentText = "") }
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
