package com.zanoni.lardr.ui.theme

import android.app.Activity
import android.os.Build
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

private val DarkColorScheme = darkColorScheme(
    primary = TomatoRedDarkTheme,
    onPrimary = Color.Black,
    secondary = TomatoOrangeDarkTheme,
    onSecondary = Color.Black,
    tertiary = TomatoGreenDarkTheme,
    onTertiary = Color.Black,
    error = ErrorRed,
    onError = Color.White,
    background = Color(0xFF1C1B1F),
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = TomatoRed,
    onPrimary = Color.Black,
    secondary = TomatoOrange,
    onSecondary = Color.Black,
    tertiary = TomatoGreen,
    onTertiary = Color.Black,
    error = ErrorRed,
    onError = Color.White,
    background = CreamAccent,
    onBackground = Color.Black,
    surface = SurfaceLight,
    onSurface = Color.Black
)

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Composable
fun LardrTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}