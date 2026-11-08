package com.unipoint.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Compose-driven tokens: all existing screens follow the selected appearance. */
object DuoColors {
    val Background: Color @Composable get() = MaterialTheme.colorScheme.background
    val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val SurfaceLow: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow
    val SurfaceMid: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainer
    val SurfaceHigh: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val SurfaceHighest: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
    val Outline: Color @Composable get() = MaterialTheme.colorScheme.outline
    val Hairline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val OnSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val OnSurfaceMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
    val OnAccent: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
    val AccentContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val OnAccentContainer: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer
    val AccentSoft: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val Secondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val OnSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSecondary
    val SecondaryContainer: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
    val Tertiary: Color @Composable get() = MaterialTheme.colorScheme.tertiary
    val Success: Color = Color(0xFF3A9764)
    val Warning: Color = Color(0xFFE3A84F)
    val Danger: Color @Composable get() = MaterialTheme.colorScheme.error
    val DangerContainer: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
    val OnDangerContainer: Color @Composable get() = MaterialTheme.colorScheme.onErrorContainer
    val Console: Color = Color(0xFF10151E)
    val ConsoleText: Color = Color(0xFFF0F3FC)
}

// Warm handmade Light is the screenshot reference. Midnight is the preserved dark
// theme, and Bento Clay uses the exact swatches from the supplied theme ZIP.
private val HandmadeLight = lightColorScheme(
    primary=Color(0xFFC25F38),onPrimary=Color.White,
    primaryContainer=Color(0xFFF6E5D8),onPrimaryContainer=Color(0xFF793516),
    secondary=Color(0xFF50704B),onSecondary=Color.White,
    secondaryContainer=Color(0xFFE0EAD9),onSecondaryContainer=Color(0xFF29402B),
    tertiary=Color(0xFF9A8062),onTertiary=Color.White,
    background=Color(0xFFF7F2E9),onBackground=Color(0xFF29241F),
    surface=Color(0xFFFFFCF7),onSurface=Color(0xFF29241F),
    surfaceVariant=Color(0xFFF0E9DE),onSurfaceVariant=Color(0xFF786E61),
    outline=Color(0xFF928575),outlineVariant=Color(0xFFDCD1C2),
    surfaceContainerLowest=Color(0xFFFFFCF7),surfaceContainerLow=Color(0xFFF7F0E5),
    surfaceContainer=Color(0xFFF2EADD),surfaceContainerHigh=Color(0xFFEBE1D3),
    surfaceContainerHighest=Color(0xFFE4D8C8),
    error=Color(0xFFB5342C),onError=Color.White,
    errorContainer=Color(0xFFFBE1DE),onErrorContainer=Color(0xFF731D19)
)
private val GraphiteDark = darkColorScheme(
    primary=Color(0xFF8AA4FF),onPrimary=Color(0xFF0B1230),
    primaryContainer=Color(0xFF26325E),onPrimaryContainer=Color(0xFFDCE3FF),
    secondary=Color(0xFF7FD6C8),onSecondary=Color(0xFF06201C),
    secondaryContainer=Color(0xFF1B3B38),onSecondaryContainer=Color(0xFFCDF3EC),
    tertiary=Color(0xFFE3C48A),onTertiary=Color(0xFF2D2008),
    background=Color(0xFF0B0D12),onBackground=Color(0xFFEAEDF5),
    surface=Color(0xFF10131A),onSurface=Color(0xFFEAEDF5),
    surfaceVariant=Color(0xFF1F2531),onSurfaceVariant=Color(0xFF9AA3B5),
    outline=Color(0xFF384156),outlineVariant=Color(0xFF262D3B),
    surfaceContainerLowest=Color(0xFF0B0D12),surfaceContainerLow=Color(0xFF141821),
    surfaceContainer=Color(0xFF181D27),surfaceContainerHigh=Color(0xFF1F2531),
    surfaceContainerHighest=Color(0xFF282F3D),
    error=Color(0xFFFF7A87),onError=Color(0xFF3A0A10),
    errorContainer=Color(0xFF4A1D24),onErrorContainer=Color(0xFFFFD9DC)
)
private val BentoClayLight = lightColorScheme(
    primary=ClayPrimary,onPrimary=Color.White,
    primaryContainer=ClaySurfaceVariant,onPrimaryContainer=ClayTextPrimary,
    secondary=ClaySecondary,onSecondary=Color.White,
    background=ClayBackground,onBackground=ClayTextPrimary,
    surface=ClaySurface,onSurface=ClayTextPrimary,
    surfaceVariant=ClaySurfaceVariant,onSurfaceVariant=ClayTextSecondary,
    outline=Color(0xFF9BA8BB),outlineVariant=ClayShadowDark,
    surfaceContainerLowest=ClaySurface,surfaceContainerLow=Color(0xFFF7F9FC),
    surfaceContainer=ClayBackground,surfaceContainerHigh=ClaySurfaceVariant,
    surfaceContainerHighest=Color(0xFFD8E2F0)
)
private val DuoShapes = Shapes(
    extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),
    medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp),
    extraLarge=RoundedCornerShape(28.dp)
)

/** Supported presets: light (mock), dark (graphite), bento (uploaded clay palette). */
@Composable
fun UniPointTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    appearance: String = if (darkTheme) "dark" else "light",
    content: @Composable () -> Unit
) {
    val scheme: ColorScheme = when(appearance){
        "dark" -> GraphiteDark
        "bento" -> BentoClayLight
        else -> HandmadeLight
    }
    MaterialTheme(colorScheme=scheme,typography=Typography,shapes=DuoShapes,content=content)
}

@Composable
fun DuoPadTheme(content: @Composable () -> Unit) {
    UniPointTheme(appearance="bento",content=content)
}
