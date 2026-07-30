/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The detail pane at the top of the guide.
 *
 * As focus moves down the channel list, [row] follows it and the pane shows that channel's
 * logo alongside what's on now, how far through it is, what's next, and a synopsis when the
 * guide carries one — the "info on the EPG" people ask for before they'll judge a guide.
 * Pressing it plays the channel full-screen.
 *
 * ## Why no inline video here
 * An earlier version auto-played the highlighted channel as a live thumbnail. On low-end TV
 * boxes (a Chromecast, a cheap stick) running a second video decoder behind the UI floods the
 * codec and locks the whole app up. A logo-and-info pane gives the same "what am I about to
 * watch" answer with none of that risk; the one player the device can handle lives full-screen.
 */
@Composable
fun GuidePreview(
    row: ChannelsViewModel.Row?,
    nowMillis: Long,
    onWatch: () -> Unit,
    onRefresh: () -> Unit,
    onGuideSettings: () -> Unit,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(212.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // ---- Logo / "watch" card ----------------------------------------------------------
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .clickable(onClick = onWatch),
            contentAlignment = Alignment.Center,
        ) {
            if (row != null) {
                AsyncImage(
                    model = row.primary.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.6f),
                )
            }
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
                        "Highlight a channel to see what's on",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
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
