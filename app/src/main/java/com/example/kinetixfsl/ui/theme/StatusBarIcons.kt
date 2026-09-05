package com.example.kinetixfsl.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Overrides the system status bar's icon color (clock, battery, notification
 * icons) for as long as the caller stays composed — light/white icons when
 * [light] is true, dark icons otherwise — then restores whatever was set
 * *before* this override on dispose, not the app-wide theme default.
 *
 * That "restore the previous value, not the theme default" is what makes
 * nested overrides stack correctly: [KinetixFSLTheme] sets the theme-driven
 * default for the whole app, a screen with its own dark top bar (the
 * community feed's navy bar, say) overrides that to light icons for as long
 * as it's shown, and a screen opened *over* that one — a profile, a chat —
 * can assert its own need (its plain background wants the ordinary theme
 * default back) without permanently clobbering what the screen underneath
 * asked for. Closing the innermost override always reveals exactly what the
 * screen beneath it wanted, all the way back down to the app default.
 */
@Composable
fun StatusBarLightIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(light) {
        val controller = view.context.findActivity()?.window?.let {
            WindowCompat.getInsetsController(it, view)
        }
        // isAppearanceLightStatusBars is the raw platform sense — true means
        // dark icons (for a light bar) — the inverse of this function's
        // "light icons" naming, which describes the icons themselves.
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !light
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }
}
