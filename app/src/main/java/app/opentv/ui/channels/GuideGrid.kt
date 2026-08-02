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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    selectedKey: Any?,
    onSelectRow: (ChannelsViewModel.Row) -> Unit,
    onFocusRow: (ChannelsViewModel.Row) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val now = System.currentTimeMillis()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Two hours per press: enough to move through an evening quickly, small enough not to skip
    // past a programme you were aiming for.
    val pagePx = remember(density) { with(density) { (2 * 60 * MINUTE_DP).dp.toPx() }.toInt() }

    Column(modifier.fillMaxSize()) {
        // The programme strip scrolls horizontally, but a d-pad has nothing focusable out there
        // to carry it — so these buttons page the whole timeline (header and every row together)
        // forward and back through the guide.
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageButton(icon = Icons.Filled.ChevronLeft, label = "Earlier") {
                coroutineScope.launch {
                    scroll.animateScrollTo((scroll.value - pagePx).coerceAtLeast(0))
                }
            }
            PageButton(label = "Now") {
                coroutineScope.launch { scroll.animateScrollTo(0) }
            }
            PageButton(icon = Icons.Filled.ChevronRight, label = "Later", iconTrailing = true) {
                coroutineScope.launch {
                    scroll.animateScrollTo((scroll.value + pagePx).coerceAtMost(scroll.maxValue))
                }
            }
        }

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
                    isSelected = row.key == selectedKey,
                    onSelect = { onSelectRow(row) },
                    onFocus = { onFocusRow(row) },
                )
            }
        }
    }
}

@Composable
private fun PageButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTrailing: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .then(
                if (focused) Modifier.border(
                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp),
                ) else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (focused) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface
        if (icon != null && !iconTrailing) {
            Icon(icon, contentDescription = null, tint = tint)
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        if (icon != null && iconTrailing) {
            Spacer(Modifier.width(4.dp))
            Icon(icon, contentDescription = null, tint = tint)
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
    isSelected: Boolean,
    onSelect: () -> Unit,
    onFocus: () -> Unit,
) {
    // Focus (the d-pad border) just moves the highlight. Selection (the filled cell) is the
    // channel the preview is playing — it only changes when you press OK.
    var focused by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {

        // ---- Fixed channel cell (does not scroll) --------------------------------------
        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                )
                .then(
                    if (focused) Modifier.border(
                        2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp),
                    ) else Modifier,
                )
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .clickable(onClick = onSelect)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.primary.number?.let { num ->
                Text(
                    "$num",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(28.dp),
                )
            }
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
                // A programme almost over (or a very short one) would otherwise be a sliver too
                // thin to read its name — the "Poi n..." problem. We floor each block at
                // [MIN_BLOCK_WIDTH] so the title stays legible, then carry the borrowed width as
                // `debt` and repay it out of the following gaps, so everything downstream stays
                // aligned with the time header instead of drifting right.
                var debt = 0.dp
                for (programme in programmes) {
                    // Clamp to the window's left edge; a programme that started earlier is
                    // drawn from `windowStart` so its block does not push everything right.
                    val start = programme.startUtcMillis.coerceAtLeast(windowStartMillis)
                    val gap = widthFor(cursor, start)
                    val repaid = minOf(debt, gap)
                    debt -= repaid
                    val spacer = gap - repaid
                    if (spacer > 0.dp) {
                        Spacer(Modifier.width(spacer))
                    }
                    val end = programme.endUtcMillis
                    val isNow = nowMillis in programme.startUtcMillis until end
                    val trueWidth = widthFor(start, end)
                    val drawnWidth = maxOf(trueWidth, MIN_BLOCK_WIDTH)
                    debt += drawnWidth - trueWidth
                    ProgrammeBlock(
                        title = programme.title,
                        width = drawnWidth,
                        isNow = isNow,
                        progress = if (isNow) programme.progressAt(nowMillis) else 0f,
                    )
                    cursor = end
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(title: String, width: Dp, isNow: Boolean, progress: Float) {
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
            ),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isNow) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp),
        )
        // How far through the current programme we are — a live fill along the bottom edge.
        if (isNow && progress > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
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
// Floor for a programme block so a nearly-finished or very short show still shows its name.
private val MIN_BLOCK_WIDTH = 72.dp
private val HALF_HOUR_WIDTH: Dp = (30 * MINUTE_DP).dp

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
