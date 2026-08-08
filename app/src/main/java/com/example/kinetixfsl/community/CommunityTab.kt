package com.example.kinetixfsl.community

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four destinations reachable from the community bottom nav. Order is
 * left-to-right on the bar.
 */
enum class CommunityTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", CommunityIcons.Home),
    PROFILE("Profile", CommunityIcons.Profile),
    CREATE("Create", CommunityIcons.CreatePost),

    /**
     * Direct messages and notifications, on two tabs of one screen. The bell
     * icon stays: it's what a user looks for when they want to know what
     * happened, and both halves answer that.
     */
    INBOX("Inbox", CommunityIcons.Bell),
}