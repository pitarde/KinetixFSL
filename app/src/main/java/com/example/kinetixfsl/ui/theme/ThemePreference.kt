package com.example.kinetixfsl.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide light/dark preference — a DEVICE setting, not per-account, so it
 * lives in its own SharedPreferences file and is not namespaced by uid.
 *
 * [mode] is Compose state: reading it in [com.example.kinetixfsl.MainActivity]'s
 * `setContent` means changing it from the Settings screen re-themes the whole
 * app instantly, no restart.
 */
object ThemePreference {

    enum class Mode { SYSTEM, LIGHT, DARK }

    private const val PREFS = "kinetix_settings"
    private const val KEY = "theme_mode"

    private var prefs: android.content.SharedPreferences? = null

    /** The current choice. Observable — recompositions read it live. */
    var mode by mutableStateOf(Mode.SYSTEM)
        private set

    /** Load the saved choice once, at app start (before the first frame). */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { Mode.valueOf(p.getString(KEY, Mode.SYSTEM.name)!!) }
            .getOrDefault(Mode.SYSTEM)
    }

    /** Change the choice and persist it. Re-themes the app immediately. */
    fun set(newMode: Mode) {
        mode = newMode
        prefs?.edit()?.putString(KEY, newMode.name)?.apply()
    }
}
