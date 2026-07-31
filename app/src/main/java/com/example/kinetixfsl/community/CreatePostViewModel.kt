package com.example.kinetixfsl.community

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.MAX_POST_MEDIA
import com.example.kinetixfsl.community.upload.PostUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One photo or video the user picked, before it's uploaded. */
data class PickedMedia(
    val uri: Uri,
    /** "image" or "video". */
    val type: String,
)

data class CreatePostUiState(
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val isLinkFieldVisible: Boolean = false,
    /**
     * Everything the user attached, in pick order. Images and videos can be
     * mixed freely, capped at [MAX_POST_MEDIA].
     */
    val media: List<PickedMedia> = emptyList(),
    val errorMessage: String? = null,
    /** True once the post has been handed off to the upload service. */
    val isPostCreated: Boolean = false,
) {
    val canAddMore: Boolean get() = media.size < MAX_POST_MEDIA
}

class CreatePostViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreatePostUiState())
    val uiState: StateFlow<CreatePostUiState> = _uiState.asStateFlow()

    fun onTitleChange(value: String) =
        _uiState.update { it.copy(title = value, errorMessage = null) }

    fun onBodyChange(value: String) =
        _uiState.update { it.copy(body = value, errorMessage = null) }

    fun onLinkUrlChange(value: String) =
        _uiState.update { it.copy(linkUrl = value, errorMessage = null) }

    fun toggleLinkField() =
        _uiState.update { it.copy(isLinkFieldVisible = !it.isLinkFieldVisible) }

    /**
     * Appends everything the user just picked, skipping duplicates and
     * stopping at [MAX_POST_MEDIA]. Picking again adds to the selection
     * rather than replacing it, so images and videos can be mixed by going
     * back to the picker.
     */
    fun onMediaPicked(picked: List<PickedMedia>) {
        if (picked.isEmpty()) return

        _uiState.update { state ->
            val existing = state.media.map { it.uri }.toSet()
            val room = MAX_POST_MEDIA - state.media.size
            val additions = picked
                .filter { it.uri !in existing }
                .take(room.coerceAtLeast(0))

            val dropped = picked.size - additions.size
            state.copy(
                media = state.media + additions,
                errorMessage = if (dropped > 0 && room <= picked.size) {
                    "You can attach up to $MAX_POST_MEDIA items."
                } else {
                    null
                },
            )
        }
    }

    /** Removes one attached item (user tapped the "x" on its thumbnail). */
    fun removeMedia(item: PickedMedia) {
        _uiState.update { it.copy(media = it.media - item, errorMessage = null) }
    }

    /**
     * Hands the post off to [PostUploadService] which runs in the background.
     * The screen closes instantly and the user sees a notification in the
     * status bar with the upload progress.
     */
    fun submitPost(context: Context) {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please add a title.") }
            return
        }

        // Take persistent read permission on each URI so the background
        // service can still read them after the activity closes.
        state.media.forEach { item ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    item.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Not all URIs support persistable permission — that's OK,
                // the service will still try to read it normally.
            }
        }

        // Pack everything into an Intent and start the foreground service.
        val intent = Intent(context, PostUploadService::class.java).apply {
            putExtra(PostUploadService.EXTRA_TITLE, state.title)
            putExtra(PostUploadService.EXTRA_BODY, state.body)
            putExtra(PostUploadService.EXTRA_LINK_URL,
                state.linkUrl.takeIf { it.isNotBlank() })
            if (state.media.isNotEmpty()) {
                putStringArrayListExtra(
                    PostUploadService.EXTRA_MEDIA_URIS,
                    ArrayList(state.media.map { it.uri.toString() }),
                )
                putStringArrayListExtra(
                    PostUploadService.EXTRA_MEDIA_TYPES,
                    ArrayList(state.media.map { it.type }),
                )
                // Grant the service read access to the content URIs.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startForegroundService(intent)

        // Signal the screen to close immediately — upload continues in background.
        _uiState.update { it.copy(isPostCreated = true) }
    }

    fun onPostCreatedHandled() {
        // Reset the entire form so it's clean when the user returns.
        _uiState.value = CreatePostUiState()
    }
}