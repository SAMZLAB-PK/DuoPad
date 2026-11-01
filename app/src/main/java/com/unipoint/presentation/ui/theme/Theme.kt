package com.unipoint.presentation.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Concept 04: neutral ink, warm-white backgrounds and soft-gray controls.
private val Ink = Color(0xFF181818)
private val WarmWhite = Color(0xFFFCFAF8)
private val Paper = Color(0xFFFFFFFF)
private val SoftGray = Color(0xFFF3F0EC)
private val GrayText = Color(0xFF686663)
private val DividerGray = Color(0xFFE6E1DC)

private val DarkColorScheme = darkColorScheme(
    primary = Color.White, onPrimary = Ink,
    secondary = Color.White, onSecondary = Ink,
    background = Ink, onBackground = Color.White,
    surface = Ink, onSurface = Color.White,
    surfaceVariant = Color(0xFF292929),
    onSurfaceVariant = Color(0xFFD5D5D5)
)
private val LightColorScheme = lightColorScheme(
    primary = Ink, onPrimary = Color.White,
    primaryContainer = SoftGray, onPrimaryContainer = Ink,
    secondary = Color(0xFF3B3938), onSecondary = Color.White,
    secondaryContainer = SoftGray, onSecondaryContainer = Ink,
    tertiary = Ink, onTertiary = Color.White,
    error = Color(0xFFB3261E),
    background = WarmWhite, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = SoftGray, onSurfaceVariant = GrayText,
    outline = DividerGray, outlineVariant = DividerGray
)

@Composable
fun UniPointTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false, // We prefer our branded colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
