package com.example.kinetixfsl.ui.home

/**
 * Sample data for the dashboard. Everything here is hardcoded so the screen
 * matches the design; later this all comes from Room (local progress) with a
 * Firestore sync layer, and this file goes away.
 *
 * The shapes below are what the ViewModel will eventually expose, so the UI
 * won't need to change when the data becomes real.
 */

/** Where the user is in a specific learning module (Alphabet, Numbers, ...). */
data class ModuleProgress(
    val title: String,
    val subtitle: String,
    /** 0f..1f, drives the progress bar. */
    val progress: Float,
    /** Displayed as-is on the right of the bar, e.g. "100xp". */
    val xpLabel: String,
    val status: ModuleStatus,
)

enum class ModuleStatus(val label: String) {
    IN_PROGRESS("In progress"),
    COMPLETED("Completed"),
    LOCKED("Locked"),
}

/** The top card — daily streak and cumulative progress across all modules. */
data class StreakSummary(
    val streakDays: Int,
    /** 0f..1f, drives the streak card's progress bar. */
    val overallProgress: Float,
)

// ---------------------------------------------------------------------------------
// Sample data — the ViewModel exposes these until real progress tracking exists.
// ---------------------------------------------------------------------------------

internal val SampleStreak = StreakSummary(
    streakDays = 5,
    overallProgress = 0.42f,
)

internal val SampleModules = listOf(
    ModuleProgress(
        title = "Alphabet:",
        subtitle = "A - Z",
        progress = 0.62f,
        xpLabel = "100xp",
        status = ModuleStatus.IN_PROGRESS,
    ),
    ModuleProgress(
        title = "Numbers:",
        subtitle = "0 - 9",
        progress = 0.40f,
        xpLabel = "100xp",
        status = ModuleStatus.IN_PROGRESS,
    ),
)