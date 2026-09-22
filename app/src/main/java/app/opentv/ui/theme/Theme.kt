/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The palette every screen reads from. It carries more roles than Material3 does — guide chrome,
 * recording/favourite/live semantics, the focus fill — so these live here rather than being passed
 * around or, worse, re-hardcoded at each call site.
 */
val LocalPalette = staticCompositionLocalOf { AppPalette.TVPLAYER.palette() }

/**
 * How translucent the guide's own chrome is — 1f leaves the old solid panels. TiviMate ships the
 * same knob as "User interface transparency", and the 0.7 it defaults to is why its guide reads as
 * a frame around the picture rather than one flat slab. Only the fills fade: focus rings, borders
 * and text stay opaque so the cursor is never the thing that gets harder to see.
 */
val LocalGuideChromeAlpha = staticCompositionLocalOf { 1f }

/**
 * Palette accessors. [primary]/[dark]/[light]/[cardFocusBg] keep the names the old accent API used
 * so existing call sites did not have to move when accents were replaced by themes.
 */
object AppTheme {
    val palette: OpenTvPalette
        @Composable
        get() = LocalPalette.current

    val primary: Color
        @Composable
        get() = LocalPalette.current.primary

    val dark: Color
        @Composable
        get() = LocalPalette.current.primaryHover

    val light: Color
        @Composable
        get() = LocalPalette.current.primaryActive

    val cardFocusBg: Color
        @Composable
        get() = LocalPalette.current.cardFocusBg
}

/**
 * Builds the Material3 scheme from the selected palette so the ~250 `MaterialTheme.colorScheme.*`
 * call sites across the app re-theme with no per-screen work.
 */
private fun buildDarkScheme(p: OpenTvPalette) = darkColorScheme(
    primary = p.primary,
    onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer,
    onPrimaryContainer = p.onPrimaryContainer,
    secondary = p.primaryActive,
    onSecondary = p.onPrimary,
    secondaryContainer = p.cardFocusBg,
    onSecondaryContainer = p.primaryActive,
    tertiary = p.info,
    onTertiary = p.onPrimary,
    tertiaryContainer = p.cardFocusBg,
    onTertiaryContainer = p.info,
    background = p.background,
    onBackground = p.onSurface,
    surface = p.surface,
    onSurface = p.onSurface,
    surfaceVariant = p.surfaceVariant,
    onSurfaceVariant = p.onSurfaceVariant,
    surfaceContainerLowest = p.surfaceContainerLowest,
    surfaceContainerLow = p.surfaceContainerLow,
    surfaceContainer = p.surfaceContainer,
    surfaceContainerHigh = p.surfaceContainerHigh,
    surfaceContainerHighest = p.surfaceContainerHighest,
    outline = p.outline,
    outlineVariant = p.outlineVariant,
    error = p.error,
    onError = p.onPrimary,
    errorContainer = p.errorContainer,
    onErrorContainer = p.error,
    inverseSurface = p.onSurface,
    inverseOnSurface = p.background,
    inversePrimary = p.primaryHover,
)

/**
 * Mirrors the reference's type scale: its dimens set the text sizes at 11, 12, 13, 14, 16, 18,
 * 20, 22, 24, 28, 34 and 44 sp, mapped here onto the fifteen Material3 roles.
 */
private val OpenTvTypography = Typography(
    displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold),
    displayMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
    displaySmall = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun OpenTvTheme(
    palette: AppPalette = AppPalette.TVPLAYER,
    guideChromeAlpha: Float = 1f,
    content: @Composable () -> Unit,
) {
    val resolved = palette.palette()
    CompositionLocalProvider(
        LocalPalette provides resolved,
        LocalGuideChromeAlpha provides guideChromeAlpha,
    ) {
        MaterialTheme(
            colorScheme = buildDarkScheme(resolved),
            typography = OpenTvTypography,
            content = content,
        )
    }
}
