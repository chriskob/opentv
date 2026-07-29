/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opentv.data.model.Channel
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The programme guide: channels down the left, a scrolling time-line to the right, with
 * each programme drawn as a block whose width is its duration. This is the "grid" a TV guide
 * is supposed to be — you can see what is on now, what is next, and read across the evening.
 *
 * ## How it lays out without a custom Layout
 *
 * Every row and the time header share one [horizontalScroll] state, so scrolling any of them
 * scrolls all of them in lock-step and the columns stay time-aligned. Within a row, blocks
 * are placed left to right at [MINUTE] width per minute; a leading spacer covers any gap
 * before the first programme, and gaps between programmes get their own spacer. No absolute
 * positioning, no measuring pass — just widths, which is cheap enough for a lazy list of
 * hundreds of channels on a weak TV box.
 */
@Composable
fun GuideGrid(
    rows: List<ChannelsViewModel.Row>,
    windowStartMillis: Long,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val now = System.currentTimeMillis()

    Column(modifier.fillMaxSize()) {
        TimeHeader(windowStartMillis, scroll)

        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                GuideRow(
                    row = row,
                    windowStartMillis = windowStartMillis,
                    nowMillis = now,
                    scroll = scroll,
                    onPlay = { onPlay(row.primary) },
                )
            }
        }
    }
}

@Composable
private fun TimeHeader(windowStartMillis: Long, scroll: androidx.compose.foundation.ScrollState) {
    Row(Modifier.fillMaxWidth().height(28.dp)) {
        // Empty corner above the channel column.
        Box(Modifier.width(CHANNEL_COLUMN))
        Row(
            Modifier
                .horizontalScroll(scroll)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // A label every half hour across the whole window.
            repeat(HOURS_IN_WINDOW * 2) { i ->
                val slotStart = windowStartMillis + i * HALF_HOUR_MS
                Box(
                    Modifier.width(HALF_HOUR_WIDTH).height(28.dp).padding(start = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        clockFormat.format(Date(slotStart)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    row: ChannelsViewModel.Row,
    windowStartMillis: Long,
    nowMillis: Long,
    scroll: androidx.compose.foundation.ScrollState,
    onPlay: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {

        // ---- Fixed channel cell (does not scroll) --------------------------------------
        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onPlay)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = row.primary.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    row.primary.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.variants.size > 1) {
                    Text(
                        "${row.variants.size} qualities",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.width(3.dp))

        // ---- Scrolling programme strip -------------------------------------------------
        Row(Modifier.horizontalScroll(scroll)) {
            val programmes = row.programmes
            if (programmes.isEmpty()) {
                // Honest placeholder spanning the window, so a guide-less channel reads as
                // "no guide" rather than as a suspiciously empty gap.
                Box(
                    Modifier
                        .width(HALF_HOUR_WIDTH * HOURS_IN_WINDOW.toFloat() * 2)
                        .fillMaxSize()
                        .padding(end = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "  No guide information",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                var cursor = windowStartMillis
                for (programme in programmes) {
                    // Clamp to the window's left edge; a programme that started earlier is
                    // drawn from `windowStart` so its block does not push everything right.
                    val start = programme.startUtcMillis.coerceAtLeast(windowStartMillis)
                    if (start > cursor) {
                        Spacer(Modifier.width(widthFor(cursor, start)))
                    }
                    val end = programme.endUtcMillis
                    val isNow = nowMillis in programme.startUtcMillis until end
                    ProgrammeBlock(
                        title = programme.title,
                        width = widthFor(start, end),
                        isNow = isNow,
                    )
                    cursor = end
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(title: String, width: Dp, isNow: Boolean) {
    Box(
        Modifier
            .width(width)
            .fillMaxSize()
            .padding(end = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isNow) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .then(
                if (isNow) Modifier.border(
                    1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp),
                ) else Modifier,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isNow) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Minutes between two instants, as guide width. Never negative; tiny slots stay legible. */
private fun widthFor(fromMillis: Long, toMillis: Long): Dp {
    val minutes = ((toMillis - fromMillis) / 60_000L).coerceAtLeast(0)
    return (minutes * MINUTE_DP).dp
}

// A weak TV box will happily scroll this; the whole strip is ~12h * 60 * 4dp = ~2880dp wide.
private const val MINUTE_DP = 4f
private const val HOURS_IN_WINDOW = 12
private const val HALF_HOUR_MS = 30 * 60 * 1000L
private val CHANNEL_COLUMN = 220.dp
private val ROW_HEIGHT = 64.dp
private val HALF_HOUR_WIDTH: Dp = (30 * MINUTE_DP).dp

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
