package com.example.kinetixfsl.ui.home

import androidx.lifecycle.ViewModel
import com.example.kinetixfsl.auth.AuthRepository

/**
 * The dashboard's state. Extremely small for now — the interesting fields
 * (streak, module progress) are still hardcoded and read directly by the UI
 * from `DashboardData.kt`, so this ViewModel only needs to worry about the
 * greeting name.
 */
data class DashboardUiState(
    /** Falls back to "there" so we never render an awkward "Good morning, ." */
    val displayName: String = "there",
)

class DashboardViewModel(
    authRepository: AuthRepository = AuthRepository(),
) : ViewModel() {

    /**
     * Read once at construction — the greeting doesn't need to be reactive since
     * a name change from Firebase requires re-auth anyway, which recreates this VM.
     */
    val uiState: DashboardUiState = run {
        val user = authRepository.currentUser
        val name = user?.displayName?.takeIf { it.isNotBlank() }
            ?: user?.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "there"
        // Only the first name — "Good morning, Karl Cruz" reads worse than "Karl."
        DashboardUiState(displayName = name.substringBefore(' '))
    }
}