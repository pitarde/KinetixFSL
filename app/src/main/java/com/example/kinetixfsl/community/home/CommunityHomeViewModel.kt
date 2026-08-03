package com.example.kinetixfsl.community.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.CommunityDirectoryRepository
import com.example.kinetixfsl.community.CommunityRepository
import com.example.kinetixfsl.community.model.Community
import com.example.kinetixfsl.community.upload.R2MediaUploader
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Backs a single community's home screen. Streams the community document,
 * decides whether the viewer is its admin (the creator) — which unlocks the
 * category-management controls — and tracks whether they've joined, which
 * gates posting.
 */
class CommunityHomeViewModel(
    private val communityId: String,
    private val repository: CommunityDirectoryRepository = CommunityDirectoryRepository(),
    private val postsRepository: CommunityRepository = CommunityRepository(),
    auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    private val currentUid: String? = auth.currentUser?.uid

    val community: StateFlow<Community?> =
        repository.observeCommunity(communityId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** True once the loaded community turns out to be one this user created. */
    val isAdmin: StateFlow<Boolean> =
        community
            .map { it != null && it.creatorId == currentUid }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val joinedIds: StateFlow<Set<String>> =
        repository.observeJoinedCommunityIds()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Whether the signed-in user has joined this community — gates posting. */
    val isMember: StateFlow<Boolean> =
        joinedIds
            .map { communityId in it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Removes a category from this community. Admin-only, enforced by the UI. */
    fun removeCategory(category: String) {
        viewModelScope.launch { repository.removeCategory(communityId, category) }
    }

    /**
     * Adds a category. Not wired to the UI yet — the "Add" button is a
     * placeholder pending its own design — but kept here so hooking it up later
     * is a one-line change.
     */
    fun addCategory(category: String) {
        viewModelScope.launch { repository.addCategory(communityId, category) }
    }

    // -------------------------------------------------------------------------
    // Editing the community's banner and profile picture (admin only)
    // -------------------------------------------------------------------------

    /** Which image is uploading right now, so the edit sheet can show progress. */
    enum class Uploading { NONE, AVATAR, BANNER }

    private val _uploading = MutableStateFlow(Uploading.NONE)
    val uploading: StateFlow<Uploading> = _uploading.asStateFlow()

    private val _editError = MutableStateFlow<String?>(null)
    val editError: StateFlow<String?> = _editError.asStateFlow()

    fun clearEditError() { _editError.value = null }

    /** Uploads the cropped [bitmap] and sets it as the community's profile picture. */
    fun updateAvatar(bitmap: Bitmap) = uploadAndSave(bitmap, Uploading.AVATAR)

    /** Uploads the cropped [bitmap] and sets it as the community's banner. */
    fun updateBanner(bitmap: Bitmap) = uploadAndSave(bitmap, Uploading.BANNER)

    private fun uploadAndSave(bitmap: Bitmap, which: Uploading) {
        if (_uploading.value != Uploading.NONE) return
        _uploading.value = which
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    out.toByteArray()
                }
            }
            when (val result = R2MediaUploader.uploadBytes(
                bytes = bytes,
                fileName = if (which == Uploading.AVATAR) "avatar.jpg" else "banner.jpg",
                mimeType = "image/jpeg",
                resourceType = "image",
            )) {
                is R2MediaUploader.UploadResult.Success -> {
                    val save = if (which == Uploading.AVATAR) {
                        repository.updateCommunityImages(communityId, avatarUrl = result.secureUrl)
                    } else {
                        repository.updateCommunityImages(communityId, bannerUrl = result.secureUrl)
                    }
                    save.onFailure { _editError.value = it.localizedMessage ?: "Couldn't save the image." }
                }
                is R2MediaUploader.UploadResult.Error -> {
                    _editError.value = result.message
                }
            }
            _uploading.value = Uploading.NONE
        }
    }

    // -------------------------------------------------------------------------
    // Deleting the whole community (creator only)
    // -------------------------------------------------------------------------

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /**
     * Permanently deletes the community and everything in it: every member's
     * posts (with their media, comments, votes and shares), the member roster,
     * and the community document. [onDeleted] runs once it's gone so the screen
     * can close.
     */
    fun deleteCommunity(onDeleted: () -> Unit) {
        if (_isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            val posts = postsRepository.postsInCommunity(communityId)
            posts.forEach { postsRepository.deletePost(it) }
            repository.deleteCommunity(communityId)
            _isDeleting.value = false
            onDeleted()
        }
    }

    /** Joins this community so the user can post to it. */
    fun join() {
        val name = community.value?.name.orEmpty()
        viewModelScope.launch { repository.join(communityId, name) }
    }

    /** Leaves this community. */
    fun leave() {
        viewModelScope.launch { repository.leave(communityId) }
    }
}
