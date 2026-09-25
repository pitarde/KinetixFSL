package com.example.kinetixfsl.community.moderator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetixfsl.community.CommunityRepository
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One eligibility row: a current value racing toward a fixed target. */
data class RequirementProgress(val label: String, val current: Long, val target: Long) {
    val fraction: Float get() = (current.toFloat() / target).coerceIn(0f, 1f)
    val met: Boolean get() = current >= target
}

sealed class EligibilityUiState {
    object Loading : EligibilityUiState()
    data class Ready(
        val requirements: List<RequirementProgress>,
        val allMet: Boolean,
        /** "" | "pending" | "approved" | "rejected". */
        val applicationStatus: String,
        val rejectionNote: String?,
        val asOfDate: String,
        val isSubmitting: Boolean = false,
        val submitError: String? = null,
    ) : EligibilityUiState()
}

/**
 * Backs [EligibilityScreen]. Combines the signed-in user's own posts, profile
 * and moderator application into the four progress rows and the bottom
 * button's state — all three sources are live, so crossing the last threshold
 * (a new upvote arrives, say) flips the button without leaving the screen.
 */
class EligibilityViewModel(
    private val repository: CommunityRepository = CommunityRepository(),
    private val moderatorRepository: ModeratorRepository = ModeratorRepository(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<EligibilityUiState>(EligibilityUiState.Loading)
    val uiState: StateFlow<EligibilityUiState> = _uiState.asStateFlow()

    /**
     * The stats snapshot behind whatever's currently on screen — kept so
     * [apply] can submit exactly the numbers the user is looking at, without
     * re-deriving them (or re-subscribing) at tap time.
     */
    private data class Snapshot(
        val displayName: String,
        val avatarUrl: String?,
        val upvotesTotal: Long,
        val followerCount: Long,
        val postCount: Long,
        val accountAgeDays: Long,
    )

    private var latestSnapshot: Snapshot? = null

    init {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            combine(
                repository.postsByAuthor(uid),
                repository.observeUserProfile(uid),
                moderatorRepository.observeMyApplication(uid),
            ) { posts, profile, application ->
                val user = auth.currentUser
                val displayName = profile?.displayName?.takeIf { it.isNotBlank() }
                    ?: user?.displayName?.takeIf { it.isNotBlank() }
                    ?: "Anonymous"
                val avatarUrl = profile?.avatarUrl ?: user?.photoUrl?.toString()
                val upvotesTotal = posts.sumOf { it.upvoteCount }
                val followerCount = profile?.followerCount ?: 0
                val postCount = posts.size.toLong()
                // TEST MODE: measuring account age in seconds instead of days —
                // see the TEST/ORIGINAL threshold block below. Swap this back to
                // daysSince(...) when ACCOUNT_AGE_TARGET reverts to 30 days.
                val accountAgeDays = profile?.createdAt?.let { secondsSince(it.toDate().time) } ?: 0

                latestSnapshot = Snapshot(
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                    upvotesTotal = upvotesTotal,
                    followerCount = followerCount,
                    postCount = postCount,
                    accountAgeDays = accountAgeDays,
                )

                val requirements = listOf(
                    RequirementProgress("Upvotes received", upvotesTotal, UPVOTES_TARGET),
                    RequirementProgress("Followers", followerCount, FOLLOWERS_TARGET),
                    RequirementProgress("Posts published", postCount, POSTS_TARGET),
                    RequirementProgress(ACCOUNT_AGE_LABEL, accountAgeDays, ACCOUNT_AGE_TARGET),
                )

                EligibilityUiState.Ready(
                    requirements = requirements,
                    allMet = requirements.all { it.met },
                    applicationStatus = application?.status.orEmpty(),
                    rejectionNote = application?.rejectionNote,
                    asOfDate = "Showing data as of: ${todayFormatted()}",
                )
            }.onEach { ready ->
                // Preserve an in-flight submit's spinner/error across a fresh
                // emission from the underlying flows, rather than clobbering it.
                val previous = _uiState.value as? EligibilityUiState.Ready
                _uiState.value = if (previous != null) {
                    ready.copy(isSubmitting = previous.isSubmitting, submitError = previous.submitError)
                } else {
                    ready
                }
            }.launchIn(viewModelScope)
        }
    }

    /** Submits (or resubmits) the application with the currently shown stats. */
    fun apply() {
        val state = _uiState.value as? EligibilityUiState.Ready ?: return
        if (!state.allMet || state.isSubmitting) return
        val snapshot = latestSnapshot ?: return

        _uiState.update {
            (it as? EligibilityUiState.Ready)?.copy(isSubmitting = true, submitError = null) ?: it
        }
        viewModelScope.launch {
            val result = moderatorRepository.submitApplication(
                displayName = snapshot.displayName,
                avatarUrl = snapshot.avatarUrl,
                upvotesTotal = snapshot.upvotesTotal,
                followerCount = snapshot.followerCount,
                postCount = snapshot.postCount,
                accountAgeDays = snapshot.accountAgeDays,
            )
            _uiState.update { current ->
                if (current !is EligibilityUiState.Ready) return@update current
                current.copy(
                    isSubmitting = false,
                    submitError = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    private companion object {
        // ══════════════════════════════════════════════════════════════════
        // TEST THRESHOLDS — ACTIVE. For trying out the moderator flow only.
        // Do NOT ship a release with these. Swap back to ORIGINAL below (and
        // change accountAgeDays above to use daysSince(...) again) once done.
        // ══════════════════════════════════════════════════════════════════
        const val UPVOTES_TARGET = 1L
        const val FOLLOWERS_TARGET = 1L
        const val POSTS_TARGET = 1L
        const val ACCOUNT_AGE_TARGET = 5L
        const val ACCOUNT_AGE_LABEL = "Account age (sec)"

        // ── ORIGINAL THRESHOLDS — restore these before shipping ────────────
        // const val UPVOTES_TARGET = 1000L
        // const val FOLLOWERS_TARGET = 200L
        // const val POSTS_TARGET = 20L
        // const val ACCOUNT_AGE_TARGET = 30L
        // const val ACCOUNT_AGE_LABEL = "Account age (days)"
    }
}

private fun daysSince(millis: Long): Long =
    TimeUnit.MILLISECONDS.toDays((System.currentTimeMillis() - millis).coerceAtLeast(0))

/** TEST-ONLY helper — see the TEST THRESHOLDS block. Delete alongside it. */
private fun secondsSince(millis: Long): Long =
    TimeUnit.MILLISECONDS.toSeconds((System.currentTimeMillis() - millis).coerceAtLeast(0))

private fun todayFormatted(): String =
    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(java.util.Date())
