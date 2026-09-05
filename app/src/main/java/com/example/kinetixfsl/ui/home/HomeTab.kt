package com.example.kinetixfsl.ui.home

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The four destinations this screen can show. Order matters for
 * [BOTTOM_NAV_TABS] — it's the left-to-right order those icons appear in.
 *
 * The camera tab was removed from the bottom nav entirely (see
 * [BOTTOM_NAV_TABS]); PROFILE is reachable only via the icon in the
 * Dashboard's own top bar, not as a bottom-nav destination — the drawer's
 * hamburger stays on Home and Modules, which both keep the same top bar
 * treatment.
 */
enum class HomeTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", HomeIcons.Home),
    MODULES("Modules", HomeIcons.Modules),
    GAME("Game", HomeIcons.Game),
    PROFILE("Profile", HomeIcons.Profile),
    ;

    companion object {
        /** The three tabs shown as bottom-nav icons, in display order. */
        val BOTTOM_NAV_TABS = listOf(HOME, MODULES, GAME)
    }
}