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
    primary = KinetixIndigo,
    onPrimary = KinetixWhite,
    primaryContainer = KinetixIndigo10,
    onPrimaryContainer = KinetixInk,

    secondary = KinetixNavy,
    onSecondary = KinetixWhite,
    secondaryContainer = KinetixNavy10,
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
    primary = KinetixIndigoLight,
    onPrimary = KinetixWhite,
    primaryContainer = KinetixIndigo,
    onPrimaryContainer = KinetixWhite,

    secondary = KinetixMint,
    onSecondary = KinetixInk,
    secondaryContainer = KinetixDarkSurfaceVariant,
    onSecondaryContainer = KinetixMint,

    tertiary = KinetixGreen,
    onTertiary = KinetixWhite,
    tertiaryContainer = Color(0xFF1A3A30),
    onTertiaryContainer = KinetixGreen,

    background = KinetixDarkBackground,
    onBackground = KinetixWhite,
    surface = KinetixDarkSurface,
    onSurface = KinetixWhite,
    surfaceVariant = KinetixDarkSurfaceVariant,
    onSurfaceVariant = KinetixDarkMuted,
    outline = KinetixDarkOutline,
    outlineVariant = Color(0xFF35375F),

    error = KinetixError,
    onError = KinetixWhite,
    errorContainer = Color(0xFF3D1C19),
    onErrorContainer = Color(0xFFFFB4AB),
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