package com.example.kinetixfsl.community

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.FollowUser
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FollowersUiState(
    val followers: List<FollowUser> = emptyList(),
    /** Who I follow, so each row knows whether to offer "Follow back". */
    val following: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val query: String = "",
    val errorMessage: String? = null,
) {
    val visible: List<FollowUser>
        get() {
            val q = query.trim().lowercase()
            return if (q.isEmpty()) {
                followers
            } else {
                followers.filter { it.displayName.lowercase().contains(q) }
            }
        }
}

class FollowersViewModel(
    private val uid: String,
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(FollowersUiState())
    val uiState: StateFlow<FollowersUiState> = _uiState.asStateFlow()

    init {
        repository.followersOf(uid)
            .onEach { list ->
                _uiState.update { it.copy(followers = list, isLoading = false) }
            }
            .catch { t ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = t.localizedMessage ?: "Couldn't load followers.",
                    )
                }
            }
            .launchIn(viewModelScope)

        repository.observeFollowing()
            .onEach { ids -> _uiState.update { it.copy(following = ids) } }
            .catch { }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun toggleFollow(user: FollowUser) {
        viewModelScope.launch {
            if (user.uid in _uiState.value.following) {
                repository.unfollow(user.uid)
            } else {
                repository.follow(user)
            }
        }
    }
}

/**
 * Everyone following you, with a search box and a follow-back action per row.
 */
@Composable
fun FollowersScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    uid: String = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
) {
    val viewModel = remember(uid) { FollowersViewModel(uid) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp)
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CommunityIcons.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
        )

        Spacer(Modifier.height(12.dp))

        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.errorMessage != null -> Message(state.errorMessage!!)

            state.followers.isEmpty() -> Message("No one is following you yet.")

            state.visible.isEmpty() -> Message("No one matches \"${state.query}\".")

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.visible, key = { it.uid }) { user ->
                    FollowerRow(
                        user = user,
                        isFollowing = user.uid in state.following,
                        onToggleFollow = { viewModel.toggleFollow(user) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            "Search for a specific username",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun FollowerRow(
    user: FollowUser,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(avatarUrl = user.avatarUrl, name = user.displayName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.displayName.ifBlank { "Unknown" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Followed you ${user.createdAt.relativeToNow()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))

        val shape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .clip(shape)
                .then(
                    if (isFollowing) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    }
                )
                .clickable(onClick = onToggleFollow)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                // "Follow back" reads better than "Follow" here — this list is
                // specifically people who already followed you.
                text = if (isFollowing) "Followed" else "Follow back",
                style = MaterialTheme.typography.labelMedium,
                color = if (isFollowing) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
