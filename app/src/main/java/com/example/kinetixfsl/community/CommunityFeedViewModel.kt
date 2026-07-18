package com.example.kinetixfsl.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * The community feed's state, boiled down to one immutable object. UI reads it
 * and picks a branch: loading spinner, empty state, error, or the actual list.
 */
sealed interface FeedState {
    data object Loading : FeedState
    data class Success(val posts: List<Post>) : FeedState
    data class Error(val message: String) : FeedState
}

class CommunityFeedViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _feedState = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedState: StateFlow<FeedState> = _feedState.asStateFlow()

    init {
        observeFeed()
    }

    private fun observeFeed() {
        repository.feedPosts()
            .onEach { posts -> _feedState.value = FeedState.Success(posts) }
            .catch { throwable ->
                _feedState.value = FeedState.Error(
                    throwable.localizedMessage ?: "Couldn't load posts. Try again.",
                )
            }
            .launchIn(viewModelScope)
    }
}