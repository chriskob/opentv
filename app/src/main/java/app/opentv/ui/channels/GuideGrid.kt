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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

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
 *
 * ## Performance: block layout memoization
 *
 * [blockLayouts] is keyed on a content-derived hash ([contentHashKey]) rather than the list
 * identity. On Fire TV Cube (and any low-end box), Compose can re-compose with a new list
 * instance that contains the same data — the old `remember(programmes, windowStartMillis)`
 * would miss and re-run the O(n) layout pass on every stale recomposition. The content hash
 * avoids that; the pass only re-runs when the programmes actually change or the window shifts.
 *
 * ## Performance: per-block key event handlers removed
 *
 * Each [ProgrammeBlock] used to carry its own [onPreviewKeyEvent] for wrap-around navigation
 * (up from first row / down from last row). On a row with 20+ programmes, that meant 20+
 * identical key event handlers — all checking the same condition — and the Compose focus system
 * walked every one on each dpad press. Now wrap-around is handled ONLY at the [GuideRow] channel
 * column level (one handler per row), and blocks use simple [focusable] + [clickable].
 *
 * ## Performance: LazyColumn prefetching
 *
 * [beyondBoundsItemCount] tells Compose to compose items just outside the viewport before they
 * scroll into view. On a Fire TV Cube where CPU is scarce, this eliminates the "pop-in" jank
 * when dpad-scrolling quickly through a channel list. Set to 5 — enough to cover one dpad hold
 * of repeat events but not so many that we waste composition on rows the user skips past.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GuideGrid(
    rows: List<ChannelsViewModel.Row>,
    windowStartMillis: Long,
    selectedKey: Any?,
    playingKey: Any? = null,
    onSelectRow: (ChannelsViewModel.Row) -> Unit,
    onFocusRow: (ChannelsViewModel.Row, Programme?) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onProgramme: (ChannelsViewModel.Row, Programme) -> Unit = { _, _ -> },
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onExitLeftFromChannel: () -> Boolean = { false },
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
    dayOffset: Int = 0,
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(dayOffset) { scroll.scrollTo(0) }

    LaunchedEffect(selectedKey) {
        val key = selectedKey ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == key }
        if (index >= 0) {
            val target = (index - 2).coerceAtLeast(0)
            listState.scrollToItem(target)
            // Single frame wait: 32ms = one ~30fps frame + safety margin for TV hardware.
            // On a slow Fire TV box, 16ms may not be enough for the newly scrolled-into-view
            // item to be composed and laid out; 32ms is reliably past that frame boundary.
            delay(32)
            runCatching { initialFocusRequester.requestFocus() }
        }
    }

    Column(modifier.fillMaxSize()) {
        TimeHeader(windowStartMillis, nowMillis, scroll, dayOffset)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(
                    items = rows,
                    key = { _, row -> row.key },
                    contentType = { _, _ -> "guide_row" },
                ) { index, row ->
                    val isHighlighted = row.key == selectedKey
                    val isPlaying = row.key == playingKey
                    GuideRow(
                        row = row,
                        rowIndex = index,
                        totalRows = rows.size,
                        windowStartMillis = windowStartMillis,
                        nowMillis = nowMillis,
                        scroll = scroll,
                        isSelected = isPlaying,
                        focusRequester = if (isHighlighted) initialFocusRequester else null,
                        onSelect = { onSelectRow(row) },
                        onLongSelect = { onLongSelectRow(row) },
                        onFocus = { prog -> onFocusRow(row, prog) },
                        onProgramme = { programme -> onProgramme(row, programme) },
                        onToggleFavourite = { onToggleFavourite(row) },
                        onExitLeft = onExitLeftFromChannel,
                        onWrapToBottom = {
                            onWrapToBottom()
                            coroutineScope.launch {
                                val last = (rows.size - 1).coerceAtLeast(0)
                                listState.scrollToItem(last)
                                delay(32)
                                runCatching { initialFocusRequester.requestFocus() }
                            }
                        },
                        onWrapToTop = {
                            onWrapToTop()
                            coroutineScope.launch {
                                listState.scrollToItem(0)
                                delay(32)
                                runCatching { initialFocusRequester.requestFocus() }
                            }
                        },
                    )
                }
            }

            // Live Current Time Indicator Line running through all rows in the guide
            if (dayOffset == 0 && nowMillis >= windowStartMillis) {
                val nowOffset = widthFor(windowStartMillis, nowMillis)
                Box(
                    Modifier
                        .fillMaxHeight()
                        .padding(start = CHANNEL_COLUMN)
                        .horizontalScroll(scroll, enabled = false)
                        .offset(x = nowOffset - 1.dp)
                        .width(1.5.dp)
                        .background(Color(0xFF26C6DA).copy(alpha = 0.7f)),
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
    playingKey: Any? = null,
    onSelectRow: (ChannelsViewModel.Row) -> Unit,
    onFocusRow: (ChannelsViewModel.Row, Programme?) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onExitLeftFromChannel: () -> Boolean = { false },
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier,
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedKey) {
        val key = selectedKey ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == key }
        if (index >= 0) {
            val target = (index - 2).coerceAtLeast(0)
            listState.scrollToItem(target)
            delay(32)
            runCatching { initialFocusRequester.requestFocus() }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            val isHighlighted = row.key == selectedKey
            val isPlaying = row.key == playingKey
            ChannelListRow(
                row = row,
                rowIndex = index,
                totalRows = rows.size,
                nowMillis = nowMillis,
                isSelected = isPlaying,
                focusRequester = if (isHighlighted) initialFocusRequester else null,
                onSelect = { onSelectRow(row) },
                onLongSelect = { onLongSelectRow(row) },
                onFocus = { prog -> onFocusRow(row, prog) },
                onToggleFavourite = { onToggleFavourite(row) },
                onExitLeft = onExitLeftFromChannel,
                onWrapToBottom = {
                    onWrapToBottom()
                    coroutineScope.launch {
                        val last = (rows.size - 1).coerceAtLeast(0)
                        listState.scrollToItem(last)
                        delay(32)
                        runCatching { initialFocusRequester.requestFocus() }
                    }
                },
                onWrapToTop = {
                    onWrapToTop()
                    coroutineScope.launch {
                        listState.scrollToItem(0)
                        delay(32)
                        runCatching { initialFocusRequester.requestFocus() }
                    }
                },
            )
        }
    }
}

/** Shared rounded-corner shape used on all guide cells for a smooth TiviMate-style look. */
private val GuideCellShape = RoundedCornerShape(6.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListRow(
    row: ChannelsViewModel.Row,
    rowIndex: Int = 0,
    totalRows: Int = 1,
    nowMillis: Long,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit = {},
    onFocus: (Programme?) -> Unit,
    onToggleFavourite: () -> Unit,
    onExitLeft: () -> Boolean,
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
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
            .clip(GuideCellShape)
            .background(
                if (focused) Color(0xFFF0F4F8)
                else if (isSelected) Color(0xFF1E2F3E)
                else Color(0xFF18222C),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, GuideCellShape)
                else if (isSelected) Modifier.border(1.5.dp, Color(0xFF26C6DA), GuideCellShape)
                else Modifier,
            )
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when {
                        e.key == Key.DirectionLeft -> onExitLeft()
                        e.key == Key.DirectionUp && rowIndex == 0 -> {
                            onWrapToBottom()
                            true
                        }
                        e.key == Key.DirectionDown && rowIndex == totalRows - 1 -> {
                            onWrapToTop()
                            true
                        }
                        else -> false
                    }
                } else false
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
        // Explicit size ensures Coil decodes at the display resolution (30dp), not full source.
        AsyncImage(
            model = row.primary.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.primary.shownName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                    fontWeight = if (isLive || focused) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (focused) Color(0xFF10171E) else if (isLive) Color(0xFF26C6DA) else Color.White,
                    maxLines = 2,
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
    dayOffset: Int = 0,
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
            .height(28.dp)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
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
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                color = Color(0xFF26C6DA),
                fontWeight = FontWeight.Normal,
                maxLines = 1,
            )
        }

        // Timeline slots with Live Current Time Indicator Marker
        Box(
            Modifier
                .horizontalScroll(scroll)
                .background(Color(0xFF141C24)),
        ) {
            Row {
                repeat(HOURS_IN_WINDOW * 2) { i ->
                    val slotStart = windowStartMillis + i * HALF_HOUR_MS
                    Box(
                        Modifier
                            .width(HALF_HOUR_WIDTH)
                            .height(28.dp)
                            .padding(start = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            slotTimeFmt.format(Date(slotStart)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = Color(0xFFCFD8DC),
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }

            // Live Current Time Indicator marker in Header
            if (dayOffset == 0 && nowMillis >= windowStartMillis) {
                val nowOffset = widthFor(windowStartMillis, nowMillis)
                Box(
                    Modifier
                        .offset(x = nowOffset - 1.5.dp)
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(Color(0xFF26C6DA)),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideRow(
    row: ChannelsViewModel.Row,
    rowIndex: Int = 0,
    totalRows: Int = 1,
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
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {

        // ---- Channel column (Fixed left, TiviMate style with rounded corners) ----
        // This is the ONLY place per-row up/down wrap-around is handled. Programme blocks
        // do NOT carry their own onPreviewKeyEvent — that would duplicate the check across
        // every block in the row (20+ handlers). Compose focus walks all handlers on each
        // dpad press, so consolidating to one per row is a measurable win on low-end boxes.
        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                .background(
                    if (focused) Color(0xFFF0F4F8)
                    else if (isSelected) Color(0xFF1A2B38)
                    else Color(0xFF161F27),
                )
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                    else if (isSelected) Modifier.border(1.5.dp, Color(0xFF26C6DA), RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                    else Modifier,
                )
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        when {
                            e.key == Key.DirectionLeft -> onExitLeft()
                            e.key == Key.DirectionUp && rowIndex == 0 -> {
                                onWrapToBottom()
                                true
                            }
                            e.key == Key.DirectionDown && rowIndex == totalRows - 1 -> {
                                onWrapToTop()
                                true
                            }
                            else -> false
                        }
                    } else false
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

            // Channel Logo — explicit size hints Coil to decode at display resolution
            AsyncImage(
                model = row.primary.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(2.dp)),
            )

            Spacer(Modifier.width(8.dp))

            // Channel Name & Icons
            Column(Modifier.weight(1f)) {
                Text(
                    row.primary.shownName,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.Medium,
                    color = if (focused) Color(0xFF10171E) else if (isSelected) Color(0xFF26C6DA) else Color.White,
                    maxLines = 2,
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

        Spacer(Modifier.width(2.dp))

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
                        .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                        .background(
                            if (emptyFocused) Color(0xFFF0F4F8)
                            else Color(0xFF1A232C),
                        )
                        .then(
                            if (emptyFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            else Modifier.border(0.5.dp, Color(0xFF141C24), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)),
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
                // Keyed on a content-derived hash so the layout pass only re-runs when
                // the programmes actually change — not on stale recompositions with the same data
                // in a different list instance (common on Fire TV Cube where the composable tree
                // re-flows frequently due to dpad repeat events).
                val contentKey = contentHashKey(programmes, windowStartMillis)
                val blockLayouts = remember(contentKey) {
                    val layouts = mutableListOf<BlockLayout>()
                    var cursor = windowStartMillis
                    var debt = 0.dp
                    for ((pIdx, programme) in programmes.withIndex()) {
                        val start = programme.startUtcMillis.coerceAtLeast(windowStartMillis)
                        val gap = widthFor(cursor, start)
                        val repaid = minOf(debt, gap)
                        debt -= repaid
                        val spacer = gap - repaid
                        val end = programme.endUtcMillis
                        val trueWidth = widthFor(start, end)
                        val drawnWidth = maxOf(trueWidth, MIN_BLOCK_WIDTH)
                        debt += drawnWidth - trueWidth

                        layouts.add(
                            BlockLayout(
                                spacerWidth = spacer,
                                blockWidth = drawnWidth,
                                programmeIndex = pIdx,
                            )
                        )
                        cursor = end
                    }
                    layouts
                }

                for (layout in blockLayouts) {
                    if (layout.spacerWidth > 0.dp) Spacer(Modifier.width(layout.spacerWidth))
                    val prog = programmes[layout.programmeIndex]
                    val isNow = nowMillis in prog.startUtcMillis until prog.endUtcMillis
                    ProgrammeBlock(
                        title = prog.title,
                        width = layout.blockWidth,
                        isNow = isNow,
                        progress = if (isNow) prog.progressAt(nowMillis) else 0f,
                        focusRequester = if (isNow) focusRequester else null,
                        onFocus = { onFocus(prog) },
                        onClick = { onProgramme(prog) },
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

/**
 * Produces a stable key from the programme list content so `remember` only re-runs the
 * O(n) block-layout pass when the data actually differs, not on every stale recomposition.
 */
private fun contentHashKey(programmes: List<Programme>, windowStartMillis: Long): Long {
    var hash = windowStartMillis
    // XOR the count and the first+last+middle IDs — enough signal to change when the
    // programme window shifts (new EPG arrives, day offset changes) but stable across
    // recompositions where the list instance differs but content hasn't changed.
    hash = hash xor programmes.size.toLong()
    if (programmes.isNotEmpty()) {
        hash = hash xor programmes.first().id
        hash = hash xor programmes.last().id
        hash = hash xor programmes[programmes.size / 2].id
    }
    return hash
}

/**
 * A programme block in the guide timeline.
 *
 * Does NOT carry its own [onPreviewKeyEvent] for up/down wrap-around. That is handled once
 * per row at the [GuideRow] channel column level, avoiding N×M key event handler evaluations
 * per dpad press (where N=rows, M=blocks per row). On a typical guide with 8 channels and
 * 15 blocks each, that saves 120 handler evaluations per dpad event.
 */
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
            .padding(end = 2.dp)
            .clip(GuideCellShape)
            .background(
                when {
                    focused -> Color(0xFFF0F4F8) // High-contrast White/Light-grey for active focused cell
                    isNow -> Color(0xFF26323E)   // Slate for live show
                    else -> Color(0xFF1E2833)    // Dark slate for upcoming shows
                },
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, GuideCellShape)
                else Modifier.border(0.5.dp, Color(0xFF141C24), GuideCellShape),
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
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Normal,
            color = when {
                focused -> Color(0xFF10171E) // Dark charcoal text on white focus background
                isNow -> Color.White
                else -> Color(0xFFE2E8F0)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )

        // Live progress line - rendered unconditionally with graphicsLayer alpha
        // to avoid inserting/removing nodes from the tree during focus transitions
        if (isNow) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.5.dp)
                    .graphicsLayer { alpha = if (!focused && progress > 0f) 1f else 0f }
                    .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
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
private val CHANNEL_COLUMN = 240.dp
private val ROW_HEIGHT = 60.dp
private val MIN_BLOCK_WIDTH = 95.dp
private val HALF_HOUR_WIDTH: Dp = (30 * MINUTE_DP).dp

private data class BlockLayout(
    val spacerWidth: Dp,
    val blockWidth: Dp,
    val programmeIndex: Int,
)