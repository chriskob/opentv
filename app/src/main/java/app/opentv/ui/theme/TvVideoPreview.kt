/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/**
 * The 16:9 preview pane that keeps a live stream rendering *behind* the guide.
 *
 * This is the non-destructive-overlay pattern in miniature: the [PlayerView] is created once and
 * stays in composition, the video keeps decoding (muted) while the EPG draws over it, and the only
 * thing that changes when the guide opens is the scale via [VideoGuideShell]. No re-initializing,
 * no second decoder.
 *
 * [previewPlayer] is the same shared `livePlayer` from the service locator that full-screen uses,
 * so backing out of full-screen into the guide resumes the stream with zero buffering — the exact
 * seamlessness TiviMate is known for.
 */
@Composable
fun TvVideoPreview(
    previewPlayer: androidx.media3.exoplayer.ExoPlayer?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (previewPlayer != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    (android.view.LayoutInflater.from(ctx).inflate(app.opentv.R.layout.view_player, null) as PlayerView).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        useController = false
                        player = previewPlayer
                    }
                },
                update = { pv ->
                    if (pv.player != previewPlayer) pv.player = previewPlayer
                    pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
            )
        } else if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.6f),
            )
        }
    }
}