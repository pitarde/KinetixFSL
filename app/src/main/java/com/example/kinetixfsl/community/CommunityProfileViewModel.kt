package com.example.kinetixfsl.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.Community
import com.example.kinetixfsl.community.model.FollowUser
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
    /** False when viewing somebody else — swaps Edit for Message + Follow. */
    val isOwnProfile: Boolean = true,
    /** Whether the signed-in user already follows the profile being viewed. */
    val isFollowing: Boolean = false,
    val errorMessage: String? = null,
    /** The communities this profile's user has joined — the "My Communities" sheet. */
    val myCommunities: List<Community> = emptyList(),
    /** Count shown on the My Communities stat pill. */
    val myCommunityCount: Int = 0,
    /** Which of those the *viewer* has joined — flips each row's Join/Joined. */
    val viewerJoinedCommunityIds: Set<String> = emptySet(),
) {
    /** Posts plus comments — the "Contributions" stat. */
    val contributions: Int get() = posts.size + comments.size
}

/**
 * Backs both the signed-in user's profile and anybody else's.
 *
 * [userId] null means "me". When it names someone else the screen becomes a
 * visitor view: Edit is replaced by Message and Follow, and the post menu drops
 * the destructive actions.
 */
class CommunityProfileViewModel(
    private val userId: String? = null,
    private val repository: CommunityRepository = CommunityRepository(),
    private val directory: CommunityDirectoryRepository = CommunityDirectoryRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommunityProfileUiState())
    val uiState: StateFlow<CommunityProfileUiState> = _uiState.asStateFlow()

    /** Whose profile this is — the argument, or the signed-in user. */
    private val targetUid: String? = userId ?: auth.currentUser?.uid

    private val isOwn: Boolean = userId == null || userId == auth.currentUser?.uid

    init {
        _uiState.update { it.copy(isOwnProfile = isOwn) }

        if (isOwn) {
            // Auth is the freshest source for our own details.
            val user = auth.currentUser
            _uiState.update {
                it.copy(
                    displayName = user?.displayName?.takeIf { name -> name.isNotBlank() }
                        ?: user?.email?.substringBefore('@')
                        ?: "Anonymous",
                    avatarUrl = user?.photoUrl?.toString(),
                    accountAge = user?.metadata?.creationTimestamp?.let(::daysOldLabel) ?: "—",
                    // We're looking at our own profile, so we're active by
                    // definition — no need to wait for the document.
                    activeTime = "Active now",
                )
            }
        }

        val uid = targetUid
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
            observeProfileDoc(uid)
            observeViewerJoined()
            if (!isOwn) observeFollowState(uid)
        }
    }

    /** The viewer's own joined ids — so each My-Communities row reads Join/Joined. */
    private fun observeViewerJoined() {
        directory.observeJoinedCommunityIds()
            .onEach { ids -> _uiState.update { it.copy(viewerJoinedCommunityIds = ids) } }
            .catch { }
            .launchIn(viewModelScope)
    }

    /** Community ids we've already fetched full docs for, to avoid refetching. */
    private var lastCommunityIds: List<String> = emptyList()

    private fun loadMyCommunities(ids: List<String>) {
        if (ids == lastCommunityIds) return
        lastCommunityIds = ids
        _uiState.update { it.copy(myCommunityCount = ids.size) }
        if (ids.isEmpty()) {
            _uiState.update { it.copy(myCommunities = emptyList()) }
            return
        }
        viewModelScope.launch {
            val communities = directory.getCommunitiesByIds(ids)
            _uiState.update { it.copy(myCommunities = communities) }
        }
    }

    /** Join or leave a community from the My Communities sheet. */
    fun toggleJoinCommunity(community: Community) {
        viewModelScope.launch {
            if (community.id in _uiState.value.viewerJoinedCommunityIds) {
                directory.leave(community.id)
            } else {
                directory.join(community.id, community.name)
            }
        }
    }

    private fun observeProfileDoc(uid: String) {
        repository.observeUserProfile(uid)
            .onEach { profile ->
                loadMyCommunities(profile?.joinedCommunityIds ?: emptyList())
                _uiState.update { state ->
                    state.copy(
                        followerCount = profile?.followerCount ?: 0,
                        // Another user's name, age and presence can only come
                        // from their document — their Auth record isn't readable.
                        displayName = if (isOwn) {
                            state.displayName
                        } else {
                            profile?.displayName?.takeIf { it.isNotBlank() }
                                ?: state.displayName
                        },
                        avatarUrl = if (isOwn) state.avatarUrl else profile?.avatarUrl,
                        accountAge = if (isOwn) {
                            state.accountAge
                        } else {
                            profile?.createdAt?.toDate()?.time?.let(::daysOldLabel) ?: "—"
                        },
                        activeTime = if (isOwn) {
                            state.activeTime
                        } else {
                            lastActiveLabel(profile?.lastActiveAt?.toDate()?.time)
                        },
                    )
                }
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    private fun observeFollowState(uid: String) {
        repository.observeFollowing()
            .onEach { following ->
                _uiState.update { it.copy(isFollowing = uid in following) }
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    /** Follow or unfollow the profile being viewed. */
    fun toggleFollow() {
        val uid = targetUid ?: return
        if (isOwn) return
        val state = _uiState.value

        viewModelScope.launch {
            val result = if (state.isFollowing) {
                repository.unfollow(uid)
            } else {
                repository.follow(
                    FollowUser(
                        uid = uid,
                        displayName = state.displayName,
                        avatarUrl = state.avatarUrl,
                    )
                )
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Couldn't update follow.")
                }
            }
        }
    }

    /** Follows a post's author, from the post menu's "Follow post". */
    fun followAuthor(post: Post) {
        viewModelScope.launch {
            repository.follow(
                FollowUser(
                    uid = post.authorId,
                    displayName = post.authorName,
                    avatarUrl = post.authorAvatarUrl,
                )
            ).onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.localizedMessage ?: "Couldn't follow.")
                }
            }
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

/**
 * Messenger-style presence: "Active now" while they're around, then how long
 * they've been gone — "5min", "3hr", "2d".
 *
 * The two-minute window stops the label flickering between "now" and "1min"
 * while somebody is actually using the app.
 */
private fun lastActiveLabel(millis: Long?): String {
    if (millis == null) return "—"
    val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        minutes < 2 -> "Active now"
        minutes < 60 -> "${minutes}min"
        hours < 24 -> "${hours}hr"
        else -> "${days}d"
    }
}
