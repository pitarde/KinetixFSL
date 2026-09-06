package com.example.kinetixfsl.ui.theme

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * One requested status-bar icon color, in the order it was requested. The
 * last entry always wins — see [applyTop].
 */
private data class StatusBarRequest(val token: Any, val light: Boolean)

/**
 * Every currently-mounted request, oldest first. Index 0 is always the
 * app-wide theme default set once by [KinetixFSLTheme] itself — see
 * [StatusBarDefault] — so the list is never empty once the app has started,
 * and "no screen override active" always resolves back to it correctly.
 */
private val requests = mutableListOf<StatusBarRequest>()

/**
 * Overrides the system status bar's icon color (clock, battery, notification
 * icons) for as long as the caller stays composed — light/white icons when
 * [light] is true, dark icons otherwise.
 *
 * Backed by a stack of every currently-mounted request rather than a single
 * "restore the previous value" snapshot: two sibling screens can be mounted
 * at once mid-transition (Compose Navigation's `AnimatedContent` keeps the
 * outgoing destination composed until its exit animation finishes), and if
 * each just captured-and-restored "whatever was there before me", whichever
 * one disposes *last* stomps the other's still-active request — exactly what
 * happened when the Home dashboard's own top bar (mounted when leaving Home
 * for Discover) disposed after Discover had already asserted its own
 * override, silently reverting Discover's white icons back to the app
 * default. Tracking every request and always applying the most recent
 * still-mounted one — see [applyTop] — is correct regardless of mount order.
 */
@Composable
fun StatusBarLightIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val token = remember { Any() }
    DisposableEffect(token, light) {
        requests.removeAll { it.token == token }
        requests.add(StatusBarRequest(token, light))
        applyTop(view)
        onDispose {
            requests.removeAll { it.token == token }
            applyTop(view)
        }
    }
}

/**
 * Registers the app-wide theme default as the permanent bottom of the
 * request stack — called once from [KinetixFSLTheme]. Updates its entry in
 * place (rather than pushing a new one) so a system theme change still takes
 * effect without disturbing whatever screen override is currently on top.
 */
@Composable
internal fun StatusBarDefault(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val token = remember { Any() }
    DisposableEffect(token, light) {
        val idx = requests.indexOfFirst { it.token == token }
        if (idx >= 0) requests[idx] = StatusBarRequest(token, light) else requests.add(0, StatusBarRequest(token, light))
        applyTop(view)
        onDispose { /* the app-wide default outlives every screen; never removed */ }
    }
}

/** Applies whichever request was mounted most recently. */
private fun applyTop(view: View) {
    val top = requests.lastOrNull() ?: return
    val controller = view.context.findActivity()?.window?.let {
        WindowCompat.getInsetsController(it, view)
    } ?: return
    // isAppearanceLightStatusBars is the raw platform sense — true means dark
    // icons (for a light bar) — the inverse of this function's "light icons"
    // naming, which describes the icons themselves.
    controller.isAppearanceLightStatusBars = !top.light
}
