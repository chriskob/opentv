/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.opentv.player.PlayerController
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The live preview at the top of the guide.
 *
 * As focus moves down the channel list, [row] follows it and the shared [PlayerController] tunes
 * to that channel — debounced, so surfing the list costs one real tune, the one you stop on.
 * The pane doubles as the EPG detail view: what's on now, how far through it is, what's next, and
 * a synopsis when the guide carries one — which is exactly the "info on the EPG" people ask for
 * before they'll judge a guide. Clicking the video goes full-screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun GuidePreview(
    controller: PlayerController,
    row: ChannelsViewModel.Row?,
    nowMillis: Long,
    onFullscreen: () -> Unit,
    onRefresh: () -> Unit,
    onGuideSettings: () -> Unit,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()

    Row(
        modifier
            .fillMaxWidth()
            .height(232.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ---- Video ------------------------------------------------------------------------
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .clickable(onClick = onFullscreen),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = controller.player
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
            )

            when (state) {
                is PlayerController.State.Buffering ->
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                is PlayerController.State.Error ->
                    Text(
                        "Preview unavailable",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                else -> Unit
            }

            // A quiet affordance so it reads as "press to watch", not a dead thumbnail.
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Watch", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(Modifier.width(18.dp))

        // ---- Now / next detail ------------------------------------------------------------
        Column(Modifier.weight(1f).fillMaxHeight()) {
            // Toolbar lives here so the actions are reachable without stealing guide space.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = onGuideSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Guide settings")
                }
                IconButton(onClick = onAddSource) {
                    Icon(Icons.Default.Add, contentDescription = "Add source")
                }
            }

            if (row == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    Text(
                        "Highlight a channel to preview it",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = row.primary.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    row.primary.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.variants.size > 1) {
                    Text(
                        "${row.variants.size} qualities",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val nowProg = row.now
            if (nowProg != null) {
                Text(
                    "${formatTime(nowProg.startUtcMillis)}–${formatTime(nowProg.endUtcMillis)}   ${nowProg.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { nowProg.progressAt(nowMillis) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
                nowProg.description?.takeIf { it.isNotBlank() }?.let { synopsis ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        synopsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    "No guide information for this channel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            row.next?.let { next ->
                Spacer(Modifier.weight(1f))
                Text(
                    "Next  ${formatTime(next.startUtcMillis)}   ${next.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val previewTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(utcMillis: Long): String = previewTimeFormat.format(Date(utcMillis))
