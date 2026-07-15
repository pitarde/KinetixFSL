package com.example.kinetixfsl.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = KinetixNavy,
    onPrimary = KinetixWhite,
    primaryContainer = KinetixNavy10,
    onPrimaryContainer = KinetixInk,

    secondary = KinetixIndigo,
    onSecondary = KinetixWhite,
    secondaryContainer = KinetixIndigo10,
    onSecondaryContainer = KinetixInk,

    tertiary = KinetixGreen,
    onTertiary = KinetixWhite,
    tertiaryContainer = KinetixMint20,
    onTertiaryContainer = KinetixInk,

    background = KinetixWhite,
    onBackground = KinetixInk,
    surface = KinetixWhite,
    onSurface = KinetixInk,
    surfaceVariant = KinetixSurface,
    onSurfaceVariant = KinetixMuted,
    outline = KinetixOutline,

    error = KinetixError,
    onError = KinetixWhite,
    errorContainer = KinetixError10,
    onErrorContainer = KinetixError,
)

private val DarkColors = darkColorScheme(
    primary = KinetixMint,
    onPrimary = KinetixInk,
    primaryContainer = KinetixIndigo,
    onPrimaryContainer = KinetixWhite,

    secondary = KinetixIndigo,
    onSecondary = KinetixWhite,

    tertiary = KinetixGreen,
    onTertiary = KinetixWhite,

    background = KinetixDarkBackground,
    onBackground = KinetixWhite,
    surface = KinetixDarkSurface,
    onSurface = KinetixWhite,
    onSurfaceVariant = KinetixMint,
    outline = KinetixMuted,

    error = KinetixError,
    onError = KinetixWhite,
)

@Composable
fun KinetixFSLTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Transparent bars; our screens draw edge to edge underneath them.
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = KinetixTypography,
        content = content,
    )
}