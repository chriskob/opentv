/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.opentv.core.AppSettings.AccentColor

val AccentColor.primary: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF26C6DA)
        AccentColor.EMERALD -> Color(0xFF2ECC71)
        AccentColor.SAPPHIRE -> Color(0xFF2979FF)
        AccentColor.AMETHYST -> Color(0xFFAB47BC)
        AccentColor.AMBER -> Color(0xFFFFA726)
    }

val AccentColor.dark: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF00838F)
        AccentColor.EMERALD -> Color(0xFF2E7D32)
        AccentColor.SAPPHIRE -> Color(0xFF1565C0)
        AccentColor.AMETHYST -> Color(0xFF7B1FA2)
        AccentColor.AMBER -> Color(0xFFE65100)
    }

val AccentColor.light: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF80DEEA)
        AccentColor.EMERALD -> Color(0xFF81C784)
        AccentColor.SAPPHIRE -> Color(0xFF82B1FF)
        AccentColor.AMETHYST -> Color(0xFFCE93D8)
        AccentColor.AMBER -> Color(0xFFFFCC80)
    }

val AccentColor.highlightGlow: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF00E5FF)
        AccentColor.EMERALD -> Color(0xFF69F0AE)
        AccentColor.SAPPHIRE -> Color(0xFF448AFF)
        AccentColor.AMETHYST -> Color(0xFFE040FB)
        AccentColor.AMBER -> Color(0xFFFFD54F)
    }

val AccentColor.cardFocusBg: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF16252C)
        AccentColor.EMERALD -> Color(0xFF132518)
        AccentColor.SAPPHIRE -> Color(0xFF141E2C)
        AccentColor.AMETHYST -> Color(0xFF241528)
        AccentColor.AMBER -> Color(0xFF281C12)
    }

val AccentColor.displayName: String
    get() = when (this) {
        AccentColor.CYAN -> "Cyan"
        AccentColor.EMERALD -> "Emerald"
        AccentColor.SAPPHIRE -> "Sapphire"
        AccentColor.AMETHYST -> "Amethyst"
        AccentColor.AMBER -> "Amber"
    }

val LocalAccentColor = staticCompositionLocalOf { AccentColor.CYAN }

object AppTheme {
    val accent: AccentColor
        @Composable
        get() = LocalAccentColor.current

    val primary: Color
        @Composable
        get() = LocalAccentColor.current.primary

    val dark: Color
        @Composable
        get() = LocalAccentColor.current.dark

    val light: Color
        @Composable
        get() = LocalAccentColor.current.light

    val highlightGlow: Color
        @Composable
        get() = LocalAccentColor.current.highlightGlow

    val cardFocusBg: Color
        @Composable
        get() = LocalAccentColor.current.cardFocusBg
}

/**
 * A deliberately dark, low-chroma palette.
 *
 * This is a living-room app: it is looked at in a dark room, from three metres away, often
 * for hours. Bright surfaces and saturated accents that read well on a phone in daylight are
 * actively unpleasant on a 55" panel at night, so everything here is anchored near-black with
 * a single restrained accent used only for focus and selection.
 */
private fun buildDarkScheme(accent: AccentColor) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Color(0xFF0D141C),
    primaryContainer = accent.dark,
    onPrimaryContainer = accent.light,
    secondary = accent.light,
    background = Color(0xFF131A22),
    onBackground = Color(0xFFECEFF1),
    surface = Color(0xFF19222B),
    onSurface = Color(0xFFECEFF1),
    surfaceVariant = Color(0xFF232D37),
    onSurfaceVariant = Color(0xFF90A4AE),
    outline = Color(0xFF171F27),
    error = Color(0xFFFF5252),
    onError = Color(0xFF1A0505),
)

/**
 * Light mode for anyone who wants it.
 */
private fun buildLightScheme(accent: AccentColor) = lightColorScheme(
    primary = accent.primary,
    onPrimary = Color.White,
    secondary = accent.light,
    background = Color(0xFFFBFBFE),
    onBackground = Color(0xFF13141A),
    surface = Color.White,
    onSurface = Color(0xFF13141A),
    surfaceVariant = Color(0xFFEEF0F6),
    onSurfaceVariant = Color(0xFF4A4F60),
    outline = Color(0xFFD3D7E2),
)

/** Scaled for TV viewing, adjusted one size smaller for sleekness and clarity. */
private val OpenTvTypography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 12.sp),
    bodySmall = TextStyle(fontSize = 11.sp),
    labelLarge = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun OpenTvTheme(
    accent: AccentColor = AccentColor.CYAN,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAccentColor provides accent,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) buildDarkScheme(accent) else buildLightScheme(accent),
            typography = OpenTvTypography,
            content = content,
        )
    }
}
