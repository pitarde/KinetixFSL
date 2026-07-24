package com.example.kinetixfsl.community

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four destinations reachable from the community bottom nav. Order is
 * left-to-right on the bar.
 *
 * HOME = the feed we're building. The other three are placeholders for now.
 */
enum class CommunityTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", CommunityIcons.Home),
    PROFILE("Profile", CommunityIcons.Profile),
    CREATE("Create", CommunityIcons.CreatePost),
    NOTIFICATIONS("Notifications", CommunityIcons.Bell),
}