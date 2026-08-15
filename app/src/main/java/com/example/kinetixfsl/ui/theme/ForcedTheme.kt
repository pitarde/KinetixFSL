package com.example.kinetixfsl.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Makes ANDROID RESOURCES follow the app's chosen light/dark theme.
 *
 * `KinetixFSLTheme(darkTheme = …)` only swaps the Compose *colour scheme*.
 * Resource-based assets — drawables with a `-night` variant, string/dimen
 * qualifiers — resolve from the device **configuration's** uiMode instead, so
 * forcing dark/light in Settings left those (e.g. the onboarding illustration)
 * on the phone's actual mode, mismatched against the forced UI.
 *
 * Wrapping the app in this provides an overridden [Configuration] whose uiMode
 * matches the chosen theme, plus a matching [LocalContext], so `painterResource`
 * and friends pick the right `-night` variant. "Use system" passes the phone's
 * own value straight through, so it behaves exactly as before.
 */
@Composable
fun ForcedThemeResources(darkTheme: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val forced = remember(darkTheme, configuration) {
        Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (darkTheme) Configuration.UI_MODE_NIGHT_YES
                else Configuration.UI_MODE_NIGHT_NO
        }
    }
    // Must keep [context] as the base so the wrapper chain still reaches the
    // host Activity: locals like LocalActivityResultRegistryOwner and the back
    // dispatcher owner are resolved by walking LocalContext up to the
    // ComponentActivity. `createConfigurationContext` returns a DETACHED context
    // (no Activity in its chain), which made rememberLauncherForActivityResult
    // crash the camera/practice screens with "No ActivityResultRegistryOwner".
    // ContextThemeWrapper applies the night-mode override while preserving that
    // chain.
    val forcedContext = remember(forced) {
        ContextThemeWrapper(context, 0).apply { applyOverrideConfiguration(forced) }
    }

    CompositionLocalProvider(
        LocalConfiguration provides forced,
        LocalContext provides forcedContext,
        content = content,
    )
}

/**
 * Unwraps a possibly-wrapped Context to its Activity.
 *
 * Needed because [ForcedThemeResources] replaces `LocalContext` with a
 * configuration wrapper, so a bare `LocalContext.current as? Activity` is null.
 * Get the Activity from `LocalView.current.context` (which this walks up) when
 * you need one, e.g. to call `recreate()`.
 */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
