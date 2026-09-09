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
        AccentColor.CYAN -> Color(0xFF22D3EE)
        AccentColor.EMERALD -> Color(0xFF34D399)
        AccentColor.SAPPHIRE -> Color(0xFF60A5FA)
        AccentColor.AMETHYST -> Color(0xFFA78BFA)
        AccentColor.AMBER -> Color(0xFFFBBF24)
    }

val AccentColor.dark: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF0891B2)
        AccentColor.EMERALD -> Color(0xFF059669)
        AccentColor.SAPPHIRE -> Color(0xFF2563EB)
        AccentColor.AMETHYST -> Color(0xFF7C3AED)
        AccentColor.AMBER -> Color(0xFFD97706)
    }

val AccentColor.light: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFFA5F3FC)
        AccentColor.EMERALD -> Color(0xFFA7F3D0)
        AccentColor.SAPPHIRE -> Color(0xFFBFDBFE)
        AccentColor.AMETHYST -> Color(0xFFDDD6FE)
        AccentColor.AMBER -> Color(0xFFFDE68A)
    }

val AccentColor.highlightGlow: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF67E8F9)
        AccentColor.EMERALD -> Color(0xFF6EE7B7)
        AccentColor.SAPPHIRE -> Color(0xFF93C5FD)
        AccentColor.AMETHYST -> Color(0xFFC4B5FD)
        AccentColor.AMBER -> Color(0xFFFCD34D)
    }

val AccentColor.cardFocusBg: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF12262E)
        AccentColor.EMERALD -> Color(0xFF12261E)
        AccentColor.SAPPHIRE -> Color(0xFF131D2F)
        AccentColor.AMETHYST -> Color(0xFF231B31)
        AccentColor.AMBER -> Color(0xFF2A2110)
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
