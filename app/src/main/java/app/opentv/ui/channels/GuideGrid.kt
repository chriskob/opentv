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
import app.opentv.ui.theme.LocalGuideChromeAlpha
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
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
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
import app.opentv.ui.components.tvFocus
import app.opentv.ui.components.logoRequest
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    selectedKeyState: State<Any?>,
    playingKey: Any? = null,
    focusRequester: FocusRequester? = null,
    onSelectRow: (ChannelsViewModel.Row) -> Unit,
    onFocusRow: (ChannelsViewModel.Row, Programme?) -> Unit,
    onLongSelectRow: (ChannelsViewModel.Row) -> Unit = {},
    onProgramme: (ChannelsViewModel.Row, Programme) -> Unit = { _, _ -> },
    onProgrammeLongPress: (ChannelsViewModel.Row, Programme) -> Unit = { _, _ -> },
    onToggleFavourite: (ChannelsViewModel.Row) -> Unit = {},
    onEnableBackScroll: () -> Unit = {},
    onJumpToLive: () -> Unit = {},
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
    /** Fired when LEFT is pressed on the leftmost programme block — opens the category rail. */
    onExitLeft: () -> Boolean = { false },
    dayOffset: Int = 0,
    /** Channel ids whose catch-up the resolver can actually build (per-channel capability) —
     *  the guide's catch-up badge shows exactly for these, never for the rest. */
    catchUpChannelIds: Set<Long> = emptySet(),
    /** (channelId, programme start) pairs the viewer has set a reminder for — each draws a bell. */
    reminderKeys: Set<Pair<Long, Long>> = emptySet(),
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
    // The caller's highlight is read through its State only from key handlers and effects (via
    // selectedKeyState.value), never during composition. Reading it here would recompose the
    // whole grid on every d-pad step — and the grid is the source of that very value.
    val density = LocalDensity.current
    val focusTargetKey = playingKey ?: rows.firstOrNull()?.key
    // Rail-preview pseudo-cursor anchor: the playing channel's row when visible, else the first.
    // Computed once here instead of once per visible row (the old in-item lookup scanned the
    // whole list for every row on every recomposition).
    val previewAnchorKey = remember(rows, playingKey) {
        rows.firstOrNull { it.key == playingKey }?.key ?: rows.firstOrNull()?.key
    }

    // Measured width of the guide area (channel column + timeline). Used to window the
    // programme blocks each row composes: only cells near the horizontal viewport are built,
    // so a 48-hour guide no longer composes hundreds of off-screen blocks per visible row.
    var guideWidthPx by remember { mutableIntStateOf(0) }

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
    // Keyed on rows/key, NOT on the focused key: this only seeds the list's initial scroll, yet
    // it used to rescan every row on each d-pad focus change (an O(n) pass per step).
    val initialFirstVisible = remember(rows, playingKey) {
        val idx = if (focusTargetKey == null) 0 else rows.indexOfFirst { it.key == focusTargetKey }.coerceAtLeast(0)
        if (idx < 6) 0 else (idx - 2).coerceAtLeast(0)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisible,
        initialFirstVisibleItemScrollOffset = 0,
    )

    // Width of the horizontally-scrollable timeline (guide area minus the fixed channel column).
    // Exposed as state and read lazily (inside the compose window below and in the keep-visible
    // callback), so an outer layout change — e.g. the main menu sliding in and widening the guide
    // — updates the viewport without recomposing every guide row on each animation frame.
    val timelineViewportPxState = remember {
        derivedStateOf {
            (guideWidthPx - with(density) { CHANNEL_COLUMN.roundToPx() }).coerceAtLeast(0)
        }
    }

    // Time span of programme blocks each row needs to compose: the viewport plus a buffer on
    // each side (so d-pad stepping and fast scrubs never outrun the built cells). Derived from
    // scroll.value but QUANTISED to a coarse step, so the value is stable while scrolling within
    // a step and rows recompose only when the window advances a step — not on every scrolled
    // pixel. A null pair means "not measured yet"; rows then compose everything so the guide is
    // never blank on the first frame.
    val composeWindow by remember(effectiveStartMillis) {
        derivedStateOf {
            val timelineViewportPx = timelineViewportPxState.value
            val densityF = density.density
            if (timelineViewportPx <= 0 || densityF <= 0f) {
                null
            } else {
                val pxPerMinute = MINUTE_DP * densityF
                val firstMinute = scroll.value / pxPerMinute
                val lastMinute = firstMinute + timelineViewportPx / pxPerMinute
                val startMillis = effectiveStartMillis +
                    (firstMinute * 60_000L).toLong() - COMPOSE_WINDOW_PAST_MILLIS
                val endMillis = effectiveStartMillis +
                    (lastMinute * 60_000L).toLong() + COMPOSE_WINDOW_FUTURE_MILLIS
                // Snap outward to the quantum: the composed set then changes at most once per
                // step rather than continuously, while the buffer keeps it safely conservative.
                val q = COMPOSE_QUANTUM_MILLIS
                (startMillis / q * q) to ((endMillis / q + 1) * q)
            }
        }
    }

    val internalFocusRequester = remember { FocusRequester() }
    val activeCellFocusRequester = focusRequester ?: internalFocusRequester
    val rowFocusRequesters = remember { mutableMapOf<Any, FocusRequester>() }

    var activeFocusedIndex by remember { mutableStateOf<Int?>(null) }
    // Focus/anchor state is exposed as State objects and read by each row through a
    // derivedStateOf. The LazyColumn item lambda no longer reads the values directly, so a
    // d-pad move no longer recomposes every visible row — only the rows whose highlight or
    // target block actually changed.
    val activeFocusedKeyState = remember { mutableStateOf<Any?>(playingKey ?: rows.firstOrNull()?.key) }
    var activeFocusedKey by activeFocusedKeyState
    val targetProgKeyState = remember { mutableStateOf<Long?>(null) }
    var targetProgKey by targetProgKeyState
    // The cursor's horizontal position, expressed as the half-hour column that contains it.
    // Always :00/:30-aligned so D-pad Left/Right steps exactly 30 minutes and Up/Down keeps the
    // same column even when a row's programme is a 1-hour (or longer) block.
    val temporalAnchorState = remember { mutableLongStateOf(halfHourColumnStart(nowMillis)) }
    var temporalAnchorMillis by temporalAnchorState
    var isNavigatingVertically by remember { mutableStateOf(false) }
    var verticalNavJob by remember { mutableStateOf<Job?>(null) }
    var focusCenterJob by remember { mutableStateOf<Job?>(null) }
    var horizontalScrollJob by remember { mutableStateOf<Job?>(null) }
    var columnStepJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // ---- TiviMate-style timeline scrub (HOLD LEFT only) ----------------------------------------
    // HOLD Left anywhere on the timeline travels back through the guide in 30-minute steps, aligned
    // to the header ruler. Forward scrubbing is deliberately not a hold gesture — TiviMate only uses
    // hold-LEFT to reach the archive — so a held RIGHT does nothing beyond its single-tap column
    // step. On release, focus re-anchors to the programme now under the viewport's left edge, so the
    // cursor is back on screen and Up/Down continues from the scrubbed time. Back returns to live
    // (HomeScreen's backScrollActive handler), which is also how the category rail is reached.
    var holdPressActive by remember { mutableStateOf(false) }
    var scrubEngaged by remember { mutableStateOf(false) }
    var holdTimeoutJob by remember { mutableStateOf<Job?>(null) }
    var scrubJob by remember { mutableStateOf<Job?>(null) }
    // Until this time, keep-visible auto-scrolls are suppressed so nothing yanks the timeline
    // while the post-scrub re-anchor settles.
    var scrubSettlingUntilMillis by remember { mutableLongStateOf(0L) }

    val engageScrub: () -> Unit = {
        if (!scrubEngaged) {
            scrubEngaged = true
            holdTimeoutJob?.cancel()
            scrubJob?.cancel()
            scrubJob = coroutineScope.launch {
                // Enter back-scroll layout: rows re-lay out from the loaded window's start and the
                // viewport is anchored on "now" first — so pressing Back afterwards restores
                // "now + playing channel".
                onEnableBackScroll()
                val frameStart = calculateMountedFrameStartTime(System.currentTimeMillis())
                val anchorPx = calculateInitialScrollOffsetPx(
                    windowStartMillis = windowStartMillis,
                    frameStartMillis = frameStart,
                    minuteDp = MINUTE_DP,
                    density = density.density,
                )
                scroll.scrollTo(anchorPx)
                delay(30)
                // One half-hour column per tick: the timeline walks back in 30-minute increments,
                // matching the guide's header ruler, rather than scrolling by pixels. Wait for the
                // back-scroll layout to give the timeline a real scroll extent first — until then
                // the range is 0 and every step would clamp straight back to the start.
                val stepPx = (HALF_HOUR_MS / 60_000.0 * MINUTE_DP * density.density).roundToInt()
                var anchored = false
                while (isActive && stepPx > 0) {
                    if (scroll.maxValue > 0) {
                        if (!anchored) {
                            scroll.scrollTo(anchorPx.coerceIn(0, scroll.maxValue))
                            anchored = true
                        }
                        val target = (scroll.value - stepPx).coerceAtLeast(0)
                        if (target != scroll.value) scroll.scrollTo(target)
                        if (target == 0) break
                    }
                    delay(SCRUB_STEP_INTERVAL_MILLIS)
                }
            }
        }
    }

    val onDirectionPressStarted: (Boolean) -> Unit = { forward ->
        holdPressActive = true
        holdTimeoutJob?.cancel()
        // Only LEFT arms the back-in-time hold. RIGHT keeps just its single-tap column step.
        if (!forward) {
            holdTimeoutJob = coroutineScope.launch {
                delay(SCRUB_ENGAGE_MILLIS)
                if (holdPressActive && !scrubEngaged) engageScrub()
            }
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
            val key = activeFocusedKey ?: playingKey ?: selectedKeyState.value ?: rows.firstOrNull()?.key
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
                // Snap to the column under the viewport's left edge, not the programme midpoint,
                // so the next Up/Down keeps the cursor in the scrubbed-to column.
                temporalAnchorMillis = halfHourColumnStart(anchorMillis)
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
            val k = targetKey ?: playingKey ?: selectedKeyState.value ?: rows.first().key
            val index = rows.indexOfFirst { it.key == k }.coerceAtLeast(0)
            val matchedRow = rows[index]
            val now = System.currentTimeMillis()
            val liveProg = matchedRow.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis }
                ?: matchedRow.now
                ?: matchedRow.programmes.firstOrNull()
            activeFocusedIndex = index
            activeFocusedKey = k
            targetProgKey = liveProg?.id
            // The live cursor sits on "now"'s half-hour column, which is also the viewport's left
            // edge in live mode — not the live programme's midpoint, which drifted into the 2pm
            // slot for a 1-hour show.
            temporalAnchorMillis = halfHourColumnStart(now)
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
            val targetKey = playingKey ?: selectedKeyState.value ?: rows.first().key
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
                    // Anchor on the playing channel when the previewed category contains it, else
                    // the first row — never a key that matches no drawn row, or the cursor vanishes.
                    val k = rows.firstOrNull { it.key == (playingKey ?: selectedKeyState.value) }?.key
                        ?: rows.first().key
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
                focusAndCenterRow(playingKey ?: selectedKeyState.value, false)
            }
        }
    }

    // Report timeline displacement so HomeScreen's Back handler knows the user is browsing
    // away from "now" (scrubbed, day-paged, or panned forward) versus sitting on live.
    //
    // Observed with snapshotFlow + distinctUntilChanged rather than keyed on `scroll.value`.
    // Keying a LaunchedEffect on the scroll position restarted (cancelled + relaunched) this
    // coroutine on EVERY scrolled pixel, and each restart pushed a state write up to HomeScreen
    // — a whole-screen recomposition per frame. Now it fires only when the threshold flips.
    val currentOnTimeShifted by rememberUpdatedState(onTimeShifted)
    LaunchedEffect(backScrollActive) {
        snapshotFlow { backScrollActive || scroll.value > 4 }
            .distinctUntilChanged()
            .collect { currentOnTimeShifted(it) }
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
        val targetKey = playingKey ?: selectedKeyState.value ?: activeFocusedKey ?: rows.firstOrNull()?.key
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

    // D-pad Left/Right moves the cursor exactly one half-hour column, so the step matches the
    // header ruler instead of varying with the focused programme's duration. A press at the first
    // column opens the category rail (same as the old leftmost-block gesture); a press past the
    // last column is absorbed as a no-op.
    val stepColumn: (Boolean) -> Boolean = { isRight ->
        if (rows.isEmpty()) {
            false
        } else {
            val rowKey = activeFocusedKey ?: selectedKeyState.value ?: rows.first().key
            val rowIndex = rows.indexOfFirst { it.key == rowKey }.coerceAtLeast(0)
            val row = rows.getOrNull(rowIndex)
            val windowEndMillis = effectiveStartMillis + HOURS_IN_WINDOW * 3600_000L
            val firstColumn = halfHourColumnStart(effectiveStartMillis)
            val lastColumn = halfHourColumnStart(windowEndMillis - HALF_HOUR_MS)
            val candidate = halfHourColumnStart(temporalAnchorMillis) +
                (if (isRight) HALF_HOUR_MS else -HALF_HOUR_MS)
            if (candidate < firstColumn) {
                // Nowhere left to go: open the category rail, like LEFT from the leftmost block.
                onExitLeft()
                true
            } else {
                val newColumn = candidate.coerceAtMost(lastColumn)
                val target = row?.let { getVerticalTargetProgram(it.programmes, newColumn) }
                temporalAnchorMillis = newColumn
                isNavigatingVertically = false
                if (row != null) {
                    activeFocusedIndex = rowIndex
                    activeFocusedKey = row.key
                    targetProgKey = target?.id
                    // The row's shared FocusRequester re-attaches to the block at the new column
                    // on the next recomposition, so give it a frame before requesting focus.
                    columnStepJob?.cancel()
                    columnStepJob = coroutineScope.launch {
                        delay(16)
                        for (attempt in 0..3) {
                            val res = runCatching { rowFocusRequesters[row.key]?.requestFocus() }
                            if (res.isSuccess) break
                            delay(20)
                        }
                    }
                }
                true
            }
        }
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
                    // TiviMate-style scrub: HOLD Left anywhere on the timeline to travel back
                    // through the guide in 30-minute steps. A single Left tap falls through and
                    // steps the cursor one column. RIGHT is tap-only — a held RIGHT is swallowed so
                    // it never walks the timeline forward.
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            onDirectionPressStarted(false)
                            // Consume the initial press: a single tap's action (step one column, or
                            // open the category rail at the first column) runs on key-up only if the
                            // hold never engaged. Letting the focused block handle this first press
                            // is what opened the rail the instant LEFT was held, before the scrub
                            // could take over.
                            true
                        } else {
                            if (holdPressActive) engageScrub()
                            holdPressActive
                        }
                    }
                    e.type == KeyEventType.KeyUp && e.key == Key.DirectionLeft -> {
                        if (scrubEngaged) {
                            releaseScrub()
                        } else {
                            holdTimeoutJob?.cancel()
                            holdPressActive = false
                            stepColumn(false)
                        }
                        true
                    }
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight -> {
                        if (e.nativeKeyEvent.repeatCount == 0) {
                            onDirectionPressStarted(true)
                            false
                        } else {
                            // Forward time-travel is not a hold gesture; swallow key repeats.
                            true
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

            Box(Modifier.weight(1f).fillMaxWidth().onSizeChanged { guideWidthPx = it.width }) {
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
                        val isPlaying = row.key == playingKey
                        val rowRequester = rowFocusRequesters.getOrPut(row.key) { FocusRequester() }
                        // During a rail preview the pseudo-cursor must sit on the ANCHOR row (the
                        // playing channel when visible, else the first row) — NOT hard-bound to
                        // index 0. Hard-binding it made the cursor jump to the top block whenever
                        // the rail opened, even when the playing channel sat mid-list.
                        val isPreviewAnchor = previewTopRow && row.key == previewAnchorKey
                        GuideRow(
                            row = row,
                            rowIndex = index,
                            totalRows = rows.size,
                            windowStartMillis = effectiveStartMillis,
                            composeStartMillis = composeWindow?.first ?: Long.MIN_VALUE,
                            composeEndMillis = composeWindow?.second ?: Long.MAX_VALUE,
                            nowMillis = currentTickMillis,
                            activeKeyState = activeFocusedKeyState,
                            fallbackKey = focusTargetKey,
                            targetProgKeyState = targetProgKeyState,
                            anchorState = temporalAnchorState,
                            scroll = scroll,
                            catchUpChannelIds = catchUpChannelIds,
                            reminderKeys = reminderKeys,
                            isSelected = isPlaying,
                            previewHighlight = isPreviewAnchor,
                            rowFocusRequester = rowRequester,
                            externalFocusRequester = activeCellFocusRequester,
                            onSelect = { onSelectRow(row) },
                            onLongSelect = { onLongSelectRow(row) },
                            onFocus = { prog ->
                                // The temporal anchor is the half-hour column, and it is only moved
                                // by an explicit horizontal step (stepColumn), a scrub release, or a
                                // jump to live — never by gaining focus. Rewriting it from the
                                // programme's midpoint here is what made the cursor slide right a
                                // column when a row contained a 1-hour block.
                                if (isNavigatingVertically) isNavigatingVertically = false
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
                                    // Use the measured timeline width (fallback to a sane estimate
                                    // before the first layout) so a focused block at the right
                                    // edge is scrolled in by the exact amount, not a guess.
                                    val viewportPx = timelineViewportPxState.value
                                    val viewportWidthDp = if (viewportPx > 0) {
                                        with(density) { viewportPx.toDp() }
                                    } else {
                                        800.dp
                                    }
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
                            onProgrammeLongPress = { programme -> onProgrammeLongPress(row, programme) },
                            onToggleFavourite = { onToggleFavourite(row) },
                            onNavigateVertical = { isDown -> handleNavigateVertical(isDown, index) },
                            onStepColumn = stepColumn,
                            onWrapToBottom = { handleWrapToBottom() },
                            onWrapToTop = { handleWrapToTop() },
                        )
                    }
                }
            }
        }

        // Live Current Time Indicator Line & Pip running from TimeHeader down through all rows.
        // Kept deliberately dim: the line crosses every row, so at full strength it competes with
        // the programme titles it is only meant to annotate. The pip stays brighter than the line,
        // so "now" is still findable at a glance without the whole column shouting.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = CHANNEL_COLUMN)
                .clipToBounds()
                .zIndex(15f)
        ) {
            // The horizontal pan is applied with graphicsLayer{translationX}, which reads
            // scroll.value at draw time, so the "now" line and pip track the scroll without
            // recomposing this overlay on every scrolled pixel. clipToBounds hides them once
            // they leave the timeline.
            val nowOffsetDp = widthFor(effectiveStartMillis, currentTickMillis)
            // Continuous vertical line running from header divider down through all rows
            Box(
                Modifier
                    .fillMaxHeight()
                    .padding(top = 28.dp)
                    .offset(x = nowOffsetDp - 0.75.dp)
                    .graphicsLayer { translationX = -scroll.value.toFloat() }
                    .width(1.5.dp)
                    .background(AppTheme.primary.copy(alpha = 0.20f))
            )
            // Small circular dot / pip on the timeline header divider
            Box(
                Modifier
                    .padding(top = 25.dp)
                    .offset(x = nowOffsetDp - 3.5.dp)
                    .graphicsLayer { translationX = -scroll.value.toFloat() }
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AppTheme.primary.copy(alpha = 0.65f))
                    .border(1.dp, AppTheme.palette.onSurface.copy(alpha = 0.35f), CircleShape)
            )
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
    selectedKeyState: State<Any?>,
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
    val selectedKey = selectedKeyState.value
    val focusTargetKey = playingKey ?: selectedKey ?: rows.firstOrNull()?.key
    // See GuideGrid: seed-only, so it must not rescan on every focus change.
    val initialFirstVisible = remember(rows, playingKey) {
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

    val clockFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val isLive = isSelected
    // See LocalGuideChromeAlpha: only the unfocused fills fade, so the cursor stays legible.
    val chromeAlpha = LocalGuideChromeAlpha.current
    // Opaque-focus themes (white cell) need dark ink while focused/selected.
    val listHighlighted = focused || isSelected

    Row(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                if (isSelected) AppTheme.palette.selectedFill
                else AppTheme.palette.chrome.copy(alpha = chromeAlpha),
                GuideCellShape,
            )
            .tvFocus(
                shape = GuideCellShape,
                selected = isSelected,
                onFocusChange = {
                    focused = it
                    if (it) onFocus(row.now)
                },
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
            color = if (listHighlighted) AppTheme.palette.onCursor else AppTheme.palette.textMuted,
            maxLines = 1,
            modifier = Modifier.width(32.dp),
        )
        // Channel Logo — enlarged for high legibility across the room
        AsyncImage(
            model = logoRequest(LocalContext.current, row.primary.logoUrl, 96),
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
                    color = if (listHighlighted) AppTheme.palette.onCursor else if (isLive) AppTheme.primary else AppTheme.palette.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isLive) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Live",
                        tint = if (listHighlighted) AppTheme.palette.onCursor else AppTheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            val nowProg = row.now
            if (nowProg != null) {
                Text(
                    text = "${clockFmt.format(Date(nowProg.startUtcMillis))}  ${nowProg.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (listHighlighted) AppTheme.palette.onCursor else AppTheme.palette.onSurfaceVariant,
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
    val chromeAlpha = LocalGuideChromeAlpha.current

    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(AppTheme.palette.chrome.copy(alpha = chromeAlpha)),
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
                        color = AppTheme.palette.textMuted,
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
                .background(AppTheme.palette.chrome.copy(alpha = chromeAlpha))
                // The 96 half-hour ruler labels are pure decoration. Marking the strip
                // semantically empty keeps them out of the accessibility tree: this box runs
                // Projectivy's accessibility service, so Compose walks that tree on every frame
                // and each node costs a HashMap allocation on the main thread.
                .clearAndSetSemantics { },
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
                            color = AppTheme.palette.onSurface,
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
    /** Visible timeline span (plus buffer) that decided which programme blocks get composed. */
    composeStartMillis: Long = Long.MIN_VALUE,
    composeEndMillis: Long = Long.MAX_VALUE,
    nowMillis: Long,
    scroll: androidx.compose.foundation.ScrollState,
    catchUpChannelIds: Set<Long> = emptySet(),
    reminderKeys: Set<Pair<Long, Long>> = emptySet(),
    previewHighlight: Boolean = false,
    isSelected: Boolean,
    /** Focused row key, read through a derivedStateOf so focus moves only recompose changed rows. */
    activeKeyState: State<Any?>,
    fallbackKey: Any? = null,
    targetProgKeyState: State<Long?>,
    anchorState: State<Long>,
    rowFocusRequester: FocusRequester,
    externalFocusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    onLongSelect: () -> Unit = {},
    onFocus: (Programme?) -> Unit,
    onProgramme: (Programme) -> Unit,
    onProgrammeLongPress: (Programme) -> Unit = {},
    onToggleFavourite: () -> Unit = {},
    onNavigateVertical: (isDown: Boolean) -> Boolean = { false },
    /** Steps the cursor one half-hour column; [Boolean] is true for RIGHT. */
    onStepColumn: (Boolean) -> Boolean = { false },
    onWrapToBottom: () -> Unit = {},
    onWrapToTop: () -> Unit = {},
) {
    // Read focus/anchor state through derivedStateOf: this row recomposes only when ITS
    // highlight or target block changes, not whenever focus moves anywhere in the grid.
    val isFocused by remember(row.key, activeKeyState, fallbackKey) {
        derivedStateOf { (activeKeyState.value ?: fallbackKey) == row.key }
    }
    val isRowHighlighted = isFocused || previewHighlight
    val chromeAlpha = LocalGuideChromeAlpha.current
    val rowTargetProgKey by remember(row.key, activeKeyState, fallbackKey, targetProgKeyState) {
        derivedStateOf {
            if ((activeKeyState.value ?: fallbackKey) == row.key) targetProgKeyState.value else null
        }
    }

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
                .background(
                    when {
                        isRowHighlighted -> AppTheme.palette.cursorFill
                        isSelected -> AppTheme.palette.selectedFill
                        else -> AppTheme.palette.chrome.copy(alpha = chromeAlpha)
                    },
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel Number (Starts at 1 in each category, sequential without duplicates)
            val displayNum = rowIndex + 1
            Text(
                "$displayNum",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                color = if (isRowHighlighted) AppTheme.palette.onCursor else AppTheme.palette.textMuted,
                maxLines = 1,
                modifier = Modifier.width(32.dp),
            )

            // Channel Logo
            AsyncImage(
                model = logoRequest(LocalContext.current, row.primary.logoUrl, 96),
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
                    color = if (isRowHighlighted) AppTheme.palette.onCursor else AppTheme.palette.onSurface,
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
                    tint = if (isRowHighlighted) AppTheme.palette.onCursor else AppTheme.primary,
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
                    tint = if (isRowHighlighted) AppTheme.palette.onCursor else AppTheme.palette.textMuted,
                    modifier = Modifier.size(14.dp),
                )
            }

            // Favorite Icon if favorited
            if (row.primary.favourite) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favorite",
                    tint = AppTheme.palette.favourite,
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
                // The shared cell FocusRequester is only attached on the truly-focused row, never
                // on the rail-preview anchor (attaching it twice would break the focus system).
                val extReq = if (isFocused) externalFocusRequester else null
                Box(
                    Modifier
                        .then(Modifier.focusRequester(rowFocusRequester))
                        .then(if (extReq != null) Modifier.focusRequester(extReq) else Modifier)
                        .width(widthFor(windowStartMillis, windowEndMillis))
                        .fillMaxSize()
                        .background(
                            if (emptyHighlighted) AppTheme.palette.cellFocus
                            else AppTheme.palette.chromeCell.copy(alpha = chromeAlpha),
                            GuideCellShape,
                        )
                        .then(
                            if (emptyHighlighted) Modifier.border(1.5.dp, AppTheme.palette.cursorBorder, GuideCellShape)
                            else Modifier.border(0.5.dp, AppTheme.palette.chromeCellBorder.copy(alpha = 0.6f), GuideCellShape),
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
                                    Key.DirectionLeft -> onStepColumn(false)
                                    Key.DirectionRight -> onStepColumn(true)
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
                        color = if (emptyHighlighted) AppTheme.palette.onCellFocus else AppTheme.palette.textMuted,
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

                        val blockWidth = widthFor(start, end)

                        if (blockWidth > 0.dp) {
                            layouts.add(
                                BlockLayout(
                                    startMillis = start,
                                    endMillis = end,
                                    blockWidth = blockWidth,
                                    programmeIndex = pIdx,
                                )
                            )
                            cursor = end
                        }
                    }
                    layouts
                }

                // Determine target block index for focus: matches the focused programme, or
                // closest to the temporal anchor, or live show, or first block. Computed in a
                // derivedStateOf that reads the anchor lazily, so moving focus elsewhere does not
                // recompose this row unless the resulting target block index actually changes.
                val targetBlockIdx by remember(blockLayouts, nowMillis) {
                    derivedStateOf {
                        val targetProgKey = rowTargetProgKey
                        val temporalAnchorMillis = anchorState.value
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
                }

                val blockFocusRequesters = remember(blockLayouts.size) {
                    List(blockLayouts.size) { FocusRequester() }
                }

                // ---- Windowed composition --------------------------------------------------
                // Only build the blocks that are on (or just off) the horizontal viewport, plus
                // the focus target. Positions stay exact because layout x is linear in time
                // (every width goes through widthFor), so a single Spacer covers every skipped
                // block and the row's total width — and therefore the shared ScrollState's
                // maxValue — is identical to a full render. The set changes only when the
                // viewport crosses a block boundary, so scrolling does not recompose rows.
                val composedIndices by remember(
                    blockLayouts,
                    targetBlockIdx,
                    composeStartMillis,
                    composeEndMillis,
                ) {
                    derivedStateOf {
                        if (blockLayouts.isEmpty() ||
                            composeEndMillis <= composeStartMillis
                        ) {
                            blockLayouts.indices.toList()
                        } else {
                            val visible = ArrayList<Int>()
                            for (i in blockLayouts.indices) {
                                val b = blockLayouts[i]
                                if (b.endMillis > composeStartMillis && b.startMillis < composeEndMillis) {
                                    visible.add(i)
                                }
                            }
                            if (targetBlockIdx in blockLayouts.indices && targetBlockIdx !in visible) {
                                visible.add(targetBlockIdx)
                                visible.sort()
                            }
                            visible
                        }
                    }
                }

                // x is emitted as an explicit cursor so skipped (off-screen) blocks are absorbed
                // into the gap spacer before the next composed block.
                var cursorMillis = windowStartMillis
                for (pOrder in composedIndices) {
                    val layout = blockLayouts[pOrder]
                    val gap = widthFor(cursorMillis, layout.startMillis)
                    if (gap > 0.dp) Spacer(Modifier.width(gap))
                    val prog = programmes[layout.programmeIndex]
                    val isNow = nowMillis in prog.startUtcMillis until prog.endUtcMillis
                    val isTarget = pOrder == targetBlockIdx
                    // TiviMate greys past slots; replayable ones (archive channel) wear the
                    // replay marker so 1-click catch-up targets are visible at a glance.
                    val isPast = prog.endUtcMillis <= nowMillis
                    val canReplay = isPast &&
                        (row.primary.tvArchive || row.primary.id in catchUpChannelIds)
                    val blockRequester = blockFocusRequesters.getOrNull(pOrder)
                    val extReq = if (isTarget && isFocused) externalFocusRequester else null
                    // A reminder is stored against one channel id, but the row groups every quality
                    // variant, so test them all — the bell then survives the primary changing.
                    val hasReminder = row.variants.any { (it.id to prog.startUtcMillis) in reminderKeys }

                    key(prog.id) {
                        ProgrammeBlock(
                            title = prog.resolvedTitle(),
                            width = layout.blockWidth,
                            isNow = isNow,
                            progress = if (isNow) prog.progressAt(nowMillis) else 0f,
                            isNew = prog.isNewEpisode(),
                            isLive = prog.isLive,
                            hasReminder = hasReminder,
                            isPast = isPast,
                            canReplay = canReplay,
                            pseudoFocused = previewHighlight && isTarget,
                            isRowHighlighted = isRowHighlighted,
                            focusRequester = blockRequester,
                            rowFocusRequester = if (isTarget) rowFocusRequester else null,
                            externalFocusRequester = extReq,
                            onFocus = { onFocus(prog) },
                            onClick = { onProgramme(prog) },
                            onLongClick = { onProgrammeLongPress(prog) },
                            onNavigateVertical = onNavigateVertical,
                            onStepColumn = onStepColumn,
                        )
                    }
                    cursorMillis = layout.endMillis
                }

                // Trailing filler block to guarantee 100% focus coverage across the entire window.
                // It is a single node, so it stays composed even when it is far off-screen.
                val lastCursor = if (blockLayouts.isEmpty()) windowStartMillis else {
                    val lastLayout = blockLayouts.last()
                    val lastProg = programmes.getOrNull(lastLayout.programmeIndex)
                    lastProg?.endUtcMillis?.coerceAtMost(windowEndMillis) ?: windowStartMillis
                }
                if (lastCursor < windowEndMillis) {
                    if (lastCursor > cursorMillis) {
                        Spacer(Modifier.width(widthFor(cursorMillis, lastCursor)))
                    }
                    val remainingWidth = widthFor(lastCursor, windowEndMillis)
                    val isFillerTarget = blockLayouts.isEmpty() || targetBlockIdx < 0
                    val extReq = if (isFillerTarget && isFocused) externalFocusRequester else null
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
                        onStepColumn = onStepColumn,
                    )
                } else if (windowEndMillis > cursorMillis) {
                    Spacer(Modifier.width(widthFor(cursorMillis, windowEndMillis)))
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

/** A tiny state chip on a programme cell — "NEW" (amber) or "LIVE" (red). */
@Composable
private fun GuideBadge(text: String, background: Color) {
    Box(
        modifier = Modifier
            .padding(end = 5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AppTheme.palette.onPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
        )
    }
}

/**
 * A programme block in the guide timeline.
 *
 * Supports directional vertical navigation using the Virtual Timestamp Anchor to preserve
 * timeline alignment across irregular program durations.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProgrammeBlock(
    title: String,
    width: Dp,
    isNow: Boolean,
    progress: Float,
    isNew: Boolean = false,
    /** XMLTV `<live/>`: this programme is a live broadcast, so it wears a LIVE chip like NEW. */
    isLive: Boolean = false,
    /** The viewer has a reminder set for this programme, so it wears a bell. */
    hasReminder: Boolean = false,
    /** Finished airing — drawn dimmed, TiviMate-style, unless focused. */
    isPast: Boolean = false,
    /** Past programme on an archive channel — wears the replay marker. */
    canReplay: Boolean = false,
    pseudoFocused: Boolean = false,
    isRowHighlighted: Boolean = false,
    focusRequester: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    externalFocusRequester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onNavigateVertical: ((isDown: Boolean) -> Boolean)? = null,
    /** Steps the cursor one half-hour column; [Boolean] is true for RIGHT. */
    onStepColumn: ((Boolean) -> Boolean)? = null,
) {
    var focused by remember { mutableStateOf(false) }

    // Pseudo-cursor: rendered like focus without stealing it (rail category preview).
    val highlighted = focused || pseudoFocused
    val chromeAlpha = LocalGuideChromeAlpha.current
    // D-pad long-OK detection. combinedClickable's onLongClick never fires for a held
    // DPAD_CENTER/ENTER from a TV remote, and a timer is the wrong shape for it anyway:
    // firing mid-hold while the key is still down leaves the remaining repeat events (and the
    // release) to land on whatever the action opened — which immediately activated the menu's
    // auto-focused first row. So the hold is measured from the system's own event timestamps
    // and fired on key-UP, and that up is consumed so the short-press click never reaches
    // `clickable` (nor activates the menu the action just opened).
    val holdAction by rememberUpdatedState(onLongClick)
    val longPressMillis = android.view.ViewConfiguration.getLongPressTimeout().toLong()

    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (rowFocusRequester != null) Modifier.focusRequester(rowFocusRequester) else Modifier)
            .then(if (externalFocusRequester != null) Modifier.focusRequester(externalFocusRequester) else Modifier)
            .width(width)
            .fillMaxSize()
            .padding(end = 1.5.dp)
            // Rounded corners come from the background/border shapes, NOT Modifier.clip: a clip
            // on every cell forced a GPU clip/saveLayer per programme block (~100+ per screen),
            // which is the bulk of the scrub/scroll render cost. A shaped background draws the
            // same rounded rect with no layer.
            .background(
                if (highlighted) AppTheme.palette.cellFocus
                else if (isPast) AppTheme.palette.chromeCell.copy(alpha = chromeAlpha * 0.55f)
                else AppTheme.palette.chromeCell.copy(alpha = chromeAlpha),
                GuideCellShape,
            )
            .then(
                if (highlighted) Modifier.border(1.5.dp, AppTheme.palette.cursorBorder, GuideCellShape)
                else Modifier.border(0.5.dp, AppTheme.palette.chromeCellBorder.copy(alpha = 0.6f), GuideCellShape)
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
                    // A single Left tap moves the cursor one half-hour column; from the first
                    // column it opens the category rail. HOLD Left is the timeline scrub, handled
                    // at the GuideGrid root.
                    if (e.type == KeyEventType.KeyDown) {
                        if (onStepColumn != null) {
                            onStepColumn(false)
                            true
                        } else false
                    } else false
                } else if (e.key == Key.DirectionRight) {
                    if (e.type == KeyEventType.KeyDown) {
                        if (onStepColumn != null) {
                            onStepColumn(true)
                            true
                        } else false
                    } else false
                } else if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) {
                    if (onLongClick == null) {
                        false
                    } else if (e.type == KeyEventType.KeyUp) {
                        // The system's own press duration, immune to our scheduling: downTime is
                        // when OK went down, eventTime when it came up.
                        val held = e.nativeKeyEvent.eventTime - e.nativeKeyEvent.downTime
                        if (held >= longPressMillis) {
                            // Consume the up: the action opens a menu whose first row is
                            // focusable, and this up must not also activate it or the cell.
                            holdAction?.invoke()
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            // One accessibility node per programme cell, not two (the cell + its title Text).
            // Projectivy's accessibility service is active on these boxes, so Compose walks the
            // semantics tree every frame — merging halves the nodes on the guide's busiest screen.
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLive) GuideBadge("LIVE", AppTheme.palette.recording)
            if (isNew) GuideBadge("NEW", AppTheme.palette.live)
            if (canReplay) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = "Replay",
                    tint = if (highlighted) AppTheme.palette.onCellFocus else AppTheme.palette.textMuted,
                    modifier = Modifier.padding(end = 4.dp).size(12.dp),
                )
            }
            if (hasReminder) {
                Icon(
                    imageVector = Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.guide_reminder_badge),
                    tint = if (highlighted) AppTheme.palette.onCellFocus else AppTheme.palette.favourite,
                    modifier = Modifier.padding(end = 4.dp).size(12.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                ),
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlighted) AppTheme.palette.onCellFocus
                else if (isPast) AppTheme.palette.textMuted
                else AppTheme.palette.onSurface,
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
/** How long LEFT must be held before the back-in-time scrub engages (a tap stays a tap). */
private const val SCRUB_ENGAGE_MILLIS = 400L
/** Delay between the discrete 30-minute steps while LEFT is held. */
private const val SCRUB_STEP_INTERVAL_MILLIS = 160L
/** How long after a scrub release keep-visible auto-scrolls stay suppressed. */
private const val SCRUB_SETTLE_MILLIS = 400L
private const val PAST_HOURS = 24
private const val FUTURE_HOURS = 24
private const val HOURS_IN_WINDOW = PAST_HOURS + FUTURE_HOURS
private const val HALF_HOUR_MS = 30 * 60 * 1000L
private val CHANNEL_COLUMN = 238.dp
private val ROW_HEIGHT = 48.dp
private val HALF_HOUR_WIDTH: Dp = (30 * MINUTE_DP).dp

/**
 * Extra timeline millis composed on each side of the visible viewport, so d-pad stepping and
 * fast scrubs never outrun the built cells (and the focused block is never decomposed).
 */
private const val COMPOSE_WINDOW_PAST_MILLIS = 60 * 60 * 1000L
private const val COMPOSE_WINDOW_FUTURE_MILLIS = 60 * 60 * 1000L
/** Coarse step the composed window snaps to, so scrolling does not recompose rows per pixel. */
private const val COMPOSE_QUANTUM_MILLIS = 15 * 60 * 1000L

private data class BlockLayout(
    val startMillis: Long,
    val endMillis: Long,
    val blockWidth: Dp,
    val programmeIndex: Int,
)