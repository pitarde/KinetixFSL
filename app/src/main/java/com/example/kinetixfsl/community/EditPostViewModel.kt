package com.example.kinetixfsl.community

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.MAX_POST_MEDIA
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.community.model.PostMedia
import com.example.kinetixfsl.community.upload.R2MediaUploader
import com.example.kinetixfsl.community.upload.SharePreviewGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditPostUiState(
    val postId: String = "",
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val isLinkFieldVisible: Boolean = false,
    /** Attachments the post already has, minus anything the user removed. */
    val existingMedia: List<PostMedia> = emptyList(),
    /** Newly picked attachments, not yet uploaded. */
    val newMedia: List<PickedMedia> = emptyList(),
    val isSaving: Boolean = false,
    val savingLabel: String = "",
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
) {
    val totalMedia: Int get() = existingMedia.size + newMedia.size
    val canAddMore: Boolean get() = totalMedia < MAX_POST_MEDIA
    val canSave: Boolean get() = title.isNotBlank() && !isSaving
}

/**
 * Backs the edit screen. Separate from [CreatePostViewModel] on purpose: the
 * two flows only look alike. Creating hands off to a background upload service
 * and closes immediately; editing has to reconcile media that's already
 * uploaded with media that isn't, then write in place.
 */
class EditPostViewModel(
    post: Post,
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditPostUiState(
            postId = post.id,
            title = post.title,
            body = post.body,
            linkUrl = post.linkUrl.orEmpty(),
            isLinkFieldVisible = !post.linkUrl.isNullOrBlank(),
            existingMedia = post.mediaItems,
        )
    )
    val uiState: StateFlow<EditPostUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) =
        _uiState.update { it.copy(title = value, errorMessage = null) }

    fun onBodyChange(value: String) =
        _uiState.update { it.copy(body = value, errorMessage = null) }

    fun onLinkUrlChange(value: String) =
        _uiState.update { it.copy(linkUrl = value, errorMessage = null) }

    fun toggleLinkField() =
        _uiState.update { it.copy(isLinkFieldVisible = !it.isLinkFieldVisible) }

    /** Drops one of the post's current attachments. */
    fun removeExisting(item: PostMedia) =
        _uiState.update { it.copy(existingMedia = it.existingMedia - item, errorMessage = null) }

    /** Drops one of the newly picked attachments. */
    fun removeNew(item: PickedMedia) =
        _uiState.update { it.copy(newMedia = it.newMedia - item, errorMessage = null) }

    fun onMediaPicked(picked: List<PickedMedia>) {
        if (picked.isEmpty()) return
        _uiState.update { state ->
            val existingUris = state.newMedia.map { it.uri }.toSet()
            val room = MAX_POST_MEDIA - state.totalMedia
            val additions = picked
                .filter { it.uri !in existingUris }
                .take(room.coerceAtLeast(0))

            state.copy(
                newMedia = state.newMedia + additions,
                errorMessage = if (additions.size < picked.size) {
                    "You can attach up to $MAX_POST_MEDIA items."
                } else {
                    null
                },
            )
        }
    }

    /**
     * Uploads any newly added attachments, then writes the whole post back.
     *
     * Unlike creating, this runs in the ViewModel rather than the upload
     * service — the user is waiting on the result to close the screen, and
     * there's no partial state worth surviving the screen being dismissed.
     */
    fun save(context: Context) {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please add a title.") }
            return
        }
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null, savingLabel = "Saving…") }

        viewModelScope.launch {
            val uploaded = mutableListOf<PostMedia>()

            state.newMedia.forEachIndexed { index, item ->
                _uiState.update {
                    it.copy(savingLabel = "Uploading ${index + 1} of ${state.newMedia.size}…")
                }

                val result = R2MediaUploader.upload(context, item.uri, item.type)
                when (result) {
                    is R2MediaUploader.UploadResult.Success -> {
                        val derived = try {
                            SharePreviewGenerator.derive(
                                context = context,
                                uri = item.uri,
                                mediaType = item.type,
                                includeShareArtifacts = false,
                            )
                        } catch (_: Exception) {
                            SharePreviewGenerator.Derivatives()
                        }
                        uploaded += PostMedia(
                            url = result.secureUrl,
                            type = item.type,
                            thumbUrl = derived.feedUrl,
                        )
                    }
                    is R2MediaUploader.UploadResult.Error -> {
                        _uiState.update {
                            it.copy(isSaving = false, errorMessage = result.message)
                        }
                        return@launch
                    }
                }
            }

            _uiState.update { it.copy(savingLabel = "Saving…") }

            repository.updatePost(
                postId = state.postId,
                title = state.title,
                body = state.body,
                linkUrl = state.linkUrl.takeIf { it.isNotBlank() },
                media = state.existingMedia + uploaded,
            ).fold(
                onSuccess = { _uiState.update { it.copy(isSaving = false, isSaved = true) } },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.localizedMessage ?: "Couldn't save changes.",
                        )
                    }
                },
            )
        }
    }
}
