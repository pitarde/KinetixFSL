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
)

/**
 * Backs a post opened from a shared link. Unlike the feed, only this one post
 * is loaded — but it's a live listener, so counts stay current while it's open.
 */
class SharedPostViewModel(
    private val postId: String,
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedPostUiState())
    val uiState: StateFlow<SharedPostUiState> = _uiState.asStateFlow()

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
    }

    fun vote(direction: String) {
        viewModelScope.launch {
            val newDir = repository.vote(postId, direction)
            _uiState.update { it.copy(userVote = newDir) }
        }
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
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(postId) { SharedPostViewModel(postId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val post = state.post

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
                onClose = onClose,
                modifier = modifier,
            )
        }
    }
}
