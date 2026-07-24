package com.example.kinetixfsl.community

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Post
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _userVotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val userVotes: StateFlow<Map<String, String>> = _userVotes.asStateFlow()

    private var feedJob: Job? = null

    /**
     * The locked-in display order. Post IDs in the order they should appear.
     * Only gets regenerated on first load or explicit refresh — vote/share
     * updates from Firestore reuse this same order so the feed stays still.
     */
    private var displayOrder: List<String> = emptyList()

    /**
     * When true, the next snapshot will shuffle the posts into a new random order
     * and lock it in. When false, incoming posts are re-sorted to match the
     * existing [displayOrder], so count updates don't cause the feed to jump.
     */
    private var shouldReshuffle = true

    init {
        observeFeed()
    }

    private fun observeFeed() {
        feedJob?.cancel()
        feedJob = repository.feedPosts()
            .onEach { posts -> applyPosts(posts) }
            .catch { t ->
                _feedState.value = FeedState.Error(
                    t.localizedMessage ?: "Couldn't load posts.",
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Decides whether to shuffle or preserve order, then emits the result.
     *
     * - First load / after refresh: shuffle and lock the new order.
     * - Vote/share/comment count update (same set of post IDs): keep the
     *   existing order, just swap in the updated Post objects so counts render.
     * - A post was added or deleted (ID set changed): re-shuffle to absorb it.
     */
    private fun applyPosts(incoming: List<Post>) {
        val incomingIds = incoming.map { it.id }.toSet()
        val currentIds = displayOrder.toSet()
        val idsChanged = incomingIds != currentIds

        if (shouldReshuffle || idsChanged) {
            // Fresh shuffle.
            val shuffled = incoming.shuffled()
            displayOrder = shuffled.map { it.id }
            shouldReshuffle = false
            _feedState.value = FeedState.Success(shuffled)
        } else {
            // Same posts, just updated data (counts changed). Preserve order.
            val postById = incoming.associateBy { it.id }
            val ordered = displayOrder.mapNotNull { id -> postById[id] }
            _feedState.value = FeedState.Success(ordered)
        }

        prefetchVotes(incoming)
    }

    private fun prefetchVotes(posts: List<Post>) {
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

    /**
     * Pull-to-refresh. Flags a reshuffle, clears vote cache, restarts the
     * Firestore listener. The next snapshot will generate a new random order.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true

            feedJob?.cancel()
            feedJob = null
            shouldReshuffle = true

            observeFeed()

            delay(1200)
            _isRefreshing.value = false
        }
    }

    fun vote(postId: String, direction: String) {
        viewModelScope.launch {
            val newDir = repository.vote(postId, direction)
            _userVotes.value = if (newDir != null) {
                _userVotes.value + (postId to newDir)
            } else {
                _userVotes.value - postId
            }
            // The Firestore snapshot listener will fire with updated counts.
            // applyPosts() will see the same IDs and preserve the current order.
        }
    }

    fun share(context: Context, post: Post) {
        val text = "${post.title}\n\nShared from KinetixFSL"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share post"))

        viewModelScope.launch {
            repository.incrementShareCount(post.id)
            // Same as vote — snapshot fires, counts update, order stays.
        }
    }
}