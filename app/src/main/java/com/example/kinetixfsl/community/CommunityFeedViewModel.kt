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

    /** Current search query. Empty = show all posts. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var feedJob: Job? = null

    /**
     * The locked-in display order. Post IDs in the order they should appear.
     * Only gets regenerated on first load or explicit refresh — vote/share
     * updates from Firestore reuse this same order so the feed stays still.
     */
    private var displayOrder: List<String> = emptyList()

    /** All posts from the latest Firestore snapshot (unfiltered). */
    private var allPosts: List<Post> = emptyList()

    /**
     * When true, the next snapshot will reorder posts and lock the order.
     * When false, incoming posts are re-sorted to match the existing
     * [displayOrder], so count updates don't cause the feed to jump.
     */
    private var shouldReorder = true

    /**
     * When true, the next reorder uses random shuffle.
     * When false, the next reorder uses newest-first.
     * Refresh always sets this to true (shuffle). New-post detection always
     * uses newest-first so the new post appears at the top without refreshing.
     */
    private var nextOrderIsRandom = false

    init {
        observeFeed()
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        emitFiltered()
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
     * Feed ordering logic:
     *
     * - First load: newest-first so new posts are visible immediately.
     * - New post arrives (ID set changes): newest-first so the new post
     *   appears at the top *without* the user having to refresh.
     * - Pull-to-refresh: always shuffles randomly to balance old and new.
     * - Vote/share/comment count update (same IDs): preserve current order.
     */
    private fun applyPosts(incoming: List<Post>) {
        allPosts = incoming

        val incomingIds = incoming.map { it.id }.toSet()
        val currentIds = displayOrder.toSet()
        val idsChanged = incomingIds != currentIds

        if (shouldReorder) {
            // Explicit refresh — always shuffle randomly.
            if (nextOrderIsRandom) {
                val shuffled = incoming.shuffled()
                displayOrder = shuffled.map { it.id }
            } else {
                val sorted = incoming.sortedByDescending { it.createdAt }
                displayOrder = sorted.map { it.id }
            }
            shouldReorder = false
        } else if (idsChanged) {
            // A post was added or deleted — show newest first so the
            // new post appears at the top automatically.
            val sorted = incoming.sortedByDescending { it.createdAt }
            displayOrder = sorted.map { it.id }
        }
        // Otherwise: same IDs, just count updates — keep current order.

        emitFiltered()
        prefetchVotes(incoming)
    }

    /**
     * Applies the search filter to the current display order and emits.
     */
    private fun emitFiltered() {
        val query = _searchQuery.value.trim().lowercase()
        val postById = allPosts.associateBy { it.id }
        val ordered = displayOrder.mapNotNull { id -> postById[id] }

        val filtered = if (query.isEmpty()) {
            ordered
        } else {
            ordered.filter { post ->
                post.title.lowercase().contains(query) ||
                        post.body.lowercase().contains(query) ||
                        post.authorName.lowercase().contains(query)
            }
        }

        _feedState.value = FeedState.Success(filtered)
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
     * Pull-to-refresh. Always shuffles the feed randomly to balance
     * old and new posts. New posts that arrive between refreshes
     * automatically go to the top without needing a refresh.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true

            feedJob?.cancel()
            feedJob = null
            shouldReorder = true
            nextOrderIsRandom = true

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
        }
    }
}
