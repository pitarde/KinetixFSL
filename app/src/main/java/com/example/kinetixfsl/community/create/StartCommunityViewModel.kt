package com.example.kinetixfsl.community.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.CommunityDirectoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI state for the two-step "Start a community" wizard. */
data class StartCommunityUiState(
    /** Categories the creator has picked so far. Multi-select. */
    val selectedCategories: Set<String> = emptySet(),
    val name: String = "",
    val description: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** The id of the freshly created community, set once the write lands. */
    val createdCommunityId: String? = null,
) {
    /** Screen 1 gates on at least one topic; the design lets you pick many. */
    val canContinueToDetails: Boolean get() = selectedCategories.isNotEmpty()

    /** Screen 2's "Create Community" button lights up once there's a name. */
    val canCreate: Boolean get() = name.isNotBlank() && selectedCategories.isNotEmpty()
}

class StartCommunityViewModel(
    private val repository: CommunityDirectoryRepository = CommunityDirectoryRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartCommunityUiState())
    val uiState: StateFlow<StartCommunityUiState> = _uiState.asStateFlow()

    /** Toggles a category chip on screen 1. */
    fun toggleCategory(category: String) {
        _uiState.value = _uiState.value.let { state ->
            val next = state.selectedCategories.toMutableSet().apply {
                if (!add(category)) remove(category)
            }
            state.copy(selectedCategories = next)
        }
    }

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value)
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Writes the community. On success [StartCommunityUiState.createdCommunityId]
     * is set, which the screen watches to navigate into the new community.
     */
    fun createCommunity() {
        val state = _uiState.value
        if (!state.canCreate || state.isSubmitting) return

        _uiState.value = state.copy(isSubmitting = true, errorMessage = null)
        viewModelScope.launch {
            val result = repository.createCommunity(
                name = state.name,
                description = state.description,
                categories = state.selectedCategories.toList(),
            )
            _uiState.value = result.fold(
                onSuccess = { id ->
                    _uiState.value.copy(isSubmitting = false, createdCommunityId = id)
                },
                onFailure = { error ->
                    _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = error.localizedMessage ?: "Couldn't create the community.",
                    )
                },
            )
        }
    }
}
