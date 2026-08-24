/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.shownName
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideGrid(
    rows: List<ChannelsViewModel.Row>,
    windowStartMillis: Long,
    selectedKey: Any?,
    onSelectRow: (ChannelsViewModel.Row) -> Unit,
    onFocusRow: (ChannelsViewModel.Row) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onProgramme: (ChannelsViewModel.Row, Programme) -> Unit = { _, _ -> },
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onExitLeftFromChannel: () -> Boolean = { false },
    dayOffset: Int = 0,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val now = System.currentTimeMillis()
    androidx.compose.runtime.LaunchedEffect(dayOffset) { scroll.scrollTo(0) }

    androidx.compose.runtime.LaunchedEffect(selectedKey) {
        val index = rows.indexOfFirst { it.key == selectedKey }
        if (index >= 0) {
            val target = (index - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Column(modifier.fillMaxSize()) {
        TimeHeader(windowStartMillis, now, scroll)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                GuideRow(
                    row = row,
                    windowStartMillis = windowStartMillis,
                    nowMillis = now,
                    scroll = scroll,
                    isSelected = row.key == selectedKey,
                    onSelect = { onSelectRow(row) },
                    onLongSelect = { onLongSelectRow(row) },
                    onFocus = { onFocusRow(row) },
                    onProgramme = { programme -> onProgramme(row, programme) },
                    onToggleFavourite = { onToggleFavourite(row) },
                    onExitLeft = onExitLeftFromChannel,
                )
            }
        }
    }
}

/**
 * The live channels as a plain vertical list instead of the time-grid.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelList(
    rows: List<ChannelsViewModel.Row>,
    selectedKey: Any?,
    onSelectRow: (ChannelsViewModel.Row) -> Unit,
    onFocusRow: (ChannelsViewModel.Row) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onExitLeftFromChannel: () -> Boolean = { false },
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(selectedKey) {
        val index = rows.indexOfFirst { it.key == selectedKey }
        if (index >= 0) {
            val target = (index - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            ChannelListRow(
                row = row,
                nowMillis = now,
                isSelected = row.key == selectedKey,
                onSelect = { onSelectRow(row) },
                onLongSelect = { onLongSelectRow(row) },
                onFocus = { onFocusRow(row) },
                onToggleFavourite = { onToggleFavourite(row) },
                onExitLeft = onExitLeftFromChannel,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListRow(
    row: ChannelsViewModel.Row,
    nowMillis: Long,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit = {},
    onFocus: () -> Unit,
    onToggleFavourite: () -> Unit,
    onExitLeft: () -> Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val isLive = isSelected

    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                if (focused) Color(0xFFF0F4F8)
                else if (isSelected) Color(0xFF1E2F3E)
                else Color(0xFF18222C),
            )
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) onExitLeft() else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .combinedClickable(
                onClick = onSelect,
                onLongClick = onLongSelect,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.primary.number?.let { num ->
            Text(
                "$num",
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) Color(0xFF37474F) else Color(0xFF78909C),
                maxLines = 1,
                modifier = Modifier.width(28.dp),
            )
        }
        AsyncImage(
            model = row.primary.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.primary.shownName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isLive || focused) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (focused) Color(0xFF10171E) else if (isLive) Color(0xFF26C6DA) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isLive) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Live",
                        tint = if (focused) Color(0xFF00838F) else Color(0xFF26C6DA),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val nowProg = row.now
            if (nowProg != null) {
                Text(
                    text = "${clockFormat.format(Date(nowProg.startUtcMillis))}  ${nowProg.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (focused) Color(0xFF455A64) else Color(0xFF90A4AE),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TimeHeader(
    windowStartMillis: Long,
    nowMillis: Long,
    scroll: androidx.compose.foundation.ScrollState,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val is24 = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val currentDateTimeFmt = remember(is24) {
        if (is24) SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault())
        else SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())
    }
    val slotTimeFmt = remember(is24) {
        if (is24) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(Color(0xFF141C24)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Top-left current date/time label (Cyan, matching TiviMate)
        Box(
            Modifier
                .width(CHANNEL_COLUMN)
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = currentDateTimeFmt.format(Date(nowMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF26C6DA),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        // Timeline slots
        Row(
            Modifier
                .horizontalScroll(scroll)
                .background(Color(0xFF141C24)),
        ) {
            repeat(HOURS_IN_WINDOW * 2) { i ->
                val slotStart = windowStartMillis + i * HALF_HOUR_MS
                Box(
                    Modifier
                        .width(HALF_HOUR_WIDTH)
                        .height(26.dp)
                        .padding(start = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        slotTimeFmt.format(Date(slotStart)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF90A4AE),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideRow(
    row: ChannelsViewModel.Row,
    windowStartMillis: Long,
    nowMillis: Long,
    scroll: androidx.compose.foundation.ScrollState,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit = {},
    onFocus: () -> Unit,
    onProgramme: (Programme) -> Unit,
    onToggleFavourite: () -> Unit = {},
    onExitLeft: () -> Boolean = { false },
) {
    var focused by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {

        // ---- Channel column (Fixed left, TiviMate style) ----
        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxSize()
                .background(
                    if (isSelected) Color(0xFF1A2B38)
                    else Color(0xFF161F27),
                )
                .then(
                    if (focused) Modifier.border(2.dp, Color(0xFF26C6DA))
                    else Modifier,
                )
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) onExitLeft()
                    else false
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = onLongSelect,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel Number
            row.primary.number?.let { num ->
                Text(
                    "$num",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF78909C),
                    maxLines = 1,
                    modifier = Modifier.width(26.dp),
                )
            }

            // Channel Logo
            AsyncImage(
                model = row.primary.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(2.dp)),
            )

            Spacer(Modifier.width(8.dp))

            // Channel Name & Icons
            Column(Modifier.weight(1f)) {
                Text(
                    row.primary.shownName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF26C6DA) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Catchup Icon if available
            if (row.primary.tvArchive) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Catchup",
                    tint = Color(0xFF78909C),
                    modifier = Modifier.size(14.dp),
                )
            }

            // Live Play Triangle indicator
            if (isSelected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Playing",
                    tint = Color(0xFF26C6DA),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(1.dp))

        // ---- Scrolling programme timeline blocks ----
        Row(Modifier.horizontalScroll(scroll)) {
            val programmes = row.programmes
            if (programmes.isEmpty()) {
                Box(
                    Modifier
                        .width(HALF_HOUR_WIDTH * HOURS_IN_WINDOW.toFloat() * 2)
                        .fillMaxSize()
                        .background(Color(0xFF1A232C))
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        stringResource(R.string.guide_no_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF78909C),
                    )
                }
            } else {
                var cursor = windowStartMillis
                var debt = 0.dp
                for (programme in programmes) {
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
                        onClick = { onProgramme(programme) },
                    )
                    cursor = end
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(
    title: String,
    width: Dp,
    isNow: Boolean,
    progress: Float,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        Modifier
            .width(width)
            .fillMaxSize()
            .padding(end = 1.dp)
            .background(
                when {
                    focused -> Color(0xFFF0F4F8) // High-contrast White/Light-grey for active focused cell
                    isNow -> Color(0xFF26323E)   // Slate for live show
                    else -> Color(0xFF1E2833)    // Dark slate for upcoming shows
                },
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White)
                else Modifier.border(0.5.dp, Color(0xFF141C24)),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (focused || isNow) FontWeight.Medium else FontWeight.Normal,
            color = when {
                focused -> Color(0xFF10171E) // Dark charcoal text on white focus background
                isNow -> Color.White
                else -> Color(0xFFD0D7DE)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 8.dp),
        )

        // Live progress line
        if (isNow && progress > 0f && !focused) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(Color(0xFF26C6DA)),
            )
        }
    }
}

/** Minutes between two instants, as guide width. */
private fun widthFor(fromMillis: Long, toMillis: Long): Dp {
    val minutes = ((toMillis - fromMillis) / 60_000L).coerceAtLeast(0)
    return (minutes * MINUTE_DP).dp
}

private const val MINUTE_DP = 4f
private const val HOURS_IN_WINDOW = 24
private const val HALF_HOUR_MS = 30 * 60 * 1000L
private val CHANNEL_COLUMN = 220.dp
private val ROW_HEIGHT = 48.dp
private val MIN_BLOCK_WIDTH = 72.dp
private val HALF_HOUR_WIDTH: Dp = (30 * MINUTE_DP).dp

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

