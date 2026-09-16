/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opentv.ui.theme.AppTheme

/**
 * The OpenTV mark, drawn from the active palette.
 *
 * The vector it replaces (`ic_opentv_logo.xml`) baked in one brand blue, so on every theme but the
 * default the mark next to the "OpenTV" wordmark clashed with the accent the wordmark itself was
 * using. Drawing it here means the whole lockup follows the theme — screen in the accent, play mark
 * in the accent's foreground ink.
 *
 * The geometry mirrors the original 48dp viewport so the shape does not change, only the colour.
 * The notification small icon still uses the vector, since the system tints that silhouette white.
 */
@Composable
fun OpenTvLogo(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val screen = AppTheme.primary
    val play = AppTheme.palette.onPrimary
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 48f

        // The screen: a rounded panel in the accent.
        drawPath(
            path = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 2f * s,
                        top = 7f * s,
                        right = 46f * s,
                        bottom = 41f * s,
                        cornerRadius = CornerRadius(7f * s, 7f * s),
                    ),
                )
            },
            color = screen,
        )

        // The play mark, so it reads as a player at a glance.
        drawPath(
            path = Path().apply {
                moveTo(19f * s, 16f * s)
                lineTo(34f * s, 24f * s)
                lineTo(19f * s, 32f * s)
                close()
            },
            color = play,
        )

        // A little stand foot, so the panel reads as a TV.
        drawRect(
            color = screen,
            topLeft = Offset(17f * s, 43f * s),
            size = Size(14f * s, 2.5f * s),
        )
    }
}
