package com.example.kinetixfsl.community

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.FollowUser
import com.example.kinetixfsl.community.model.Post
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

sealed interface FeedState {
    data object Loading : FeedState
    data class Success(val posts: List<Post>) : FeedState
    data class Error(val message: String) : FeedState
}

class CommunityFeedViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
    private val directory: CommunityDirectoryRepository = CommunityDirectoryRepository(),
    /**
     * Non-null makes this a single community's feed: posts are drawn from that
     * community only and ranked by vote score, re-ranking live as votes change.
     * Null is the Home Feed — every post, individual and community alike, so a
     * community's posts surface to everyone and help the community get noticed.
     */
    private val communityId: String? = null,
) : ViewModel() {

    /** Communities the signed-in user has joined — drives each card's Join pill. */
    val joinedCommunityIds: StateFlow<Set<String>> =
        directory.observeJoinedCommunityIds()
            .catch { emit(emptySet()) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptySet())

    private val currentUid: String? =
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Community id → its profile picture URL, for the community header a
     * community post shows in the home feed. Posts don't carry the community's
     * avatar (it can change after posting), so it's looked up here and cached.
     */
    private val _communityAvatars = MutableStateFlow<Map<String, String?>>(emptyMap())
    val communityAvatars: StateFlow<Map<String, String?>> = _communityAvatars.asStateFlow()
    private val fetchedCommunityIds = mutableSetOf<String>()

    /** Communities the viewer created — their posts show no Join pill (you can't
     *  join your own community). */
    private val _ownedCommunityIds = MutableStateFlow<Set<String>>(emptySet())
    val ownedCommunityIds: StateFlow<Set<String>> = _ownedCommunityIds.asStateFlow()

    /**
     * Posts the viewer hid — filtered out of every emission below. Backed by a
     * live listener, so hiding one never needs a local patch: it just drops out
     * of [allPosts] the moment Firestore confirms the write.
     */
    private val hiddenPostIds: StateFlow<Set<String>> =
        repository.observeHiddenPostIds()
            .catch { emit(emptySet()) }
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), emptySet())

    private fun prefetchCommunityAvatars(posts: List<Post>) {
        val ids = posts
            .map { it.communityId }
            .filter { it.isNotBlank() && fetchedCommunityIds.add(it) }
            .distinct()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val communities = directory.getCommunitiesByIds(ids)
            val avatars = communities.associate { it.id to it.avatarUrl }
            val owned = communities.filter { it.creatorId == currentUid }.map { it.id }.toSet()
            if (avatars.isNotEmpty()) _communityAvatars.value = _communityAvatars.value + avatars
            if (owned.isNotEmpty()) _ownedCommunityIds.value = _ownedCommunityIds.value + owned
        }
    }

    /** Join or leave the community a home-feed post belongs to. */
    fun toggleJoinCommunity(post: Post) {
        val cid = post.communityId
        if (cid.isBlank()) return
        viewModelScope.launch {
            if (cid in joinedCommunityIds.value) {
                directory.leave(cid)
            } else {
                directory.join(cid, post.communityName)
            }
        }
    }

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
     * When true, the next snapshot re-ranks by hot score and locks the order.
     * When false, incoming posts reuse the existing [displayOrder], so vote
     * updates don't make the feed jump. Set true on first load and refresh.
     */
    private var shouldReorder = true

    /** Author ids the signed-in user follows. */
    private val _followingIds = MutableStateFlow<Set<String>>(emptySet())
    val followingIds: StateFlow<Set<String>> = _followingIds.asStateFlow()

    /**
     * One-shot message for a failed follow. A follow that silently does
     * nothing is near-impossible to diagnose from the outside, so failures
     * surface rather than being swallowed.
     */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun clearActionError() { _actionError.value = null }

    init {
        observeFeed()

        viewModelScope.launch { repository.ensureUserProfile() }

        repository.observeFollowing()
            .onEach { ids ->
                _followingIds.value = ids
                // Re-emit so followed authors move to the top immediately
                // rather than waiting for the next snapshot.
                emitFiltered()
            }
            .catch { /* following is optional — the feed still works */ }
            .launchIn(viewModelScope)

        // Re-emit the instant a hide lands, so the post disappears immediately
        // rather than waiting for the next post snapshot.
        hiddenPostIds
            .onEach { emitFiltered() }
            .launchIn(viewModelScope)
    }

    /** Hides a post from this and every other feed, permanently. */
    fun hidePost(postId: String) {
        viewModelScope.launch { repository.hidePost(postId) }
    }

    /** Deletes a post the signed-in user authored. */
    fun deletePost(post: Post) {
        viewModelScope.launch {
            repository.deletePost(post).onFailure { error ->
                _actionError.value = error.localizedMessage ?: "Couldn't delete post."
            }
        }
    }

    /**
     * Follows or unfollows the post's author.
     *
     * The button reads from [followingIds], which is a live Firestore listener,
     * so the label flips as soon as the write lands — no local optimism needed.
     */
    fun toggleFollow(post: Post) {
        val authorId = post.authorId
        if (authorId.isBlank()) return

        viewModelScope.launch {
            val result = if (authorId in _followingIds.value) {
                repository.unfollow(authorId)
            } else {
                repository.follow(
                    FollowUser(
                        uid = authorId,
                        displayName = post.authorName,
                        avatarUrl = post.authorAvatarUrl,
                    )
                )
            }
            result.onFailure { error ->
                _actionError.value = error.localizedMessage ?: "Couldn't update follow."
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        emitFiltered()
    }

    private fun observeFeed() {
        feedJob?.cancel()
        val source = if (communityId != null) {
            repository.communityPosts(communityId)
        } else {
            repository.feedPosts()
        }
        feedJob = source
            .onEach { posts -> applyPosts(posts) }
            .catch { t ->
                _feedState.value = FeedState.Error(
                    t.localizedMessage ?: "Couldn't load posts.",
                )
            }
            .launchIn(viewModelScope)
    }

    /**
     * Feed ordering: Reddit's "Hot" ranking (see [hotScore]).
     *
     * The order is recomputed from raw data — vote score plus post age — so:
     *
     * - First load, pull-to-refresh, or a post added/removed → re-rank by hot
     *   score. On refresh this naturally reshuffles, because time has moved and
     *   votes have changed since last time; a brand-new post ranks near the top
     *   on its recency. No stored order, no special "refresh logic" needed.
     * - Vote/share/comment update on the same set of posts → keep the current
     *   order, so the feed doesn't jump under the reader mid-scroll. Reddit
     *   re-ranks a loaded page on refresh, not on every incoming vote.
     *
     * Home and community feeds share this — the only difference is the candidate
     * pool the snapshot was drawn from (everything vs one community's posts).
     */
    private fun applyPosts(incoming: List<Post>) {
        allPosts = incoming

        val incomingIds = incoming.map { it.id }.toSet()
        val currentIds = displayOrder.toSet()
        val idsChanged = incomingIds != currentIds

        if (shouldReorder || idsChanged) {
            displayOrder = incoming
                .sortedByDescending { hotScore(it) }
                .map { it.id }
            shouldReorder = false
        }
        // Otherwise: same posts, only counts changed — hold the order steady.

        emitFiltered()
        prefetchVotes(incoming)
        prefetchCommunityAvatars(incoming)
    }

    /**
     * Reddit's open-sourced "Hot" score, adapted for up/down votes:
     *
     *   sign · log10(max(|score|, 1)) + createdAtSeconds / 45000
     *
     * The log makes a post's first few votes count for far more than later ones
     * (1→10 ≈ 10→100 ≈ 100→1000). The time term lifts the baseline by a fixed
     * amount every ~12.5 hours, so newer posts outrank older ones at equal
     * votes and old posts sink even while slowly gaining votes.
     */
    private fun hotScore(post: Post): Double {
        val s = post.score
        val order = log10(max(abs(s), 1L).toDouble())
        val sign = when {
            s > 0L -> 1.0
            s < 0L -> -1.0
            else -> 0.0
        }
        val seconds = post.createdAt?.seconds ?: 0L
        return sign * order + seconds / 45000.0
    }

    /**
     * Applies the search filter to the current display order and emits.
     */
    private fun emitFiltered() {
        val query = _searchQuery.value.trim().lowercase()
        val postById = allPosts.associateBy { it.id }
        val hidden = hiddenPostIds.value
        val ordered = displayOrder.mapNotNull { id -> postById[id] }
            .filter { it.id !in hidden }

        val filtered = if (query.isEmpty()) {
            ordered
        } else {
            ordered.filter { post ->
                post.title.lowercase().contains(query) ||
                        post.body.lowercase().contains(query) ||
                        post.authorName.lowercase().contains(query)
            }
        }

        // Ranking is the hot score alone — nothing jumps the queue. Following
        // someone widens the candidate pool elsewhere; it doesn't reorder here.
        _feedState.value = FeedState.Success(filtered)
    }

    /**
     * Post ids whose vote we've already looked up, whether or not the user had
     * voted. Without this, any post the user *hasn't* voted on never lands in
     * [_userVotes] and so was re-queried on every single snapshot — a fresh
     * read per post, every time any count anywhere changed. Invisible on WiFi,
     * but on mobile data it's dozens of round trips fighting for the link and
     * the feed takes forever to settle.
     */
    private val checkedVotePostIds = mutableSetOf<String>()

    private fun prefetchVotes(posts: List<Post>) {
        val unchecked = posts.map { it.id }.filter { checkedVotePostIds.add(it) }
        if (unchecked.isEmpty()) return

        viewModelScope.launch {
            val found = mutableMapOf<String, String>()
            unchecked.forEach { postId ->
                repository.getUserVote(postId)?.let { found[postId] = it }
            }
            if (found.isNotEmpty()) {
                _userVotes.value = _userVotes.value + found
            }
        }
    }

    /**
     * Pull-to-refresh. Re-ranks by hot score against current votes and the
     * current time, so the order naturally changes since the last refresh —
     * older posts having decayed, vote counts having moved.
     */
    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true

            feedJob?.cancel()
            feedJob = null
            shouldReorder = true

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

    /**
     * Opens the system share sheet with a link back to the post, then records
     * the share. The repository keeps the count to one per account, so sharing
     * the same post twice doesn't inflate the number.
     */
    fun share(context: Context, post: Post) {
        // Just the link. The title used to be prepended, but it showed up as a
        // second line of text in chat apps on top of their own link preview,
        // which read as clutter.
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, post.title)
            putExtra(Intent.EXTRA_TEXT, ShareLinks.postUrl(post.id))
        }
        context.startActivity(Intent.createChooser(intent, "Share post"))

        viewModelScope.launch {
            repository.sharePost(post.id)
        }
    }
}
