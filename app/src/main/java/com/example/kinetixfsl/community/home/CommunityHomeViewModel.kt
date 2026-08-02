package com.example.kinetixfsl.community.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.CommunityDirectoryRepository
import com.example.kinetixfsl.community.model.Community
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs a single community's home screen. Streams the community document,
 * decides whether the viewer is its admin (the creator) — which unlocks the
 * category-management controls — and tracks whether they've joined, which
 * gates posting.
 */
class CommunityHomeViewModel(
    private val communityId: String,
    private val repository: CommunityDirectoryRepository = CommunityDirectoryRepository(),
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
