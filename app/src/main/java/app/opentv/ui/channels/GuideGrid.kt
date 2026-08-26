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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
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
import androidx.compose.ui.unit.sp
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
    onFocusRow: (ChannelsViewModel.Row, Programme?) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onProgramme: (ChannelsViewModel.Row, Programme) -> Unit = { _, _ -> },
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onExitLeftFromChannel: () -> Boolean = { false },
    dayOffset: Int = 0,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }
    var hasFocusedPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(dayOffset) { scroll.scrollTo(0) }

    LaunchedEffect(selectedKey) {
        val index = rows.indexOfFirst { it.key == selectedKey }
        if (index >= 0) {
            val target = (index - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
            if (!hasFocusedPlaying) {
                delay(60)
                runCatching { initialFocusRequester.requestFocus() }
                hasFocusedPlaying = true
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        TimeHeader(windowStartMillis, nowMillis, scroll)

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                val isSelected = row.key == selectedKey
                GuideRow(
                    row = row,
                    windowStartMillis = windowStartMillis,
                    nowMillis = nowMillis,
                    scroll = scroll,
                    isSelected = isSelected,
                    focusRequester = if (isSelected) initialFocusRequester else null,
                    onSelect = { onSelectRow(row) },
                    onLongSelect = { onLongSelectRow(row) },
                    onFocus = { prog -> onFocusRow(row, prog) },
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
    onFocusRow: (ChannelsViewModel.Row, Programme?) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onExitLeftFromChannel: () -> Boolean = { false },
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }
    var hasFocusedPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(selectedKey) {
        val index = rows.indexOfFirst { it.key == selectedKey }
        if (index >= 0) {
            val target = (index - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
            if (!hasFocusedPlaying) {
                delay(60)
                runCatching { initialFocusRequester.requestFocus() }
                hasFocusedPlaying = true
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            val isSelected = row.key == selectedKey
            ChannelListRow(
                row = row,
                nowMillis = nowMillis,
                isSelected = isSelected,
                focusRequester = if (isSelected) initialFocusRequester else null,
                onSelect = { onSelectRow(row) },
                onLongSelect = { onLongSelectRow(row) },
                onFocus = { prog -> onFocusRow(row, prog) },
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
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit = {},
    onFocus: (Programme?) -> Unit,
    onToggleFavourite: () -> Unit,
    onExitLeft: () -> Boolean,
) {
    var focused by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val is24 = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val clockFmt = remember(is24) {
        if (is24) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    val isLive = isSelected

    Row(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                if (focused) Color(0xFFF0F4F8)
                else if (isSelected) Color(0xFF1E2F3E)
                else Color(0xFF18222C),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White)
                else if (isSelected) Modifier.border(1.5.dp, Color(0xFF26C6DA))
                else Modifier,
            )
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) onExitLeft() else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus(row.now)
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
                    text = "${clockFmt.format(Date(nowProg.startUtcMillis))}  ${nowProg.title}",
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
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
                color = Color(0xFF26C6DA),
                fontWeight = FontWeight.Bold,
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
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = Color(0xFFB0BEC5),
                        fontWeight = FontWeight.SemiBold,
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
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit = {},
    onFocus: (Programme?) -> Unit,
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
                    if (focused) Color(0xFFF0F4F8)
                    else if (isSelected) Color(0xFF1A2B38)
                    else Color(0xFF161F27),
                )
                .then(
                    if (focused) Modifier.border(2.dp, Color.White)
                    else if (isSelected) Modifier.border(1.5.dp, Color(0xFF26C6DA))
                    else Modifier,
                )
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) onExitLeft()
                    else false
                }
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocus(row.now)
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
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    color = if (focused) Color(0xFF37474F) else Color(0xFF90A4AE),
                    maxLines = 1,
                    modifier = Modifier.width(28.dp),
                )
            }

            // Channel Logo
            AsyncImage(
                model = row.primary.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(2.dp)),
            )

            Spacer(Modifier.width(8.dp))

            // Channel Name & Icons
            Column(Modifier.weight(1f)) {
                Text(
                    row.primary.shownName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
                    fontWeight = if (focused || isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (focused) Color(0xFF10171E) else if (isSelected) Color(0xFF26C6DA) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Catchup Icon if available
            if (row.primary.tvArchive) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Catchup",
                    tint = if (focused) Color(0xFF00838F) else Color(0xFF00ACC1),
                    modifier = Modifier.size(14.dp),
                )
            }

            // Favorite Icon if favorited
            if (row.primary.favourite) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Live Play Triangle indicator
            if (isSelected) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Playing",
                    tint = if (focused) Color(0xFF00838F) else Color(0xFF26C6DA),
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.width(1.dp))

        // ---- Scrolling programme timeline blocks ----
        Row(Modifier.horizontalScroll(scroll)) {
            val programmes = row.programmes
            if (programmes.isEmpty()) {
                var emptyFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .width(HALF_HOUR_WIDTH * HOURS_IN_WINDOW.toFloat() * 2)
                        .fillMaxSize()
                        .background(
                            if (emptyFocused) Color(0xFFF0F4F8)
                            else Color(0xFF1A232C),
                        )
                        .then(
                            if (emptyFocused) Modifier.border(2.dp, Color.White)
                            else Modifier.border(0.5.dp, Color(0xFF141C24)),
                        )
                        .onFocusChanged {
                            emptyFocused = it.isFocused
                            if (it.isFocused) onFocus(null)
                        }
                        .focusable()
                        .clickable(onClick = onSelect)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        stringResource(R.string.guide_no_info),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = if (emptyFocused) Color(0xFF10171E) else Color(0xFF78909C),
                    )
                }
            } else {
                val blockLayouts = remember(programmes, windowStartMillis, nowMillis) {
                    val layouts = mutableListOf<BlockLayout>()
                    var cursor = windowStartMillis
                    var debt = 0.dp
                    val hasNow = programmes.any { nowMillis in it.startUtcMillis until it.endUtcMillis }
                    for ((pIdx, programme) in programmes.withIndex()) {
                        val start = programme.startUtcMillis.coerceAtLeast(windowStartMillis)
                        val gap = widthFor(cursor, start)
                        val repaid = minOf(debt, gap)
                        debt -= repaid
                        val spacer = gap - repaid
                        val end = programme.endUtcMillis
                        val isNow = nowMillis in programme.startUtcMillis until end
                        val trueWidth = widthFor(start, end)
                        val drawnWidth = maxOf(trueWidth, MIN_BLOCK_WIDTH)
                        debt += drawnWidth - trueWidth
                        val shouldAttachFocus = if (hasNow) isNow else (pIdx == 0)
                        
                        layouts.add(BlockLayout(
                            spacerWidth = spacer,
                            blockWidth = drawnWidth,
                            isNow = isNow,
                            progress = if (isNow) programme.progressAt(nowMillis) else 0f,
                            programmeIndex = pIdx,
                            shouldAttachFocus = shouldAttachFocus
                        ))
                        cursor = end
                    }
                    layouts
                }

                for (layout in blockLayouts) {
                    if (layout.spacerWidth > 0.dp) Spacer(Modifier.width(layout.spacerWidth))
                    ProgrammeBlock(
                        title = programmes[layout.programmeIndex].title,
                        width = layout.blockWidth,
                        isNow = layout.isNow,
                        progress = layout.progress,
                        focusRequester = if (layout.shouldAttachFocus) focusRequester else null,
                        onFocus = { onFocus(programmes[layout.programmeIndex]) },
                        onClick = { onProgramme(programmes[layout.programmeIndex]) },
                    )
                }

                // Trailing filler block to guarantee 100% focus coverage across the entire window
                val windowEnd = windowStartMillis + (HOURS_IN_WINDOW * 60 * 60 * 1000L)
                val lastCursor = programmes.lastOrNull()?.endUtcMillis ?: windowStartMillis
                if (lastCursor < windowEnd) {
                    val remainingWidth = widthFor(lastCursor, windowEnd)
                    if (remainingWidth > 0.dp) {
                        ProgrammeBlock(
                            title = stringResource(R.string.guide_no_info),
                            width = remainingWidth,
                            isNow = false,
                            progress = 0f,
                            onFocus = { onFocus(null) },
                            onClick = onSelect,
                        )
                    }
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
    focusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
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
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 16.sp),
            fontWeight = if (focused || isNow) FontWeight.Bold else FontWeight.Medium,
            color = when {
                focused -> Color(0xFF10171E) // Dark charcoal text on white focus background
                isNow -> Color.White
                else -> Color(0xFFE2E8F0)
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )

        // Live progress line
        if (isNow && progress > 0f && !focused) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.5.dp)
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

private const val MINUTE_DP = 7.0f
private const val HOURS_IN_WINDOW = 24
private const val HALF_HOUR_MS = 30 * 60 * 1000L
private val CHANNEL_COLUMN = 230.dp
private val ROW_HEIGHT = 58.dp
private val MIN_BLOCK_WIDTH = 95.dp
private val HALF_HOUR_WIDTH: Dp = (30 * MINUTE_DP).dp

private data class BlockLayout(
    val spacerWidth: Dp,
    val blockWidth: Dp,
    val isNow: Boolean,
    val progress: Float,
    val programmeIndex: Int,
    val shouldAttachFocus: Boolean,
)
