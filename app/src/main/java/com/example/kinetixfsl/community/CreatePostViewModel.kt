package com.example.kinetixfsl.community

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.upload.CloudinaryUploader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreatePostUiState(
    val title: String = "",
    val body: String = "",
    val linkUrl: String = "",
    val isLinkFieldVisible: Boolean = false,
    /** The local URI the user picked from the gallery (image or video). */
    val mediaUri: Uri? = null,
    /** "image" or "video" — set when the user picks a file. */
    val mediaType: String? = null,
    val isUploading: Boolean = false,
    val errorMessage: String? = null,
    /** True once the post has been written to Firestore — caller navigates away. */
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
     * Submits the post: validates → uploads media to Cloudinary (if any) →
     * writes the Firestore document. The feed updates automatically because
     * it uses a snapshot listener.
     */
    fun submitPost(context: Context) {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please add a title.") }
            return
        }

        _uiState.update { it.copy(isUploading = true, errorMessage = null) }

        viewModelScope.launch {
            // Step 1: upload media to Cloudinary if the user attached something.
            var imageUrl: String? = null
            var videoUrl: String? = null

            if (state.mediaUri != null && state.mediaType != null) {
                when (
                    val uploadResult = CloudinaryUploader.upload(
                        context = context,
                        uri = state.mediaUri,
                        resourceType = state.mediaType,
                    )
                ) {
                    is CloudinaryUploader.UploadResult.Success -> {
                        if (state.mediaType == "image") {
                            imageUrl = uploadResult.secureUrl
                        } else {
                            videoUrl = uploadResult.secureUrl
                        }
                    }
                    is CloudinaryUploader.UploadResult.Error -> {
                        _uiState.update {
                            it.copy(isUploading = false, errorMessage = uploadResult.message)
                        }
                        return@launch
                    }
                }
            }

            // Step 2: write the post to Firestore.
            val result = repository.createPost(
                title = state.title,
                body = state.body,
                linkUrl = state.linkUrl.takeIf { it.isNotBlank() },
                imageUrl = imageUrl,
                videoUrl = videoUrl,
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isUploading = false, isPostCreated = true) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            errorMessage = error.localizedMessage ?: "Couldn't create post.",
                        )
                    }
                },
            )
        }
    }

    fun onPostCreatedHandled() {
        _uiState.update { it.copy(isPostCreated = false) }
    }
}
