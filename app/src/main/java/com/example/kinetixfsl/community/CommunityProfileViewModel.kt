package com.example.kinetixfsl.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Post
import com.example.kinetixfsl.community.model.UserComment
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class CommunityProfileUiState(
    val displayName: String = "",
    val avatarUrl: String? = null,
    val posts: List<Post> = emptyList(),
    val comments: List<UserComment> = emptyList(),
    val isLoadingPosts: Boolean = true,
    val isLoadingComments: Boolean = true,
    /**
     * Scoped to the Comments tab. The collection-group query it uses needs its
     * own Firestore rule, and when that's missing only this tab should break —
     * the rest of the profile still works.
     */
    val commentsError: String? = null,
    val followerCount: Long = 0,
    val accountAge: String = "—",
    val activeTime: String = "—",
    val errorMessage: String? = null,
) {
    /** Posts plus comments — the "Contributions" stat. */
    val contributions: Int get() = posts.size + comments.size
}

class CommunityProfileViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityProfileUiState())
    val uiState: StateFlow<CommunityProfileUiState> = _uiState.asStateFlow()

    init {
        val user = auth.currentUser

        _uiState.update {
            it.copy(
                displayName = user?.displayName?.takeIf { name -> name.isNotBlank() }
                    ?: user?.email?.substringBefore('@')
                    ?: "Anonymous",
                avatarUrl = user?.photoUrl?.toString(),
                accountAge = user?.metadata?.creationTimestamp?.let(::daysOldLabel) ?: "—",
                // This is the signed-in user looking at their own profile, so
                // by definition they're active right now. Real presence for
                // other users comes later.
                activeTime = "Active now",
            )
        }

        val uid = user?.uid
        if (uid == null) {
            _uiState.update {
                it.copy(
                    isLoadingPosts = false,
                    isLoadingComments = false,
                    errorMessage = "You're not signed in.",
                )
            }
        } else {
            observePosts(uid)
            observeComments(uid)

            repository.observeUserProfile(uid)
                .onEach { profile ->
                    _uiState.update { it.copy(followerCount = profile?.followerCount ?: 0) }
                }
                .catch { }
                .launchIn(viewModelScope)
        }
    }

    private fun observePosts(uid: String) {
        repository.postsByAuthor(uid)
            .onEach { posts ->
                _uiState.update { it.copy(posts = posts, isLoadingPosts = false) }
                prefetchVotes(posts)
            }
            .catch { t ->
                _uiState.update {
                    it.copy(
                        isLoadingPosts = false,
                        errorMessage = t.localizedMessage ?: "Couldn't load your posts.",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeComments(uid: String) {
        repository.commentsByAuthor(uid)
            .onEach { comments ->
                // Titles live on the parent posts, so they're filled in after.
                _uiState.update { it.copy(comments = comments, isLoadingComments = false) }

                val titles = repository.postTitles(comments.map { it.postId })
                _uiState.update { state ->
                    state.copy(
                        comments = state.comments.map { item ->
                            item.copy(postTitle = titles[item.postId] ?: item.postTitle)
                        }
                    )
                }
            }
            .catch { t ->
                _uiState.update {
                    it.copy(
                        isLoadingComments = false,
                        commentsError = t.localizedMessage
                            ?: "Couldn't load your comments.",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private val _userVotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val userVotes: StateFlow<Map<String, String>> = _userVotes.asStateFlow()

    /** Ids already looked up, so snapshots don't re-query the same posts. */
    private val checkedVotePostIds = mutableSetOf<String>()

    private fun prefetchVotes(posts: List<Post>) {
        val unchecked = posts.map { it.id }.filter { checkedVotePostIds.add(it) }
        if (unchecked.isEmpty()) return

        viewModelScope.launch {
            val found = mutableMapOf<String, String>()
            unchecked.forEach { id -> repository.getUserVote(id)?.let { found[id] = it } }
            if (found.isNotEmpty()) _userVotes.value = _userVotes.value + found
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

    fun share(context: android.content.Context, post: Post) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, post.title)
            putExtra(android.content.Intent.EXTRA_TEXT, ShareLinks.postUrl(post.id))
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share post"))
        viewModelScope.launch { repository.sharePost(post.id) }
    }

    fun deletePost(post: Post) {
        viewModelScope.launch {
            repository.deletePost(post).onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Couldn't delete post.")
                }
            }
        }
    }

    fun editComment(item: UserComment, newBody: String) {
        viewModelScope.launch {
            repository.updateComment(item.postId, item.comment.id, newBody)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.localizedMessage
                                ?: "Couldn't save your comment.",
                        )
                    }
                }
        }
    }

    fun deleteComment(item: UserComment) {
        viewModelScope.launch {
            repository.deleteComment(item.postId, item.comment.id)
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = error.localizedMessage
                                ?: "Couldn't delete your comment.",
                        )
                    }
                }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Whole days since the account was created — "0d" on the first day, ticking up
 * by one every day after. Deliberately always in days rather than rolling up to
 * months or years, so the number keeps growing visibly.
 */
private fun daysOldLabel(millis: Long): String {
    val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    return "${TimeUnit.MILLISECONDS.toDays(diff)}d"
}
