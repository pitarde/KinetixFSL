package com.example.kinetixfsl.community

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.kinetixfsl.community.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SharedPostUiState(
    val post: Post? = null,
    val userVote: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** A failed edit/delete/hide action — surfaced as a toast, not a full-screen error. */
    val actionError: String? = null,
    /** The post's community's own profile picture, looked up separately since
     *  it isn't denormalized onto the post the way the name is. */
    val communityAvatarUrl: String? = null,
    /** Whether the signed-in user has hidden this post — flips Hide to Unhide. */
    val isHidden: Boolean = false,
)

/**
 * Backs a post opened from a shared link. Unlike the feed, only this one post
 * is loaded — but it's a live listener, so counts stay current while it's open.
 */
class SharedPostViewModel(
    private val postId: String,
    private val repository: CommunityRepository = CommunityRepository(),
    private val directory: CommunityDirectoryRepository = CommunityDirectoryRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedPostUiState())
    val uiState: StateFlow<SharedPostUiState> = _uiState.asStateFlow()

    /** The community id we're already observing, so a live post update doesn't
     *  needlessly resubscribe on every snapshot. */
    private var observedCommunityId: String? = null

    init {
        repository.observePost(postId)
            .onEach { post ->
                _uiState.update {
                    it.copy(
                        post = post,
                        isLoading = false,
                        errorMessage = if (post == null) {
                            "This post is no longer available."
                        } else {
                            null
                        },
                    )
                }
                if (post != null && post.communityId.isNotBlank()) {
                    observeCommunityAvatar(post.communityId)
                }
            }
            .catch { t ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.localizedMessage ?: "Couldn't open this post.",
                    )
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            _uiState.update { it.copy(userVote = repository.getUserVote(postId)) }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isHidden = repository.isPostHidden(postId)) }
        }
    }

    private fun observeCommunityAvatar(communityId: String) {
        if (observedCommunityId == communityId) return
        observedCommunityId = communityId
        directory.observeCommunity(communityId)
            .onEach { community ->
                _uiState.update { it.copy(communityAvatarUrl = community?.avatarUrl) }
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun vote(direction: String) {
        viewModelScope.launch {
            val newDir = repository.vote(postId, direction)
            _uiState.update { it.copy(userVote = newDir) }
        }
    }

    /** Deletes this post. [onDone] runs after so the caller can close the screen. */
    fun deletePost(post: Post, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deletePost(post)
                .onSuccess { onDone() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(actionError = error.localizedMessage ?: "Couldn't delete post.")
                    }
                }
        }
    }

    /**
     * Hides or unhides this post from the signed-in user's feeds, depending on
     * the current state. [onHidden] runs only when the post *becomes* hidden —
     * that's the moment it makes sense to close the screen; unhiding it should
     * leave the user looking at it, now with "Hide" back on the menu.
     */
    fun toggleHide(onHidden: () -> Unit) {
        viewModelScope.launch {
            if (_uiState.value.isHidden) {
                repository.unhidePost(postId)
                _uiState.update { it.copy(isHidden = false) }
            } else {
                repository.hidePost(postId)
                _uiState.update { it.copy(isHidden = true) }
                onHidden()
            }
        }
    }

    fun dismissActionError() {
        _uiState.update { it.copy(actionError = null) }
    }

    fun share(context: Context, post: Post) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, post.title)
            putExtra(Intent.EXTRA_TEXT, ShareLinks.postUrl(post.id))
        }
        context.startActivity(Intent.createChooser(intent, "Share post"))

        viewModelScope.launch { repository.sharePost(post.id) }
    }
}

/**
 * The screen a shared link lands on. It's the same [PostDetailScreen] the feed
 * opens, so a linked post looks and behaves identically to tapping it in the
 * feed — [onClose] is wired to the community feed rather than to a dismiss.
 */
@Composable
fun SharedPostScreen(
    postId: String,
    onClose: () -> Unit,
    /** Opens the author's profile. No-op when there's nowhere to navigate. */
    onAuthorClick: (String) -> Unit = {},
    /**
     * Opens the editor for this post — the host owns that overlay, so it's
     * handed the loaded post. Null (the default) hides Edit even on your own
     * post, for callers that have nowhere to open an editor.
     */
    onEditPost: ((Post) -> Unit)? = null,
    /** Opens the post's community. No-op when there's nowhere to navigate. */
    onCommunityClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(postId) { SharedPostViewModel(postId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val post = state.post

    LaunchedEffect(state.actionError) {
        val message = state.actionError ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        viewModel.dismissActionError()
    }

    when {
        state.isLoading -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        post == null -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Post unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.errorMessage ?: "This post may have been deleted.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Go to the feed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }

        else -> {
            val commentVm = remember(post.id) { CommentViewModel(postId = post.id) }
            PostDetailScreen(
                post = post,
                viewModel = commentVm,
                userVote = state.userVote,
                onUpvote = { viewModel.vote("up") },
                onDownvote = { viewModel.vote("down") },
                onShare = { viewModel.share(context, post) },
                onAuthorClick = onAuthorClick,
                onCommunityClick = onCommunityClick,
                communityAvatarUrl = state.communityAvatarUrl,
                onEdit = onEditPost?.let { { it(post) } },
                onDelete = { viewModel.deletePost(post, onDone = onClose) },
                onHide = { viewModel.toggleHide(onHidden = onClose) },
                isHidden = state.isHidden,
                onClose = onClose,
                modifier = modifier,
            )
        }
    }
}
