/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The guide screen's own theme: the parity palette plus the measured metrics.
 *
 * The guide does not take the app-wide theme, so a viewer who picked Dracula for the rest of the
 * app still gets the guide in its measured navy-and-amber look. Everything else in the app keeps
 * reading [LocalPalette], which [GuideTheme] replaces only for the content it wraps.
 */
@Composable
fun GuideTheme(
    chromeAlpha: Float = GuideMetrics.TIVIMATE_PARITY.chromeAlpha,
    content: @Composable () -> Unit,
) {
    OpenTvTheme(
        palette = AppPalette.TVIMATE_PARITY,
        guideChromeAlpha = chromeAlpha,
    ) {
        CompositionLocalProvider(
            LocalGuideMetrics provides GuideMetrics.TIVIMATE_PARITY,
        ) {
            content()
        }
    }
}
