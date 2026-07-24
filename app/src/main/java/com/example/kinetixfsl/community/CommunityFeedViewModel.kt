package com.example.kinetixfsl.community

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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

    /**
     * Tracks the current user's vote on each post they've interacted with.
     * Key = postId, value = "up", "down", or absent if no vote.
     */
    private val _userVotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val userVotes: StateFlow<Map<String, String>> = _userVotes.asStateFlow()

    init {
        observeFeed()
    }

    private fun observeFeed() {
        repository.feedPosts()
            .onEach { posts ->
                _feedState.value = FeedState.Success(posts)
                // Prefetch the user's votes for visible posts.
                posts.forEach { post ->
                    if (post.id !in _userVotes.value) {
                        viewModelScope.launch {
                            val dir = repository.getUserVote(post.id)
                            if (dir != null) {
                                _userVotes.value = _userVotes.value + (post.id to dir)
                            }
                        }
                    }
                }
            }
            .catch { t ->
                _feedState.value = FeedState.Error(
                    t.localizedMessage ?: "Couldn't load posts.",
                )
            }
            .launchIn(viewModelScope)
    }

    fun vote(postId: String, direction: String) {
        viewModelScope.launch {
            val newDir = repository.vote(postId, direction)
            _userVotes.value = if (newDir != null) {
                _userVotes.value + (postId to newDir)
            } else {
                _userVotes.value - postId
            }
        }
    }

    /**
     * Fires an Android share Intent with the post title and a simple text link,
     * then increments the share count in Firestore.
     */
    fun share(context: Context, post: Post) {
        val text = "${post.title}\n\nShared from KinetixFSL"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share post"))

        viewModelScope.launch {
            repository.incrementShareCount(post.id)
        }
    }
}
