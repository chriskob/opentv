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
        AccentColor.AZURE -> Color(0xFF4D9FFF)
        AccentColor.IRIS -> Color(0xFFA98BFF)
        AccentColor.JADE -> Color(0xFF34D399)
        AccentColor.EMBER -> Color(0xFFFFA23A)
    }

val AccentColor.dark: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF0891B2)
        AccentColor.AZURE -> Color(0xFF2563EB)
        AccentColor.IRIS -> Color(0xFF7C3AED)
        AccentColor.JADE -> Color(0xFF059669)
        AccentColor.EMBER -> Color(0xFFC2620E)
    }

val AccentColor.light: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFFA5F3FC)
        AccentColor.AZURE -> Color(0xFFBFDBFE)
        AccentColor.IRIS -> Color(0xFFDDD6FE)
        AccentColor.JADE -> Color(0xFFA7F3D0)
        AccentColor.EMBER -> Color(0xFFFED7AA)
    }

val AccentColor.highlightGlow: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF67E8F9)
        AccentColor.AZURE -> Color(0xFF93C5FD)
        AccentColor.IRIS -> Color(0xFFC4B5FD)
        AccentColor.JADE -> Color(0xFF6EE7B7)
        AccentColor.EMBER -> Color(0xFFFDBA74)
    }

val AccentColor.cardFocusBg: Color
    get() = when (this) {
        AccentColor.CYAN -> Color(0xFF12262E)
        AccentColor.AZURE -> Color(0xFF131F36)
        AccentColor.IRIS -> Color(0xFF211536)
        AccentColor.JADE -> Color(0xFF12261E)
        AccentColor.EMBER -> Color(0xFF2D1C0E)
    }

val AccentColor.displayName: String
    get() = when (this) {
        AccentColor.CYAN -> "Cyan"
        AccentColor.AZURE -> "Azure"
        AccentColor.IRIS -> "Iris"
        AccentColor.JADE -> "Jade"
        AccentColor.EMBER -> "Ember"
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
 * A deliberately dark, low-chroma palette with a real elevation ramp.
 *
 * This is a living-room app: it is looked at in a dark room, from three metres away, often
 * for hours. Bright surfaces and saturated accents that read well on a phone in daylight are
 * actively unpleasant on a 55" panel at night, so everything here is anchored near-black with
 * a single restrained accent used only for focus and selection.
 *
 * The old scheme packed background, surface and surfaceVariant into a few steps and made
 * `outline` *darker* than the surface it bordered, so nothing read as raised. The roles below
 * form a proper ladder — page (darkest) → surface → surfaceContainer…Highest (lightest) — and
 * `outline`/`outlineVariant` are now lighter than the fills they sit on, which is what lets a
 * card read as a card. Existing call sites that used `surface`/`surfaceVariant` keep working;
 * only the tonal spacing changed.
 */
private fun buildDarkScheme(accent: AccentColor) = darkColorScheme(
    primary = accent.primary,
    onPrimary = Color(0xFF04121A),
    primaryContainer = accent.dark,
    onPrimaryContainer = accent.light,
    secondary = accent.light,
    onSecondary = Color(0xFF04121A),
    secondaryContainer = accent.cardFocusBg,
    onSecondaryContainer = accent.light,
    background = Color(0xFF0A0F15),
    onBackground = Color(0xFFE8EEF4),
    surface = Color(0xFF10171E),
    onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF1B2530),
    onSurfaceVariant = Color(0xFF9FB0BE),
    surfaceContainerLowest = Color(0xFF0A0F15),
    surfaceContainerLow = Color(0xFF111922),
    surfaceContainer = Color(0xFF141E27),
    surfaceContainerHigh = Color(0xFF18222C),
    surfaceContainerHighest = Color(0xFF1F2B36),
    outline = Color(0xFF4A5B6C),
    outlineVariant = Color(0xFF263442),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1416),
    onErrorContainer = Color(0xFFFFDAD8),
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
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAccentColor provides accent,
    ) {
        MaterialTheme(
            colorScheme = buildDarkScheme(accent),
            typography = OpenTvTypography,
            content = content,
        )
    }
}
