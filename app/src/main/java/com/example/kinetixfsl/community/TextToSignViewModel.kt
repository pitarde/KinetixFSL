package com.example.kinetixfsl.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.model.PostMedia
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One media result on the Text-to-Sign screen — a single photo or video lifted
 * out of a validated community post. Only the media is carried: the search
 * deliberately drops the post's title, votes and comments, showing just the
 * sign itself.
 */
data class SignMedia(
    val postId: String,
    val media: PostMedia,
)

data class TextToSignUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    /** Media from every validated post tagged with the searched word. */
    val results: List<SignMedia> = emptyList(),
    /** True once a search has actually run for the current query. */
    val hasSearched: Boolean = false,
)

/**
 * Backs the Text-to-Sign search. The user types a word (e.g. "sala"); the
 * repository returns admin-validated posts whose [hashtags] START WITH what's
 * been typed so far, and this flattens their media into a flat grid of signs.
 *
 * Matching is by #hashtag prefix: an author who wants a tutorial to be
 * searchable adds one #tag per sign in the media, and results appear as soon as
 * the typed text is a prefix of one of those tags — "sala" already surfaces
 * #salamat — rather than waiting for the whole word. (Default tutorials for
 * words with no community post yet are a later addition — see the empty state
 * on [TextToSignScreen].)
 */
class TextToSignViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(TextToSignUiState())
    val uiState: StateFlow<TextToSignUiState> = _uiState.asStateFlow()

    /** Cancels an in-flight search when the query changes again. */
    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()

        val term = value.trim()
        if (term.isEmpty()) {
            _uiState.update {
                it.copy(results = emptyList(), isLoading = false, hasSearched = false)
            }
            return
        }

        searchJob = viewModelScope.launch {
            // Debounce so a search doesn't fire on every keystroke.
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(term)
        }
    }

    /** Runs the search immediately (the keyboard's search action). */
    fun submitSearch() {
        val term = _uiState.value.query.trim()
        if (term.isEmpty()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(term) }
    }

    private suspend fun runSearch(term: String) {
        _uiState.update { it.copy(isLoading = true) }
        val posts = repository.validatedPostsByHashtagPrefix(term)
        val media = posts.flatMap { post ->
            post.mediaItems.map { SignMedia(postId = post.id, media = it) }
        }
        _uiState.update {
            it.copy(results = media, isLoading = false, hasSearched = true)
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
