package com.example.kinetixfsl.community

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Community
import com.example.kinetixfsl.community.model.MAX_POST_MEDIA
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.community.model.PostMedia
import com.example.kinetixfsl.community.upload.PostUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class EditPostUiState(
    val postId: String = "",
    val title: String = "",
    val body: String = "",
    /** One entry per link field; blank ones are dropped on save. */
    val links: List<String> = emptyList(),
    /** Attachments the post already has, minus anything the user removed. */
    val existingMedia: List<PostMedia> = emptyList(),
    /** Newly picked attachments, not yet uploaded. */
    val newMedia: List<PickedMedia> = emptyList(),
    val isSaving: Boolean = false,
    val savingLabel: String = "",
    val errorMessage: String? = null,
    val isSaved: Boolean = false,
    /** Where the post lives. Blank id means the Home Feed. */
    val selectedCommunityId: String = "",
    val selectedCommunityName: String = "",
) {
    val totalMedia: Int get() = existingMedia.size + newMedia.size
    val canAddMore: Boolean get() = totalMedia < MAX_POST_MEDIA
    val canSave: Boolean get() = title.isNotBlank() && !isSaving

    /** Label for the community pill: the name, or the Home Feed default. */
    val communityLabel: String
        get() = selectedCommunityName.ifBlank { "Home Feed" }
}

/**
 * Backs the edit screen. Separate from [CreatePostViewModel] on purpose: the
 * two flows only look alike. Creating hands off to a background upload service
 * and closes immediately; editing has to reconcile media that's already
 * uploaded with media that isn't, then write in place.
 */
class EditPostViewModel(
    post: Post,
    private val directory: CommunityDirectoryRepository = CommunityDirectoryRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EditPostUiState(
            postId = post.id,
            title = post.title,
            body = post.body,
            links = post.allLinks,
            existingMedia = post.mediaItems,
            selectedCommunityId = post.communityId,
            selectedCommunityName = post.communityName,
        )
    )
    val uiState: StateFlow<EditPostUiState> = _uiState.asStateFlow()

    /**
     * Communities the user has joined — the options in the community picker.
     * The post's own community is included even if it isn't in this list yet
     * (e.g. the picker opens before the join listener has caught up), so
     * switching away and back never loses the current selection.
     */
    val joinedCommunities: StateFlow<List<Community>> =
        directory.observeJoinedCommunities()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Picks the target community. A blank [id] means the Home Feed. */
    fun selectCommunity(id: String, name: String) =
        _uiState.update { it.copy(selectedCommunityId = id, selectedCommunityName = name) }

    fun onTitleChange(value: String) =
        _uiState.update { it.copy(title = value, errorMessage = null) }

    fun onBodyChange(value: String) =
        _uiState.update { it.copy(body = value, errorMessage = null) }

    fun onLinkChange(index: Int, value: String) =
        _uiState.update { state ->
            if (index !in state.links.indices) return@update state
            state.copy(
                links = state.links.toMutableList().also { it[index] = value },
                errorMessage = null,
            )
        }

    fun addLinkField() =
        _uiState.update { it.copy(links = it.links + "", errorMessage = null) }

    fun removeLink(index: Int) =
        _uiState.update { state ->
            if (index !in state.links.indices) return@update state
            state.copy(
                links = state.links.toMutableList().also { it.removeAt(index) },
                errorMessage = null,
            )
        }

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
     * Hands the edit off to [PostUploadService] and closes the screen at once —
     * exactly like creating a post. Any newly added photo or video uploads in
     * the background with a status-bar notification, and the post is written
     * when that finishes; the user is returned to wherever they were (their
     * profile, a community, the feed) without waiting.
     */
    fun save(context: Context) {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please add a title.") }
            return
        }
        if (state.isSaved) return

        // The background service reads the new media after this screen is gone,
        // so take persistable read permission on each URI first — same as create.
        state.newMedia.forEach { item ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    item.uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                // Not all URIs support it; the service still reads them normally.
            }
        }

        val cleanLinks = state.links.map { it.trim() }.filter { it.isNotBlank() }

        val intent = Intent(context, PostUploadService::class.java).apply {
            putExtra(PostUploadService.EXTRA_POST_ID, state.postId)
            putExtra(PostUploadService.EXTRA_TITLE, state.title)
            putExtra(PostUploadService.EXTRA_BODY, state.body)
            putStringArrayListExtra(PostUploadService.EXTRA_LINK_URLS, ArrayList(cleanLinks))
            putExtra(PostUploadService.EXTRA_COMMUNITY_ID, state.selectedCommunityId)
            putExtra(PostUploadService.EXTRA_COMMUNITY_NAME, state.selectedCommunityName)

            // Media already on the post, kept as-is (three parallel arrays).
            putStringArrayListExtra(
                PostUploadService.EXTRA_EXISTING_URLS,
                ArrayList(state.existingMedia.map { it.url }),
            )
            putStringArrayListExtra(
                PostUploadService.EXTRA_EXISTING_TYPES,
                ArrayList(state.existingMedia.map { it.type }),
            )
            putStringArrayListExtra(
                PostUploadService.EXTRA_EXISTING_THUMBS,
                ArrayList(state.existingMedia.map { it.thumbUrl.orEmpty() }),
            )

            // Newly picked media to upload.
            if (state.newMedia.isNotEmpty()) {
                putStringArrayListExtra(
                    PostUploadService.EXTRA_MEDIA_URIS,
                    ArrayList(state.newMedia.map { it.uri.toString() }),
                )
                putStringArrayListExtra(
                    PostUploadService.EXTRA_MEDIA_TYPES,
                    ArrayList(state.newMedia.map { it.type }),
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        context.startForegroundService(intent)

        // Close immediately — the upload and save continue in the background.
        _uiState.update { it.copy(isSaved = true) }
    }
}
