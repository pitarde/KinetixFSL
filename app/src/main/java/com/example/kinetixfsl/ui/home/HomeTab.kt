package com.example.kinetixfsl.ui.home

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The five destinations reachable from the bottom navigation bar. Order matters —
 * it's the left-to-right order the icons appear in.
 *
 * Home and the four other tabs are all fully separate screens; the drawer is
 * only reachable from the Home tab (that's the only tab that shows the hamburger).
 */
enum class HomeTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", HomeIcons.Home),
    MODULES("Modules", HomeIcons.Modules),
    CAMERA("Camera", HomeIcons.Camera),
    GAME("Game", HomeIcons.Game),
    PROFILE("Profile", HomeIcons.Profile),
}