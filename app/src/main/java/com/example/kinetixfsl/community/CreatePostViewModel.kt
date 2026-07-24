package com.example.kinetixfsl.community

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.kinetixfsl.community.upload.PostUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CreatePostUiState(
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val isLinkFieldVisible: Boolean = false,
    /** The local URI the user picked from the gallery (image or video). */
    val mediaUri: Uri? = null,
    /** "image" or "video" — set when the user picks a file. */
    val mediaType: String? = null,
    val errorMessage: String? = null,
    /** True once the post has been handed off to the upload service. */
    val isPostCreated: Boolean = false,
)

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

    /** Called when the user picks an image from the gallery. */
    fun onImagePicked(uri: Uri) {
        _uiState.update {
            it.copy(mediaUri = uri, mediaType = "image", errorMessage = null)
        }
    }

    /** Called when the user picks a video from the gallery. */
    fun onVideoPicked(uri: Uri) {
        _uiState.update {
            it.copy(mediaUri = uri, mediaType = "video", errorMessage = null)
        }
    }

    /** Clears the selected media (user tapped the "x" on the preview). */
    fun clearMedia() {
        _uiState.update { it.copy(mediaUri = null, mediaType = null) }
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

        // Take a persistent read permission on the media URI so the
        // background service can still read it after the activity closes.
        if (state.mediaUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    state.mediaUri,
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
            if (state.mediaUri != null) {
                putExtra(PostUploadService.EXTRA_MEDIA_URI, state.mediaUri.toString())
                putExtra(PostUploadService.EXTRA_MEDIA_TYPE, state.mediaType)
                // Grant the service read access to the content URI.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startForegroundService(intent)

        // Signal the screen to close immediately — upload continues in background.
        _uiState.update { it.copy(isPostCreated = true) }
    }

    fun onPostCreatedHandled() {
        _uiState.update { it.copy(isPostCreated = false) }
    }
}