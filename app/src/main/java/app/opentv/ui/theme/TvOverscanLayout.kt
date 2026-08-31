/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 10-foot TV Layout Tokens and Overscan Safe Padding utilities for Android TV and Fire TV.
 *
 * Traditional and budget television panels often apply a 3-5% overscan crop at the physical
 * display edges. To ensure critical UI controls, video preview bounds, and text are never clipped,
 * all primary TV views align within these standard safe zones.
 */
object TvOverscanDefaults {
    /** Standard horizontal safe margin (5% of 1080p canvas = 54-58dp). */
    val HorizontalMargin: Dp = 58.dp

    /** Standard vertical safe margin (5% of 1080p canvas = 32-36dp). */
    val VerticalMargin: Dp = 32.dp

    /** Compact horizontal safe margin for overlay panels. */
    val CompactHorizontalMargin: Dp = 36.dp

    /** Compact vertical safe margin for overlay panels. */
    val CompactVerticalMargin: Dp = 24.dp

    /** 16:9 Standard Mini-Player Dimensions for TV Guides. */
    val MiniPlayerWidth: Dp = 420.dp
    val MiniPlayerHeight: Dp = 236.dp
    val MiniPlayerCornerRadius: Dp = 12.dp
    val MiniPlayerBorderWidth: Dp = 2.dp
}

/**
 * Applies standard 5% TV overscan padding to a container.
 */
@Composable
fun Modifier.tvSafeOverscanPadding(
    horizontalFraction: Float = 0.05f,
    verticalFraction: Float = 0.05f,
): Modifier {
    val configuration = LocalConfiguration.current
    val horizontal = (configuration.screenWidthDp * horizontalFraction).dp
    val vertical = (configuration.screenHeightDp * verticalFraction).dp
    return this.padding(horizontal = horizontal, vertical = vertical)
}

/**
 * Returns safe [PaddingValues] derived from the current screen configuration.
 */
@Composable
fun rememberTvSafePaddingValues(
    horizontalMargin: Dp = TvOverscanDefaults.HorizontalMargin,
    verticalMargin: Dp = TvOverscanDefaults.VerticalMargin,
): PaddingValues = PaddingValues(
    start = horizontalMargin,
    top = verticalMargin,
    end = horizontalMargin,
    bottom = verticalMargin,
)
