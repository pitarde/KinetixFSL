package com.example.kinetixfsl.community.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.CommunityDirectoryRepository
import com.example.kinetixfsl.community.model.Community
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Discover / category list renders. */
sealed interface DiscoverState {
    data object Loading : DiscoverState
    data class Success(val communities: List<Community>) : DiscoverState
    data class Error(val message: String) : DiscoverState
}

/**
 * Backs the Discover screen and the per-category list — the same list of
 * communities, optionally narrowed to one [filterCategory]. Tracks which
 * communities the user has joined so each card's Join button reads correctly.
 */
class DiscoverCommunitiesViewModel(
    private val filterCategory: String? = null,
    private val repository: CommunityDirectoryRepository = CommunityDirectoryRepository(),
) : ViewModel() {

    val state: StateFlow<DiscoverState> =
        repository.observeAllCommunities()
            .map { communities ->
                val shown = if (filterCategory == null) {
                    communities
                } else {
                    communities.filter { filterCategory in it.categories }
                }
                DiscoverState.Success(shown) as DiscoverState
            }
            .catch { emit(DiscoverState.Error(it.localizedMessage ?: "Couldn't load communities.")) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverState.Loading)

    /** Ids the user has joined — flips each card's button between Join/Joined. */
    val joinedIds: StateFlow<Set<String>> =
        repository.observeJoinedCommunityIds()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun join(community: Community) {
        viewModelScope.launch { repository.join(community.id, community.name) }
    }

    fun leave(communityId: String) {
        viewModelScope.launch { repository.leave(communityId) }
    }
}
