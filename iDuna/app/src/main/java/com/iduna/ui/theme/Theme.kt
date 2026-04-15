package com.iduna.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Light mode palette
val Background = Color(0xFFF5F7FB)
val Surface = Color(0xFFFFFFFF)
val SurfaceVariant = Color(0xFFE8EEF8)
val SurfaceRaised = Color(0xFFFDFEFF)
val OutlineSoft = Color(0xFFD7DFEC)
val TextPrimary = Color(0xFF0E1726)
val TextSecondary = Color(0xFF667085)

// Dark mode palette
val DarkBackground = Color(0xFF0B0F1A)
val DarkSurface = Color(0xFF111827)
val DarkSurfaceVariant = Color(0xFF1C2333)
val DarkSurfaceRaised = Color(0xFF151E2E)
val DarkOutlineSoft = Color(0xFF2A3550)
val DarkTextPrimary = Color(0xFFE8EFF8)
val DarkTextSecondary = Color(0xFF7A90B0)

// Accent colors (shared between light and dark)
val AccentRed = Color(0xFFFF355D)
val AccentBlue = Color(0xFF3CA5FF)
val AccentGreen = Color(0xFF2DE38B)
val AccentCyan = Color(0xFF3CE3FF)

private val IdunaDarkScheme = darkColorScheme(
    primary = AccentCyan,
    secondary = AccentGreen,
    tertiary = AccentRed,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    secondaryContainer = DarkSurfaceRaised,  // used as card raised surface
    outline = DarkOutlineSoft,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onTertiary = DarkBackground,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
)

private val IdunaLightScheme = lightColorScheme(
    primary = Color(0xFF0F8DA8),
    secondary = Color(0xFF168A58),
    tertiary = AccentRed,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    secondaryContainer = SurfaceRaised,      // used as card raised surface
    outline = OutlineSoft,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
)

private val IdunaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 64.sp,
        letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)

@Composable
fun IdunaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) IdunaDarkScheme else IdunaLightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = IdunaTypography,
        content = content,
    )
}
