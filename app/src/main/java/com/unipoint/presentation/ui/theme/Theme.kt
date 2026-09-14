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

private val Navy950 = Color(0xFF07111F)
private val Navy900 = Color(0xFF0B1728)
private val Navy800 = Color(0xFF10243A)
private val Slate = Color(0xFF9FB0C5)
private val White = Color(0xFFFBFCFD)
private val Teal = Color(0xFF18D6C2)
private val Cyan = Color(0xFF42C8FF)
private val Amber = Color(0xFFFFB84D)
private val Red = Color(0xFFFF6B7A)

private val DarkColorScheme = darkColorScheme(
    primary = Teal,
    secondary = Cyan,
    tertiary = Amber,
    error = Red,
    background = Navy950,
    surface = Navy900,
    surfaceVariant = Navy800,
    onPrimary = Color(0xFF00251F),
    onSecondary = Color(0xFF001D29),
    onBackground = White,
    onSurface = White,
    onSurfaceVariant = Slate
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF008C91),
    secondary = Color(0xFF00739E),
    tertiary = Color(0xFF9A5A00),
    error = Color(0xFFB3261E),
    background = Color(0xFFFBFCFD),
    surface = Color.White,
    surfaceVariant = Color(0xFFE8EEF5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF101820),
    onSurface = Color(0xFF101820),
    onSurfaceVariant = Color(0xFF536477)
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
