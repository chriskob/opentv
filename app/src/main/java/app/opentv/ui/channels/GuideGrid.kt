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
import app.opentv.ui.theme.AppTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Formats a channel's shownName across two distinct lines (matching TiviMate):
 * Line 1: Network / Main Station name (e.g., "CBS 2 CHICAGO")
 * Line 2: Station callsign / sub-brand in parentheses (e.g., "(WBBM)")
 */
internal fun formatChannelNameForDisplay(name: String): String {
    val trimmed = name.trim()
    if (trimmed.contains('\n')) return trimmed

    // Check if name contains a callsign or secondary label in parentheses/brackets e.g. "CBS 2 CHICAGO (WBBM)"
    val parenMatch = Regex("""^(.*?)\s*([(\[][A-Za-z0-9-]+[)\]])$""").matchEntire(trimmed)
    if (parenMatch != null && parenMatch.groupValues[1].isNotBlank()) {
        return "${parenMatch.groupValues[1]}\n${parenMatch.groupValues[2]}"
    }

    // Check if name ends with a 2-5 letter station callsign or quality/stream tag e.g. "CBS 2 CHICAGO WBBM" -> "CBS 2 CHICAGO\n(WBBM)"
    val callSignMatch = Regex("""^(.*?)\s+([A-Z0-9]{2,5}(?:-[A-Z0-9]+)?)$""").matchEntire(trimmed)
    if (callSignMatch != null && callSignMatch.groupValues[1].isNotBlank()) {
        val call = callSignMatch.groupValues[2]
        return "${callSignMatch.groupValues[1]}\n($call)"
    }

    // If channel has 2+ words (e.g. "FOX WEATHER", "CW CHICAGO"), split onto 2 lines
    val words = trimmed.split(Regex("""\s+"""))
    if (words.size >= 2) {
        val line1 = words.dropLast(1).joinToString(" ")
        val line2 = words.last()
        return "$line1\n$line2"
    }

    return trimmed
}

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
    focusRequester: FocusRequester? = null,
    onSelectRow: (ChannelsViewModel.Row) -> Unit,
    onFocusRow: (ChannelsViewModel.Row, Programme?) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onProgramme: (ChannelsViewModel.Row, Programme) -> Unit = { _, _ -> },
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onEnableBackScroll: () -> Unit = {},
    onJumpToLive: () -> Unit = {},
    highlightedProgramme: Programme? = null,
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
    dayOffset: Int = 0,
    /** Channel ids whose catch-up the resolver can actually build (per-channel capability) —
     *  the guide's catch-up badge shows exactly for these, never for the rest. */
    catchUpChannelIds: Set<Long> = emptySet(),
    /** "EPG updated … · N channels" stamp shown in the time header, or null until first sync. */
    epgInfoLine: String? = null,
    /** Increment to ask the grid to restore the cursor onto the playing channel at "now". */
    restoreTick: Int = 0,
    /** Bumped when a rail category is focus-previewed: snaps the grid to its first channel. */
    scrollTopTick: Int = 0,
    /** True while the rail previews a category: the grid tints its first row as the cursor. */
    previewTopRow: Boolean = false,
    /** Fires whenever the timeline is displaced from "now" (scrubbed, paged, or panned). */
    onTimeShifted: (Boolean) -> Unit = {},
    nowMillis: Long = System.currentTimeMillis(),
    backScrollActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val focusTargetKey = playingKey ?: selectedKey ?: rows.firstOrNull()?.key

    // Recurring 30s ticker to keep nowMillis accurate and line advancing
    var currentTickMillis by remember { mutableLongStateOf(nowMillis) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            currentTickMillis = System.currentTimeMillis()
        }
    }

    val currentMountedStart = remember(currentTickMillis) {
        calculateMountedFrameStartTime(nowMillis = currentTickMillis)
    }
    val effectiveStartMillis = if (backScrollActive) windowStartMillis else currentMountedStart

    val initialNowScrollPx = remember(effectiveStartMillis, backScrollActive) {
        if (backScrollActive) {
            calculateInitialScrollOffsetPx(
                windowStartMillis = windowStartMillis,
                frameStartMillis = currentMountedStart,
                minuteDp = MINUTE_DP,
                density = density.density,
            )
        } else {
            0
        }
    }
    val scroll = rememberScrollState(initial = initialNowScrollPx)
    val initialFirstVisible = remember(focusTargetKey, rows) {
        val idx = if (focusTargetKey == null) 0 else rows.indexOfFirst { it.key == focusTargetKey }.coerceAtLeast(0)
        if (idx < 6) 0 else (idx - 2).coerceAtLeast(0)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisible,
        initialFirstVisibleItemScrollOffset = 0,
    )

    val internalFocusRequester = remember { FocusRequester() }
    val activeCellFocusRequester = focusRequester ?: internalFocusRequester
    val rowFocusRequesters = remember { mutableMapOf<Any, FocusRequester>() }

    var activeFocusedIndex by remember { mutableStateOf<Int?>(null) }
    var activeFocusedKey by remember { mutableStateOf<Any?>(playingKey ?: selectedKey ?: rows.firstOrNull()?.key) }
    var targetProgKey by remember { mutableStateOf<Long?>(null) }
    var temporalAnchorMillis by remember { mutableLongStateOf(nowMillis) }
    var isNavigatingVertically by remember { mutableStateOf(false) }
    var verticalNavJob by remember { mutableStateOf<Job?>(null) }
    var focusCenterJob by remember { mutableStateOf<Job?>(null) }
    var horizontalScrollJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // ---- TiviMate-style timeline scrub (hold LEFT / RIGHT) -------------------------------------
    // HOLD Left/Right anywhere scrolls the timeline continuously (accelerating) within the loaded
    // guide window. Single taps fall through untouched and keep stepping programme-by-programme.
    // On release, focus re-anchors to the programme now under the viewport's left edge, so the
    // cursor is back on screen and Up/Down continues from the scrubbed time. Back returns to live
    // (HomeScreen's backScrollActive handler), which is also how the category rail is reached.
    var holdPressActive by remember { mutableStateOf(false) }
    var pressIsForward by remember { mutableStateOf(false) }
    var scrubEngaged by remember { mutableStateOf(false) }
    var scrubIsForward by remember { mutableStateOf(false) }
    var holdTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }
    // Until this time, keep-visible auto-scrolls are suppressed so nothing yanks the timeline
    // while the post-scrub re-anchor settles.
    var scrubSettlingUntilMillis by remember { mutableLongStateOf(0L) }

    val engageScrub: (Boolean) -> Unit = { forward ->
        if (!scrubEngaged) {
            scrubEngaged = true
            scrubIsForward = forward
            holdTimeoutJob?.cancel()
            scrubJob?.cancel()
            scrubJob = coroutineScope.launch {
                // Either direction enters back-scroll layout: rows re-lay out from the loaded
                // window's start and the viewport is anchored on "now" first — so pressing Back
                // afterwards restores "now + playing channel" for forward scrubs too.
                onEnableBackScroll()
                val frameStart = calculateMountedFrameStartTime(System.currentTimeMillis())
                scroll.scrollTo(
                    calculateInitialScrollOffsetPx(
                        windowStartMillis = windowStartMillis,
                        frameStartMillis = frameStart,
                        minuteDp = MINUTE_DP,
                        density = density.density,
                    ),
                )
                delay(30)
                var lastFrame = System.currentTimeMillis()
                val startedAt = lastFrame
                while (isActive) {
                    delay(16)
                    val frame = System.currentTimeMillis()
                    val dt = (frame - lastFrame).coerceAtLeast(1L)
                    lastFrame = frame
                    // ~10 minutes/sec at first, easing to ~90 minutes/sec after a few seconds held:
                    // nearby programmes stay precise, and a week of catch-up is still only seconds.
                    val heldSeconds = (frame - startedAt) / 1000f
                    val minutesPerSecond = (SCRUB_MINUTES_PER_SECOND_START +
                        heldSeconds * SCRUB_ACCELERATION).coerceAtMost(SCRUB_MINUTES_PER_SECOND_MAX)
                    val deltaPx = (minutesPerSecond * MINUTE_DP * density.density * dt / 1000f).roundToInt()
                    if (deltaPx > 0) {
                        val target = if (scrubIsForward) scroll.value + deltaPx else scroll.value - deltaPx
                        scroll.scrollTo(target.coerceIn(0, scroll.maxValue))
                    }
                }
            }
        }
    }

    val onDirectionPressStarted: (Boolean) -> Unit = { forward ->
        pressIsForward = forward
        holdPressActive = true
        holdTimeoutJob?.cancel()
        holdTimeoutJob = coroutineScope.launch {
            delay(SCRUB_ENGAGE_MILLIS)
            if (holdPressActive && !scrubEngaged) engageScrub(pressIsForward)
        }
    }

    val releaseScrub: () -> Unit = {
        holdTimeoutJob?.cancel()
        scrubJob?.cancel()
        scrubJob = null
        val wasScrubbing = scrubEngaged
        scrubEngaged = false
        holdPressActive = false
        if (wasScrubbing) {
            // Kill any in-flight keep-visible animation so nothing can drift after release, and
            // suppress auto-scrolls briefly while the re-anchor settles.
            horizontalScrollJob?.cancel()
            scrubSettlingUntilMillis = System.currentTimeMillis() + SCRUB_SETTLE_MILLIS
            // Re-anchor focus to the programme containing/nearest the viewport's left edge, and
            // set targetProgKey EXPLICITLY: the picker's fallback chain ends at the live
            // programme, which can sit far right of the scrub position — without this, focus
            // jumps there and keep-visible animates the whole timeline back (cursor "vanishes",
            // guide keeps moving).
            val key = activeFocusedKey ?: playingKey ?: selectedKey ?: rows.firstOrNull()?.key
            val row = rows.firstOrNull { it.key == key }
            val scrollDp = with(density) { scroll.value.toDp() }
            val anchorMillis = effectiveStartMillis + (scrollDp.value / MINUTE_DP).toLong() * 60_000L
            val anchorProgramme = row?.programmes
                ?.firstOrNull { anchorMillis in it.startUtcMillis until it.endUtcMillis }
                ?: row?.programmes?.minByOrNull {
                    if (anchorMillis < it.startUtcMillis) it.startUtcMillis - anchorMillis
                    else anchorMillis - it.endUtcMillis
                }
            val targetReq = key?.let { rowFocusRequesters[it] }
            if (row != null && targetReq != null) {
                temporalAnchorMillis = anchorProgramme?.let { computeProgrammeMidpoint(it) } ?: anchorMillis
                targetProgKey = anchorProgramme?.id
                coroutineScope.launch {
                    delay(16)
                    for (attempt in 0..4) {
                        val res = runCatching { targetReq.requestFocus() }
                        if (res.isSuccess) break
                        delay(25)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            holdTimeoutJob?.cancel()
            scrubJob?.cancel()
        }
    }

    val focusAndCenterRow = { targetKey: Any?, animate: Boolean ->
        if (rows.isNotEmpty()) {
            val k = targetKey ?: playingKey ?: selectedKey ?: rows.first().key
            val index = rows.indexOfFirst { it.key == k }.coerceAtLeast(0)
            val matchedRow = rows[index]
            val now = System.currentTimeMillis()
            val liveProg = matchedRow.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis }
                ?: matchedRow.now
                ?: matchedRow.programmes.firstOrNull()
            activeFocusedIndex = index
            activeFocusedKey = k
            targetProgKey = liveProg?.id
            temporalAnchorMillis = if (liveProg != null) computeProgrammeMidpoint(liveProg) else now
            isNavigatingVertically = false
            onFocusRow(matchedRow, liveProg)

            val targetVisible = when {
                rows.size <= 6 -> 0
                index <= 2 -> 0
                index >= rows.size - 3 -> (rows.size - 6).coerceAtLeast(0)
                else -> index - 2
            }
            val targetPx = if (backScrollActive) {
                val frameStart = calculateMountedFrameStartTime(now)
                calculateInitialScrollOffsetPx(
                    windowStartMillis = windowStartMillis,
                    frameStartMillis = frameStart,
                    minuteDp = MINUTE_DP,
                    density = density.density,
                )
            } else {
                0
            }
            focusCenterJob?.cancel()
            focusCenterJob = coroutineScope.launch {
                if (targetPx != scroll.value) {
                    if (animate) scroll.animateScrollTo(targetPx) else scroll.scrollTo(targetPx)
                }
                if (animate) {
                    listState.animateScrollToItem(targetVisible, 0)
                } else {
                    listState.scrollToItem(targetVisible, 0)
                }
                delay(30)
                // While the rail is previewing, focus must stay in the rail: centering is purely
                // visual. Reclaiming row focus here would collapse the rail (onFocusRow closes it).
                if (!previewTopRow) {
                    val targetReq = rowFocusRequesters[k] ?: activeCellFocusRequester
                    for (attempt in 0..3) {
                        val res = runCatching { targetReq.requestFocus() }
                        if (res.isSuccess) break
                        delay(30)
                    }
                }
            }
        }
    }

    var initializedKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(focusTargetKey, rows.isNotEmpty()) {
        if (rows.isNotEmpty()) {
            val targetKey = playingKey ?: selectedKey ?: rows.first().key
            if (initializedKey != targetKey) {
                initializedKey = targetKey
                focusAndCenterRow(targetKey, false)
            }
        }
    }

    LaunchedEffect(backScrollActive) {
        if (backScrollActive) {
            val now = System.currentTimeMillis()
            val frameStart = calculateMountedFrameStartTime(now)
            val targetPx = calculateInitialScrollOffsetPx(
                windowStartMillis = windowStartMillis,
                frameStartMillis = frameStart,
                minuteDp = MINUTE_DP,
                density = density.density,
            )
            if (targetPx != scroll.value) {
                scroll.scrollTo(targetPx)
            }
        }
    }

    // HomeScreen Back handler: restore the cursor onto the playing channel at "now".
    // During a rail preview the rail owns focus — focusAndCenterRow calls onFocusRow, which
    // collapses the rail — so only center the list visually and park the focus-box state.
    LaunchedEffect(restoreTick) {
        if (restoreTick > 0) {
            if (previewTopRow) {
                if (rows.isNotEmpty()) {
                    val k = playingKey ?: selectedKey ?: rows.first().key
                    val index = rows.indexOfFirst { it.key == k }.coerceAtLeast(0)
                    val targetVisible = when {
                        rows.size <= 6 -> 0
                        index <= 2 -> 0
                        index >= rows.size - 3 -> (rows.size - 6).coerceAtLeast(0)
                        else -> index - 2
                    }
                    listState.scrollToItem(targetVisible, 0)
                    activeFocusedIndex = index
                    activeFocusedKey = k
                }
            } else {
                focusAndCenterRow(playingKey ?: selectedKey, false)
            }
        }
    }

    // Report timeline displacement so HomeScreen's Back handler knows the user is browsing
    // away from "now" (scrubbed, day-paged, or panned forward) versus sitting on live.
    LaunchedEffect(backScrollActive, scroll.value) {
        onTimeShifted(backScrollActive || scroll.value > 4)
    }

    // Rail category focus-preview: snap the grid to the top (channel 1) and clear any stale
    // cursor state, so the previewed category always starts from its first channel.
    LaunchedEffect(scrollTopTick) {
        if (scrollTopTick > 0) {
            listState.scrollToItem(0, 0)
            activeFocusedKey = null
            activeFocusedIndex = null
            targetProgKey = null
        }
    }

    LaunchedEffect(windowStartMillis) {
        if (backScrollActive) {
            val frameStart = calculateMountedFrameStartTime(System.currentTimeMillis())
            val targetPx = calculateInitialScrollOffsetPx(
                windowStartMillis = windowStartMillis,
                frameStartMillis = frameStart,
                minuteDp = MINUTE_DP,
                density = density.density,
            )
            scroll.scrollTo(targetPx)
        }
    }

    val handleJumpToLive = {
        val targetKey = playingKey ?: selectedKey ?: activeFocusedKey ?: rows.firstOrNull()?.key
        focusAndCenterRow(targetKey, true)
        onJumpToLive()
    }

    val handleNavigateVertical: (isDown: Boolean, fromRowIndex: Int) -> Boolean = { isDown, fromRowIndex ->
        if (rows.isNotEmpty()) {
            val targetIndex = if (isDown) {
                if (fromRowIndex < rows.size - 1) fromRowIndex + 1 else 0
            } else {
                if (fromRowIndex > 0) fromRowIndex - 1 else rows.size - 1
            }
            val targetRow = rows[targetIndex]
            isNavigatingVertically = true

            val targetRequester = rowFocusRequesters[targetRow.key]
            val success = if (targetRequester != null) {
                runCatching { targetRequester.requestFocus() }.isSuccess
            } else false

            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val firstVisible = listState.firstVisibleItemIndex
            val lastVisible = visibleItems.lastOrNull()?.index ?: (firstVisible + 5)
            val visibleCount = visibleItems.size.coerceAtLeast(1)

            // Only scroll LazyColumn when the target item is at or beyond viewport edges
            val targetScrollIndex = when {
                targetIndex == 0 -> 0
                targetIndex >= rows.size - 1 -> (rows.size - visibleCount).coerceAtLeast(0)
                targetIndex <= firstVisible -> (targetIndex - 1).coerceAtLeast(0)
                targetIndex >= lastVisible -> (targetIndex - visibleCount + 2).coerceAtLeast(0)
                else -> null
            }

            if (targetScrollIndex != null || !success) {
                verticalNavJob?.cancel()
                verticalNavJob = coroutineScope.launch {
                    if (targetScrollIndex != null) {
                        listState.scrollToItem(targetScrollIndex, 0)
                    }
                    if (!success) {
                        delay(25L)
                        for (attempt in 0..3) {
                            val res = runCatching { rowFocusRequesters[targetRow.key]?.requestFocus() }
                            if (res.isSuccess) break
                            delay(25L)
                        }
                    }
                }
            }
            true
        } else false
    }

    val handleWrapToBottom = {
        if (rows.isNotEmpty()) handleNavigateVertical(false, 0)
    }

    val handleWrapToTop = {
        if (rows.isNotEmpty()) handleNavigateVertical(true, rows.size - 1)
    }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                when {
                    // MediaPlay jumps the guide back to the live edge.
                    e.type == KeyEventType.KeyDown && (e.key == Key.MediaPlay || e.key == Key.MediaPlayPause) -> {
                        handleJumpToLive()
                        true
                    }
                    // TiviMate-style scrub: HOLD Left/Right anywhere on the timeline to rewind or
                    // fast-forward; single taps return false and keep stepping programme focus.
                    // Key repeats are consumed once a press is active so focus-walking stops and
                    // the scrub takes over.
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            onDirectionPressStarted(false)
                            false
                        } else {
                            if (holdPressActive) engageScrub(false)
                            holdPressActive
                        }
                    }
                    e.type == KeyEventType.KeyUp && e.key == Key.DirectionLeft -> {
                        if (scrubEngaged) {
                            releaseScrub()
                            true
                        } else {
                            holdPressActive = false
                            false
                        }
                    }
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            onDirectionPressStarted(true)
                            false
                        } else {
                            if (holdPressActive) engageScrub(true)
                            holdPressActive
                        }
                    }
                    e.type == KeyEventType.KeyUp && e.key == Key.DirectionRight -> {
                        if (scrubEngaged) {
                            releaseScrub()
                            true
                        } else {
                            holdPressActive = false
                            false
                        }
                    }
                    else -> false
                }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            TimeHeader(
                windowStartMillis = effectiveStartMillis,
                nowMillis = currentTickMillis,
                scroll = scroll,
                epgInfoLine = epgInfoLine,
            )
            Spacer(Modifier.height(2.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (rows.isEmpty()) {
                        // Focusable empty state: exiting the rail onto an empty category (e.g.
                        // Favourites with nothing starred) needs a focus target inside the guide,
                        // or focus strands on the rail entry and the screen dead-ends.
                        item(key = "guide-empty") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .focusRequester(activeCellFocusRequester)
                                    .focusable()
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    stringResource(R.string.channels_manager_empty_category),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.key },
                        contentType = { _, _ -> "guide_row" },
                    ) { index, row ->
                        val currentActiveKey = activeFocusedKey ?: focusTargetKey ?: rows.firstOrNull()?.key
                        val isPlaying = row.key == playingKey
                        val isHighlighted = row.key == currentActiveKey
                        val rowRequester = rowFocusRequesters.getOrPut(row.key) { FocusRequester() }
                        GuideRow(
                            row = row,
                            rowIndex = index,
                            totalRows = rows.size,
                            windowStartMillis = effectiveStartMillis,
                            nowMillis = currentTickMillis,
                            temporalAnchorMillis = temporalAnchorMillis,
                            scroll = scroll,
                            catchUpChannelIds = catchUpChannelIds,
                            isSelected = isPlaying,
                            isRowHighlighted = isHighlighted || (previewTopRow && index == 0),
                        previewHighlight = previewTopRow && index == 0,
                            targetProgKey = if (isHighlighted) targetProgKey else null,
                            rowFocusRequester = rowRequester,
                            externalFocusRequester = if (isHighlighted) activeCellFocusRequester else null,
                            onSelect = { onSelectRow(row) },
                            onLongSelect = { onLongSelectRow(row) },
                            onFocus = { prog ->
                                if (!isNavigatingVertically) {
                                    if (prog != null) {
                                        temporalAnchorMillis = computeProgrammeMidpoint(prog)
                                    }
                                } else {
                                    isNavigatingVertically = false
                                }
                                activeFocusedIndex = index
                                activeFocusedKey = row.key
                                targetProgKey = prog?.id
                                onFocusRow(row, prog)

                                // Keep horizontally visible if focused block is outside current viewport
                                // Only scroll if NOT navigating vertically AND programme is completely offscreen
                                if (prog != null && !isNavigatingVertically &&
                                    System.currentTimeMillis() >= scrubSettlingUntilMillis
                                ) {
                                    val progStartX = widthFor(effectiveStartMillis, prog.startUtcMillis)
                                    val progEndX = widthFor(effectiveStartMillis, prog.endUtcMillis)
                                    val currentScrollDp = with(density) { scroll.value.toDp() }
                                    val viewportWidthDp = 800.dp
                                    if (progEndX <= currentScrollDp) {
                                        val targetPx = with(density) { (progStartX - 10.dp).coerceAtLeast(0.dp).roundToPx() }
                                        horizontalScrollJob?.cancel()
                                        horizontalScrollJob = coroutineScope.launch { scroll.animateScrollTo(targetPx) }
                                    } else if (progStartX >= currentScrollDp + viewportWidthDp) {
                                        val targetPx = with(density) { (progEndX - viewportWidthDp + 10.dp).coerceAtLeast(0.dp).roundToPx() }
                                        horizontalScrollJob?.cancel()
                                        horizontalScrollJob = coroutineScope.launch { scroll.animateScrollTo(targetPx) }
                                    }
                                }
                            },
                            onProgramme = { programme -> onProgramme(row, programme) },
                            onToggleFavourite = { onToggleFavourite(row) },
                            onNavigateVertical = { isDown -> handleNavigateVertical(isDown, index) },
                            onWrapToBottom = { handleWrapToBottom() },
                            onWrapToTop = { handleWrapToTop() },
                        )
                    }
                }
            }
        }

        // Live Current Time Indicator Line & Pip running from TimeHeader down through all rows
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = CHANNEL_COLUMN)
                .clipToBounds()
                .zIndex(15f)
        ) {
            val scrollOffsetDp = with(density) { scroll.value.toDp() }
            val nowOffsetDp = widthFor(effectiveStartMillis, currentTickMillis)
            val lineXDp = nowOffsetDp - scrollOffsetDp
            if (lineXDp >= 0.dp) {
                // Continuous vertical line running from header divider down through all rows
                Box(
                    Modifier
                        .fillMaxHeight()
                        .padding(top = 28.dp)
                        .offset(x = lineXDp - 0.75.dp)
                        .width(1.5.dp)
                        .background(AppTheme.primary.copy(alpha = 0.85f))
                )
                // Small circular dot / pip on the timeline header divider
                Box(
                    Modifier
                        .padding(top = 25.dp)
                        .offset(x = lineXDp - 3.5.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(AppTheme.primary)
                        .border(1.dp, Color.White, CircleShape)
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
    focusRequester: FocusRequester? = null,
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
    val focusTargetKey = playingKey ?: selectedKey ?: rows.firstOrNull()?.key
    val initialFirstVisible = remember(focusTargetKey, rows) {
        val idx = if (focusTargetKey == null) 0 else rows.indexOfFirst { it.key == focusTargetKey }.coerceAtLeast(0)
        if (idx < 6) 0 else (idx - 2).coerceAtLeast(0)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisible,
        initialFirstVisibleItemScrollOffset = 0,
    )
    val internalFocusRequester = remember { FocusRequester() }
    val activeCellRequester = focusRequester ?: internalFocusRequester
    var activeFocusedIndex by remember { mutableStateOf<Int?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val focusAndCenter = { targetKey: Any? ->
        if (rows.isNotEmpty()) {
            val k = targetKey ?: playingKey ?: selectedKey ?: rows.first().key
            val index = rows.indexOfFirst { it.key == k }.coerceAtLeast(0)
            activeFocusedIndex = index
            val target = when {
                rows.size <= 6 -> 0
                index <= 2 -> 0
                index >= rows.size - 3 -> (rows.size - 6).coerceAtLeast(0)
                else -> index - 2
            }
            coroutineScope.launch {
                listState.scrollToItem(target, 0)
                delay(30)
                for (attempt in 0..5) {
                    val res = runCatching { activeCellRequester.requestFocus() }
                    if (res.isSuccess) break
                    delay(40)
                }
            }
        }
    }

    var initializedKey by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(focusTargetKey, rows.isNotEmpty()) {
        if (rows.isNotEmpty()) {
            val targetKey = playingKey ?: selectedKey ?: rows.first().key
            if (initializedKey != targetKey) {
                initializedKey = targetKey
                focusAndCenter(targetKey)
            }
        }
    }

    val handleWrapToBottom = {
        if (rows.isNotEmpty()) {
            val lastIdx = rows.size - 1
            activeFocusedIndex = lastIdx
            onFocusRow(rows[lastIdx], rows[lastIdx].now)
            coroutineScope.launch {
                val targetVisible = (rows.size - 6).coerceAtLeast(0)
                listState.scrollToItem(targetVisible, 0)
                delay(30)
                runCatching { activeCellRequester.requestFocus() }
            }
        }
    }

    val handleWrapToTop = {
        if (rows.isNotEmpty()) {
            activeFocusedIndex = 0
            onFocusRow(rows[0], rows[0].now)
            coroutineScope.launch {
                listState.scrollToItem(0, 0)
                delay(30)
                runCatching { activeCellRequester.requestFocus() }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 2.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (rows.isEmpty()) {
            // Focusable empty state — same reason as GuideGrid's: the guide must always offer
            // a focus target, or leaving the rail onto an empty category strands the cursor.
            item(key = "guide-empty") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .focusRequester(activeCellRequester)
                        .focusable()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        stringResource(R.string.channels_manager_empty_category),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
            val currentActiveKey = activeFocusedIndex?.let { rows.getOrNull(it)?.key } ?: selectedKey ?: playingKey ?: rows.firstOrNull()?.key
            val isPlaying = row.key == playingKey
            val rowRequester = if (row.key == currentActiveKey) activeCellRequester else null
            ChannelListRow(
                row = row,
                rowIndex = index,
                totalRows = rows.size,
                nowMillis = nowMillis,
                isSelected = isPlaying,
                focusRequester = rowRequester,
                onSelect = { onSelectRow(row) },
                onLongSelect = { onLongSelectRow(row) },
                onFocus = { prog ->
                    activeFocusedIndex = index
                    onFocusRow(row, prog)
                },
                onToggleFavourite = { onToggleFavourite(row) },
                onExitLeft = onExitLeftFromChannel,
                onWrapToBottom = handleWrapToBottom,
                onWrapToTop = handleWrapToTop,
            )
        }
    }
}

/** Shared rounded-corner shape used on all guide cells for a smooth TiviMate-style look. */
private val GuideCellShape = RoundedCornerShape(4.dp)

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

    val clockFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

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
                else if (isSelected) Modifier.border(1.5.dp, AppTheme.primary, GuideCellShape)
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
        // Channel Number (Starts at 1 in each category, sequential without duplicates)
        val displayNum = rowIndex + 1
        Text(
            "$displayNum",
            style = MaterialTheme.typography.labelMedium,
            color = if (focused) Color(0xFF37474F) else Color(0xFF78909C),
            maxLines = 1,
            modifier = Modifier.width(32.dp),
        )
        // Channel Logo — enlarged for high legibility across the room
        AsyncImage(
            model = row.primary.logoUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatChannelNameForDisplay(row.primary.shownName),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 15.sp),
                    fontWeight = if (isLive || focused) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (focused) Color(0xFF10171E) else if (isLive) AppTheme.primary else Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isLive) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Live",
                        tint = if (focused) AppTheme.dark else AppTheme.primary,
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
    epgInfoLine: String? = null,
) {
    val currentDateTimeFmt = remember { SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()) }
    val slotTimeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color(0xFF161E26)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Top-left label: Current Date & Time in clean Cyan, matching TiviMate, with the
        // EPG sync stamp ("EPG updated … · N channels") stacked underneath it.
        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = currentDateTimeFmt.format(Date(nowMillis)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    color = AppTheme.primary,
                    maxLines = 1,
                )
                if (epgInfoLine != null) {
                    Text(
                        text = epgInfoLine,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color(0xFF8B9BA8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Timeline slots
        Box(
            Modifier
                .horizontalScroll(scroll)
                .background(Color(0xFF161E26)),
        ) {
            Row {
                repeat(HOURS_IN_WINDOW * 2) { i ->
                    val slotStart = windowStartMillis + i * HALF_HOUR_MS
                    Box(
                        Modifier
                            .width(HALF_HOUR_WIDTH)
                            .height(38.dp)
                            .padding(start = 6.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            slotTimeFmt.format(Date(slotStart)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = Color(0xFFCFD8DC),
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
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
    temporalAnchorMillis: Long,
    scroll: androidx.compose.foundation.ScrollState,
    catchUpChannelIds: Set<Long> = emptySet(),
    previewHighlight: Boolean = false,
    isSelected: Boolean,
    isRowHighlighted: Boolean = false,
    targetProgKey: Long? = null,
    rowFocusRequester: FocusRequester,
    externalFocusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit = {},
    onFocus: (Programme?) -> Unit,
    onProgramme: (Programme) -> Unit,
    onToggleFavourite: () -> Unit = {},
    onNavigateVertical: (isDown: Boolean) -> Boolean = { false },
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when {
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
    ) {

        // ---- Channel column (Fixed left header, NOT focusable, no white cursor highlight) ----
        Row(
            Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .background(if (isRowHighlighted) Color(0xFF1C2630) else Color(0xFF161E26))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel Number (Starts at 1 in each category, sequential without duplicates)
            val displayNum = rowIndex + 1
            Text(
                "$displayNum",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = Color(0xFF8B9BA8),
                maxLines = 1,
                modifier = Modifier.width(32.dp),
            )

            // Channel Logo
            AsyncImage(
                model = row.primary.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
            )

            Spacer(Modifier.width(8.dp))

            // Channel Name & Icons
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    formatChannelNameForDisplay(row.primary.shownName),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 15.sp),
                    fontWeight = if (isRowHighlighted) FontWeight.Bold else FontWeight.Medium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Live Play Triangle indicator (shown on currently playing channel, like ABC 7 in Image 2)
            if (isSelected) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Playing",
                    tint = AppTheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }

            // Catchup Icon if available (circular replay icon ↺) — per-channel, mirroring what
            // CatchupResolver can actually build for THIS channel (flag, template, portal source,
            // or Xtream-format stream URL). Channels outside that get no badge.
            if (row.primary.tvArchive || row.primary.id in catchUpChannelIds) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "Catchup",
                    tint = Color(0xFF8B9BA8),
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
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // ---- Scrolling programme timeline blocks ----
        Row(Modifier.horizontalScroll(scroll)) {
            val programmes = row.programmes
            val windowEndMillis = windowStartMillis + (HOURS_IN_WINDOW * 3600_000L)
            if (programmes.isEmpty()) {
                var emptyFocused by remember { mutableStateOf(false) }
                // Pseudo-cursor while the rail previews this category (no focus stolen).
                val emptyHighlighted = emptyFocused || previewHighlight
                val extReq = if (isRowHighlighted) externalFocusRequester else null
                Box(
                    Modifier
                        .then(Modifier.focusRequester(rowFocusRequester))
                        .then(if (extReq != null) Modifier.focusRequester(extReq) else Modifier)
                        .width(widthFor(windowStartMillis, windowEndMillis))
                        .fillMaxSize()
                        .clip(GuideCellShape)
                        .background(
                            if (emptyHighlighted) Color.White
                            else Color(0xFF222C36),
                        )
                        .then(
                            if (emptyHighlighted) Modifier.border(1.5.dp, Color.White, GuideCellShape)
                            else Modifier.border(0.5.dp, Color(0xFF334250).copy(alpha = 0.6f), GuideCellShape),
                        )
                        .onFocusChanged {
                            emptyFocused = it.isFocused
                            if (it.isFocused) onFocus(null)
                        }
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown) {
                                when (e.key) {
                                    Key.DirectionUp -> onNavigateVertical(false)
                                    Key.DirectionDown -> onNavigateVertical(true)
                                    else -> false
                                }
                            } else false
                        }
                        .focusable()
                        .clickable(onClick = onSelect)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        stringResource(R.string.guide_no_info),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 13.sp),
                        color = if (emptyFocused) Color(0xFF10171E) else Color(0xFF78909C),
                    )
                }
            } else {
                // Keyed on a content-derived hash so the layout pass only re-runs when
                // the programmes actually change — not on stale recompositions.
                val contentKey = contentHashKey(programmes, windowStartMillis)
                val blockLayouts = remember(contentKey) {
                    val layouts = mutableListOf<BlockLayout>()
                    var cursor = windowStartMillis
                    for ((pIdx, programme) in programmes.withIndex()) {
                        val rawStart = programme.startUtcMillis.coerceAtLeast(windowStartMillis)
                        val rawEnd = programme.endUtcMillis.coerceAtMost(windowEndMillis)
                        if (rawEnd <= rawStart) continue

                        // Strictly avoid overlap: if EPG has overlapping entries, clamp start to cursor
                        val start = maxOf(rawStart, cursor)
                        if (start >= windowEndMillis) break
                        val end = maxOf(start, rawEnd)

                        val spacer = widthFor(cursor, start)
                        val blockWidth = widthFor(start, end)

                        if (blockWidth > 0.dp) {
                            layouts.add(
                                BlockLayout(
                                    spacerWidth = spacer,
                                    blockWidth = blockWidth,
                                    programmeIndex = pIdx,
                                )
                            )
                            cursor = end
                        }
                    }
                    layouts
                }

                // Determine target block index for focus: matches targetProgKey, or closest to temporalAnchorMillis, or live show, or first block
                val targetBlockIdx = remember(programmes, targetProgKey, temporalAnchorMillis, nowMillis) {
                    val targetMatch = if (targetProgKey != null) {
                        blockLayouts.indexOfFirst { layout ->
                            programmes.getOrNull(layout.programmeIndex)?.id == targetProgKey
                        }
                    } else -1

                    if (targetMatch >= 0) {
                        targetMatch
                    } else {
                        val anchorProg = getVerticalTargetProgram(programmes, temporalAnchorMillis)
                        val anchorMatch = if (anchorProg != null) {
                            blockLayouts.indexOfFirst { layout ->
                                programmes.getOrNull(layout.programmeIndex)?.id == anchorProg.id
                            }
                        } else -1

                        if (anchorMatch >= 0) {
                            anchorMatch
                        } else {
                            val liveIdx = blockLayouts.indexOfFirst { layout ->
                                val prog = programmes[layout.programmeIndex]
                                nowMillis in prog.startUtcMillis until prog.endUtcMillis
                            }
                            if (liveIdx >= 0) liveIdx else 0
                        }
                    }
                }

                val blockFocusRequesters = remember(blockLayouts.size) {
                    List(blockLayouts.size) { FocusRequester() }
                }

                for ((pOrder, layout) in blockLayouts.withIndex()) {
                    if (layout.spacerWidth > 0.dp) Spacer(Modifier.width(layout.spacerWidth))
                    val prog = programmes[layout.programmeIndex]
                    val isNow = nowMillis in prog.startUtcMillis until prog.endUtcMillis
                    val isFirst = pOrder == 0
                    val isLast = pOrder == blockLayouts.size - 1
                    val isTarget = pOrder == targetBlockIdx
                    val blockRequester = blockFocusRequesters.getOrNull(pOrder)
                    val extReq = if (isTarget && isRowHighlighted) externalFocusRequester else null

                    ProgrammeBlock(
                        title = prog.resolvedTitle(),
                        width = layout.blockWidth,
                        isNow = isNow,
                        progress = if (isNow) prog.progressAt(nowMillis) else 0f,
                        isNew = prog.isNewEpisode(),
                        pseudoFocused = previewHighlight && isTarget,
                        isRowHighlighted = isRowHighlighted,
                        focusRequester = blockRequester,
                        rowFocusRequester = if (isTarget) rowFocusRequester else null,
                        externalFocusRequester = extReq,
                        onFocus = { onFocus(prog) },
                        onClick = { onProgramme(prog) },
                        onNavigateVertical = onNavigateVertical,
                        onMoveLeft = if (!isFirst) {
                            { runCatching { blockFocusRequesters[pOrder - 1].requestFocus() } }
                        } else null,
                        onMoveRight = if (!isLast) {
                            { runCatching { blockFocusRequesters[pOrder + 1].requestFocus() } }
                        } else null,
                    )
                }

                // Trailing filler block to guarantee 100% focus coverage across the entire window
                val lastCursor = if (blockLayouts.isEmpty()) windowStartMillis else {
                    val lastLayout = blockLayouts.last()
                    val lastProg = programmes.getOrNull(lastLayout.programmeIndex)
                    lastProg?.endUtcMillis?.coerceAtMost(windowEndMillis) ?: windowStartMillis
                }
                if (lastCursor < windowEndMillis) {
                    val remainingWidth = widthFor(lastCursor, windowEndMillis)
                        val isFillerTarget = blockLayouts.isEmpty() || targetBlockIdx < 0
                        val extReq = if (isFillerTarget && isRowHighlighted) externalFocusRequester else null
                        ProgrammeBlock(
                            title = stringResource(R.string.guide_no_info),
                            width = remainingWidth,
                            isNow = false,
                            progress = 0f,
                            isRowHighlighted = isRowHighlighted,
                            rowFocusRequester = if (isFillerTarget) rowFocusRequester else null,
                            externalFocusRequester = extReq,
                            onFocus = { onFocus(null) },
                            onClick = onSelect,
                            onNavigateVertical = onNavigateVertical,
                            onMoveLeft = {
                                blockFocusRequesters.lastOrNull()?.let { req ->
                                    runCatching { req.requestFocus() }
                                }
                            },
                        )
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
 * Supports directional vertical navigation using the Virtual Timestamp Anchor to preserve
 * timeline alignment across irregular program durations.
 */
@Composable
private fun ProgrammeBlock(
    title: String,
    width: Dp,
    isNow: Boolean,
    progress: Float,
    isNew: Boolean = false,
    pseudoFocused: Boolean = false,
    isRowHighlighted: Boolean = false,
    focusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    externalFocusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
    onNavigateVertical: ((isDown: Boolean) -> Boolean)? = null,
    onMoveLeft: (() -> Unit)? = null,
    onMoveRight: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }

    // Pseudo-cursor: rendered like focus without stealing it (rail category preview).
    val highlighted = focused || pseudoFocused

    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
            .then(if (externalFocusRequester != null) Modifier.focusRequester(externalFocusRequester) else Modifier)
            .width(width)
            .fillMaxSize()
            .padding(end = 1.5.dp)
            .clip(GuideCellShape)
            .background(
                if (highlighted) Color.White
                else Color(0xFF222C36)
            )
            .then(
                if (highlighted) Modifier.border(1.5.dp, Color.White, GuideCellShape)
                else Modifier.border(0.5.dp, Color(0xFF334250).copy(alpha = 0.6f), GuideCellShape)
            )
            .onPreviewKeyEvent { e ->
                if (e.key == Key.DirectionUp) {
                    if (e.type == KeyEventType.KeyDown && onNavigateVertical != null) {
                        onNavigateVertical(false)
                    } else false
                } else if (e.key == Key.DirectionDown) {
                    if (e.type == KeyEventType.KeyDown && onNavigateVertical != null) {
                        onNavigateVertical(true)
                    } else false
                } else if (e.key == Key.DirectionLeft) {
                    // Single Left taps step between blocks; on the leftmost block Left is a no-op
                    // (the category rail is reached with Back). HOLD Left is the timeline scrub,
                    // handled at the GuideGrid root.
                    if (e.type == KeyEventType.KeyDown) {
                        if (onMoveLeft != null) {
                            onMoveLeft()
                            true
                        } else false
                    } else false
                } else if (e.key == Key.DirectionRight) {
                    if (onMoveRight != null) {
                        if (e.type == KeyEventType.KeyDown) {
                            onMoveRight()
                            true
                        } else false
                    } else false
                } else false
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .focusable()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isNew) {
                Box(
                    modifier = Modifier
                        .padding(end = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFE65100))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "NEW",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                ),
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    highlighted -> Color(0xFF10171E) // Dark slate text on pure white focus background
                    else -> Color(0xFFECEFF1)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
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

// ---- TiviMate-style hold-to-scrub tuning ------------------------------------------------
/** How long Left/Right must be held before the timeline scrub engages (a tap stays a tap). */
private const val SCRUB_ENGAGE_MILLIS = 400L
/** Scrub speed in minutes of timeline per second: start, acceleration per second held, cap. */
private const val SCRUB_MINUTES_PER_SECOND_START = 10f
private const val SCRUB_ACCELERATION = 20f
private const val SCRUB_MINUTES_PER_SECOND_MAX = 90f
/** How long after a scrub release keep-visible auto-scrolls stay suppressed. */
private const val SCRUB_SETTLE_MILLIS = 400L
private const val PAST_HOURS = 24
private const val FUTURE_HOURS = 24
private const val HOURS_IN_WINDOW = PAST_HOURS + FUTURE_HOURS
private const val HALF_HOUR_MS = 30 * 60 * 1000L
private val CHANNEL_COLUMN = 240.dp
private val ROW_HEIGHT = 48.dp
private val HALF_HOUR_WIDTH: Dp = (30 * MINUTE_DP).dp

private data class BlockLayout(
    val spacerWidth: Dp,
    val blockWidth: Dp,
    val programmeIndex: Int,
)