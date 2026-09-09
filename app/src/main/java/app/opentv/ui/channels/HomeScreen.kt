/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.core.findActivity
import app.opentv.core.requestIgnoreBatteryOptimizations
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.Reminder
import app.opentv.data.model.shownName
import app.opentv.reminders.ReminderScheduler
import app.opentv.player.PlaybackQueue
import app.opentv.player.PlayerController
import app.opentv.ui.ChannelsViewModel
import app.opentv.ui.player.PlayerScreen
import app.opentv.ui.RecordingBackgroundDialog
import app.opentv.ui.RecordingBackgroundPrompt
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import androidx.activity.compose.BackHandler
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live TV: a category rail on the left, the channel list on the right.
 *
 * The rail is vertical because provider category lists run to dozens of entries and a
 * horizontal chip row hides all but the first few — on a d-pad, anything you cannot see
 * you cannot reach. Favourites and All are pinned at the top.
 *
 * The list shows what is on *now* and *next* against every channel. That doubles as a
 * permanent, glanceable health indicator for the guide: when the EPG stops updating, these
 * lines say so honestly instead of going quietly stale.
 */
@Composable
fun HomeScreen(
    isTelevision: Boolean,
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onPlayCatchup: (mediaKey: String, url: String, title: String, ua: String) -> Unit = { _, _, _, _ -> },
    onOpenMainMenu: () -> Unit = {},
    onDismissMainMenu: () -> Unit = {},
    onFullScreenChanged: (Boolean) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ChannelsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    var isFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullScreen) {
        onFullScreenChanged(isFullScreen)
        if (isFullScreen) {
            onDismissMainMenu()
        }
    }
    val previewEnabled by settings.guidePreviewVideo.collectAsState()
    val channelLayout by settings.channelLayout.collectAsState()
    val guideResetOnOpen by settings.guideResetOnOpen.collectAsState()

    val categories by viewModel.visibleCategoryGroups.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val favouritesOnly by viewModel.favouritesOnly.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val showFavouritesCategory by settings.showFavouritesCategory.collectAsState()
    val showAllChannelsCategory by settings.showAllChannelsCategory.collectAsState()

    // Rail open targets — declared before the index helpers below, which read them. Set at the
    // moment LEFT/Back opens the rail: the entry the rail should scroll to and focus, and the
    // category it switches to (via the entry's onFocused, never inside the key dispatch).
    var railScrollToIndex by remember { mutableStateOf(-1) }
    var railOpenFocusKey by remember { mutableStateOf<String?>(null) }

    // Rail index for a category key (accounts for the provider section, shown only when there
    // is more than one source). Used to open the rail on the playing channel's category.
    fun railIndexForCategoryKey(key: String?): Int {
        var idx = if (sources.size > 1) 3 + sources.size else 0
        if (showFavouritesCategory) idx += 1
        if (showAllChannelsCategory) idx += 1
        if (key == null) return idx
        val groupIdx = categories.indexOfFirst { it.key == key }
        return if (groupIdx >= 0) idx + groupIdx else idx
    }

    /**
     * Index of the rail entry that currently carries [railFocusRequester] — mirroring the
     * attachment rules on the rail items below. Opening the rail scrolls here BEFORE focus is
     * requested, so focus lands on a real, composed entry instead of drifting onto the first
     * visible one (Favourites) and committing a selection change the user never made.
     * The explicit open target ([railOpenFocusKey], set by LEFT/Back) wins over the guide's
     * current selection — they differ whenever the guide sits on another category.
     */
    fun railFocusTargetIndex(): Int {
        val favBase = if (sources.size > 1) 3 + sources.size else 0
        when (railOpenFocusKey) {
            RAIL_KEY_FAVOURITES -> if (showFavouritesCategory) return favBase
            RAIL_KEY_ALL_CHANNELS -> if (showAllChannelsCategory) return favBase + if (showFavouritesCategory) 1 else 0
            null -> {}
            else -> if (categories.any { it.key == railOpenFocusKey }) return railIndexForCategoryKey(railOpenFocusKey)
        }
        val selectedOnFavourites = favouritesOnly && showFavouritesCategory
        val selectedOnAll = !favouritesOnly && selectedCategory == null && showAllChannelsCategory
        val selectionHasRailEntry = selectedOnFavourites || selectedOnAll || selectedCategory != null
        return when {
            selectedOnFavourites -> favBase
            selectedOnAll -> favBase + if (showFavouritesCategory) 1 else 0
            selectionHasRailEntry -> railIndexForCategoryKey(selectedCategory)
            showFavouritesCategory -> favBase
            showAllChannelsCategory -> favBase + 1
            else -> railIndexForCategoryKey(selectedCategory)
        }
    }
    val windowStart by viewModel.windowStartMillis.collectAsState()
    // How many hours into the past the guide is scrolled (0 = live now).
    val guideHourOffset by viewModel.guideHourOffset.collectAsState()
    // Tri-state: null = still checking the catalogue, true = channels on disk, false = confirmed
    // empty. Drives the choice between the loading spinner and a recoverable error below.
    val channelsPresent by viewModel.channelsPresent.collectAsState()

    // Two separate ideas, on purpose:
    //  - highlightedRow: where the d-pad is in the grid. Moves freely with up/down.
    //  - selectedRow: what the preview pane plays. Only changes when you press OK, so scrolling
    //    the list is calm and silent instead of re-tuning a stream on every keypress.
    var highlightedRow by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    var highlightedProgramme by remember { mutableStateOf<Programme?>(null) }
    var selectedRow by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    val previewSound by settings.guidePreviewSound.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Recording from the guide: what's capturing now, and a scope to kick a capture off.
    val activeRecordings by graph.recordingRepository.observeActive().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val recordScope = scope
    // The programme the user pressed OK on in the grid — drives the per-programme record menu.
    var recordTarget by remember { mutableStateOf<Pair<ChannelsViewModel.Row, Programme>?>(null) }
    // The channel whose OK menu (Watch / Record / Schedule) is open.
    var channelMenu by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    // When a recording is running and the user wants to watch a live stream, asks first.
    var pendingLiveChannel by remember { mutableStateOf<Channel?>(null) }

    // First time the user records or schedules while OpenTV isn't exempt from battery optimisation,
    // offer the exemption so the capture survives standby. Once per session; never blocks recording.
    var showBackgroundPrompt by remember { mutableStateOf(false) }
    fun promptBackgroundIfNeeded() {
        if (RecordingBackgroundPrompt.shouldShow(context)) {
            RecordingBackgroundPrompt.markShown()
            showBackgroundPrompt = true
        }
    }

    // ---- Category rail collapse (Change 2) ---------------------------------------------------
    // The rail is full-width while focus is on it, then slides shut once focus moves into the
    // guide (onFocusRow) so the grid gets the whole width. Pressing d-pad LEFT from the guide's
    // leftmost (channel) column slides it back and drops focus on the selected category.
    var railExpanded by remember { mutableStateOf(false) }
    var backScrollActive by remember { mutableStateOf(false) }
    // True when the guide timeline is displaced from "now" (scrubbed, day-paged, panned forward).
    var timeShifted by remember { mutableStateOf(false) }
    // Bumped to ask GuideGrid to restore the cursor onto the playing channel at "now".
    var guideRestoreTick by remember { mutableStateOf(0) }
    // Bumped when a rail category gains focus: live-previews it and scrolls the guide to its
    // first channel (channel 1), TiviMate-style.
    var guideScrollTopTick by remember { mutableStateOf(0) }
    // True while the rail previews a category: the grid tints its first row as the visible cursor.
    var railPreviewing by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (railExpanded) 240.dp else 0.dp,
        label = "railWidth",
    )
    val railFocusRequester = remember { FocusRequester() }
    val railListState = rememberLazyListState()
    // Suppresses rail-entry focus previews while the rail is expanding but the intended
    // entry has not yet taken focus. Without this, rows changing while the guide rebuilds can
    // move focus to Favourites (or another visible entry), which would select a category
    // the user did not choose. The intended entry is carried in [railOpenFocusKey].
    var suppressRailPreviewSelection by remember { mutableStateOf(false) }
    val guideFocusRequester = remember { FocusRequester() }
    // Set when LEFT reopens the rail; the effect waits for the rail to be laid out again before
    // moving focus onto it — a just-revealed node isn't focusable on the very same frame.
    var pendingRailFocus by remember { mutableStateOf(false) }
    var pendingGuideFocus by remember { mutableStateOf(false) }
    // Scroll the rail list to the target entry BEFORE focus lands on it (a just-revealed
    // off-screen row can't take focus).
    LaunchedEffect(railExpanded, railScrollToIndex) {
        if (railExpanded && railScrollToIndex >= 0) {
            railListState.scrollToItem(railScrollToIndex)
        }
    }
    LaunchedEffect(pendingRailFocus) {
        if (pendingRailFocus) {
            delay(40)
            // The scroll MUST complete before focus is requested: a far-down entry isn't
            // composed until the list reaches it, and requestFocus fails silently on an
            // unattached requester.
            //
            // Preview suppression stays on until focus lands on the intended entry: focus
            // moving to another entry while rows are being rebuilt must not select anything.
            // It clears when the intended entry ([railOpenFocusKey]) gains focus, in the rail
            // preview handler below.
            if (railExpanded && railScrollToIndex >= 0) {
                runCatching { railListState.scrollToItem(railScrollToIndex) }
            }
            for (attempt in 0..9) {
                val res = runCatching { railFocusRequester.requestFocus() }
                if (res.isSuccess) break
                delay(60)
            }
            delay(300)
            // Clear suppression unconditionally once the open window is over: focus landing on
            // an unintended entry was already blocked from selecting by the per-entry guard, and
            // leaving suppression stuck on would freeze rail preview navigation if requestFocus
            // "succeeded" on a requester that didn't actually win focus.
            suppressRailPreviewSelection = false
            pendingRailFocus = false
        }
    }
    LaunchedEffect(railExpanded) {
        if (!railExpanded) {
            // Once the rail is closed and visible, previews are always deliberate.
            suppressRailPreviewSelection = false
            // Clear the open target so the next open recomputes it: a stale scroll index or
            // focus key would silently focus a leftover entry from the previous open.
            railScrollToIndex = -1
            railOpenFocusKey = null
        }
    }
    LaunchedEffect(pendingGuideFocus) {
        if (pendingGuideFocus) {
            delay(30)
            runCatching { guideFocusRequester.requestFocus() }
            pendingGuideFocus = false
        }
    }

    // ---- Back button navigation flow ---------------------------------------------------------
    BackHandler(enabled = isFullScreen) {
        // Return the guide to the channel that was ACTUALLY playing fullscreen. selectedRow is
        // kept in lockstep with the player via PlayerScreen's onChannelChange (covers zapping),
        // so it is authoritative here; lastChannelId is only a fallback for a fresh process.
        val currentId = selectedRow?.primary?.id
            ?: highlightedRow?.primary?.id
            ?: settings.lastChannelId
        val targetRow = rows.firstOrNull { it.primary.id == currentId || it.variants.any { v -> v.id == currentId } }
            ?: selectedRow
            ?: highlightedRow
        if (targetRow != null) {
            selectedRow = targetRow
            highlightedRow = targetRow
            val now = System.currentTimeMillis()
            highlightedProgramme = targetRow.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: targetRow.now
        }
        if (currentId > 0L && rows.isNotEmpty() && rows.none { it.primary.id == currentId || it.variants.any { v -> v.id == currentId } }) {
            // The playing channel lives outside the current category rows: keep the selection
            // pointing at it with a provisional row while the matching category loads.
            scope.launch {
                val ch = graph.catalogRepository.channel(currentId)
                if (ch != null) {
                    val prov = ChannelsViewModel.Row(
                        primary = ch,
                        variants = listOf(ch),
                        now = null,
                        next = null,
                        programmes = emptyList(),
                    )
                    selectedRow = prov
                    highlightedRow = prov
                }
            }
        }
        // Always switch to the playing channel's category — not just when it's
        // missing from current rows. Fixes "Back always opens Favourites" bug.
        if (currentId > 0L) {
            viewModel.selectCategoryForChannel(currentId)
        }
        nowMillis = System.currentTimeMillis()
        viewModel.tick()
        viewModel.guideToNow()
        backScrollActive = false
        pendingGuideFocus = true
        isFullScreen = false
    }

    // 1. If channel menu / recording dialog / background prompt is open, close it.
    // 2. If browsing past catch-up programmes (backScrollActive), Back returns to the live show.
    // 3. If browsing away from the playing channel (catch-up scrub, forward walk, day-page, or a
    //    different channel highlighted), Back returns to the playing channel at "now".
    // 4. Otherwise (already on the playing channel at "now"), Back opens the Category/Channel
    //    List rail, and from there the Main Menu sidebar.
    BackHandler(enabled = !isFullScreen && (channelMenu != null || recordTarget != null || showBackgroundPrompt || pendingLiveChannel != null)) {
        channelMenu = null
        recordTarget = null
        showBackgroundPrompt = false
        pendingLiveChannel = null
    }

    val browsingAwayFromLive = backScrollActive || timeShifted || guideHourOffset != 0
    BackHandler(enabled = !isFullScreen && !railExpanded && channelMenu == null && recordTarget == null && !showBackgroundPrompt && pendingLiveChannel == null) {
        if (browsingAwayFromLive) {
            nowMillis = System.currentTimeMillis()
            viewModel.tick()
            viewModel.guideToNow()
            backScrollActive = false
            val targetRow = selectedRow ?: rows.firstOrNull()
            if (targetRow != null) {
                highlightedRow = targetRow
                val now = System.currentTimeMillis()
                highlightedProgramme = targetRow.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: targetRow.now
            }
            guideRestoreTick++
        } else {
            // Open the rail on the PLAYING channel's category (TiviMate-style), not on the last
            // focus-previewed entry.
            //
            // Do NOT call selectCategory() here. This runs inside a key-event dispatch; swapping
            // the guide's rows synchronously disposes the focused guide node mid-dispatch, and
            // the next d-pad event's 2D focus search then walks the rebuilding LazyList beyond
            // bounds row-by-row — measured on-device as a >5s ANR storm (dropbox data_app_anr
            // 2026-09-08 17:17/17:18: createItemsAfterList subcompose at 140%+ CPU). The
            // category switch instead happens in the target entry's onFocused handler once
            // focus has safely landed inside the rail.
            val playingChannel = (selectedRow ?: highlightedRow)?.primary
            val playingGroup = playingChannel?.categoryId?.let { catId ->
                categories.firstOrNull { catId in it.ids }
            }
            railOpenFocusKey = playingGroup?.key
                ?: if (favouritesOnly) RAIL_KEY_FAVOURITES else selectedCategory
            suppressRailPreviewSelection = true
            if (playingGroup != null) {
                railScrollToIndex = railIndexForCategoryKey(playingGroup.key)
            } else if (railScrollToIndex < 0) {
                // Open handlers didn't pick a target: fall back to the CURRENT selection so a
                // hidden/resolved-later selection still focuses a real entry on the first pass.
                railScrollToIndex = railFocusTargetIndex()
                railOpenFocusKey = if (favouritesOnly) RAIL_KEY_FAVOURITES else selectedCategory
            }
            railExpanded = true
            pendingRailFocus = true
        }
    }

    BackHandler(enabled = !isFullScreen && channelMenu == null && recordTarget == null && !showBackgroundPrompt && pendingLiveChannel == null && railExpanded) {
        railExpanded = false
        suppressRailPreviewSelection = false
        onOpenMainMenu()
    }

    // Re-evaluate "now" once a minute so progress bars advance without leaving the screen.
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            viewModel.tick()
            delay(60_000)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var screenResumed by remember { mutableStateOf(true) }

    // Observe back scroll requests (from long-pressing Back in guide)
    val backScrollReq by app.opentv.core.PlayRequests.backScrollRequest.collectAsState()
    LaunchedEffect(backScrollReq) {
        if (backScrollReq != null) {
            app.opentv.core.PlayRequests.consumeBackScroll()
            if (!isFullScreen) {
                backScrollActive = true
            }
        }
    }

    // Observe fullscreen requests (e.g. from long-pressing Back in guide)
    val fullScreenReq by app.opentv.core.PlayRequests.fullScreenRequest.collectAsState()
    LaunchedEffect(fullScreenReq) {
        if (fullScreenReq != null) {
            app.opentv.core.PlayRequests.consumeFullScreen()
            isFullScreen = true
        }
    }

    // Observe channel play requests
    val playRequest by app.opentv.core.PlayRequests.channelId.collectAsState()
    LaunchedEffect(playRequest, rows) {
        val reqId = playRequest ?: return@LaunchedEffect
        val match = rows.firstOrNull { it.primary.id == reqId || it.variants.any { v -> v.id == reqId } }
        if (match != null) {
            app.opentv.core.PlayRequests.consume()
            selectedRow = match
            highlightedRow = match
            isFullScreen = true
        } else {
            app.opentv.core.PlayRequests.consume()
            scope.launch {
                val ch = graph.catalogRepository.channel(reqId)
                if (ch != null) {
                    val provisional = ChannelsViewModel.Row(
                        primary = ch,
                        variants = listOf(ch),
                        now = null,
                        next = null,
                        programmes = emptyList(),
                    )
                    selectedRow = provisional
                    highlightedRow = provisional
                }
                settings.lastChannelId = reqId
                viewModel.selectCategoryForChannel(reqId)
                isFullScreen = true
            }
        }
    }

    // Synchronously compute initial active row so the first composition frame already has the
    // playing / last tuned channel key ready for GuideGrid without waiting for an async effect.
    val initialRow = remember(rows) {
        if (rows.isEmpty()) null
        else {
            val lastId = settings.lastChannelId
            val matchByLastId = if (lastId > 0) {
                rows.firstOrNull { r -> r.primary.id == lastId || r.variants.any { it.id == lastId } }
            } else null
            matchByLastId ?: rows.first()
        }
    }
    val activeSelectedRow = selectedRow ?: initialRow
    val activeHighlightedRow = if (highlightedRow != null && rows.any { it.key == highlightedRow?.key }) {
        highlightedRow
    } else {
        initialRow ?: activeSelectedRow
    }

    // While rail-previewing a category, land the guide cursor on the PLAYING channel when the
    // previewed rows contain it (TiviMate-style single highlight), else on the first row — then
    // ask the grid to center it (restoreTick; purely visual while the rail holds focus). The old
    // behavior forced the first row, which drew two highlights: top row box + playing row tint.
    LaunchedEffect(rows, railPreviewing) {
        if (railPreviewing && rows.isNotEmpty()) {
            val anchorId = (selectedRow ?: highlightedRow)?.primary?.id
            val target = rows.firstOrNull { it.primary.id == anchorId } ?: rows.firstOrNull()
            highlightedRow = target
            val now = System.currentTimeMillis()
            highlightedProgramme = target?.programmes?.firstOrNull { now in it.startUtcMillis until it.endUtcMillis }
                ?: target?.now
            guideRestoreTick++
        }
    }
    LaunchedEffect(rows) {
        val lastId = settings.lastChannelId
        if (lastId > 0L && rows.isNotEmpty() && (selectedRow == null || (selectedRow?.primary?.id == lastId && selectedRow?.programmes.isNullOrEmpty()))) {
            val match = rows.firstOrNull { it.primary.id == lastId || it.variants.any { v -> v.id == lastId } }
            if (match != null) {
                selectedRow = match
                highlightedRow = match
                val now = System.currentTimeMillis()
                highlightedProgramme = match.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: match.now
            }
        }
    }

    LaunchedEffect(rows, isFullScreen) {
        if (isFullScreen && rows.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                PlaybackQueue.items = rows.mapIndexed { index, row ->
                    PlaybackQueue.Item(row.primary.id, row.primary.shownName, row.primary.logoUrl, index + 1)
                }
            }
        }
    }

    LaunchedEffect(initialRow) {
        if (selectedRow == null && initialRow != null) {
            selectedRow = initialRow
            highlightedRow = initialRow
            val now = System.currentTimeMillis()
            highlightedProgramme = initialRow.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: initialRow.now
        }
    }

    // ---- Live preview player -----------------------------------------------------------------
    // Reused shared player for seamless transition between guide preview and full-screen.
    val previewController = remember {
        graph.livePlayer.also {
            it.player.volume = 1f
        }
    }


    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    screenResumed = true
                    previewController.player.volume = if (isFullScreen || previewSound) 1f else 0f
                    val isPlayingOrBuffering = previewController.player.currentMediaItem != null &&
                        (previewController.player.playbackState == androidx.media3.common.Player.STATE_READY ||
                         previewController.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)

                    if (!isFullScreen && previewEnabled && !isPlayingOrBuffering) {
                        val row = selectedRow ?: activeSelectedRow
                        if (row != null) {
                            val channel = row.primary
                            scope.launch {
                                val source = sources.firstOrNull { it.id == channel.sourceId }
                                    ?: graph.sourceRepository.byId(channel.sourceId)
                                val url = graph.catalogRepository.resolvePlaybackUrl(channel, source)
                                previewController.play(
                                    PlayerController.Request(
                                        url = url,
                                        title = channel.shownName,
                                        userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                                        isLive = true,
                                    ),
                                    debounce = false,
                                )
                            }
                        }
                    } else {
                        previewController.player.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    screenResumed = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The channel the preview pane is currently streaming (it shares the live player with
    // PlayerScreen). startLive uses this to recognise "fullscreen was requested for the channel
    // already previewing", so the second OK hands the running stream to the full surface without
    // a stop/restart.
    var previewedChannelId by remember { mutableStateOf<Long?>(null) }

    // Watching a live channel while a recording runs opens a second stream on the same line — which
    // cuts the recording and can get a single-connection account banned. So every jump to full-screen
    // live is funnelled through [requestLive]: with a recording active it asks first.
    fun startLive(channel: Channel) {
        onDismissMainMenu()
        PlaybackQueue.items = rows.mapIndexed { index, row ->
            PlaybackQueue.Item(row.primary.id, row.primary.shownName, row.primary.logoUrl, index + 1)
        }
        val match = rows.firstOrNull { it.primary.id == channel.id || it.variants.any { v -> v.id == channel.id } }
        if (match != null) {
            selectedRow = match
            highlightedRow = match
            highlightedProgramme = match.now
        }
        val isAlreadyPlayingThisChannel = (settings.lastChannelId == channel.id || previewedChannelId == channel.id) &&
            (previewController.player.playbackState == androidx.media3.common.Player.STATE_READY ||
             previewController.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)

        settings.lastChannelId = channel.id
        if (!isAlreadyPlayingThisChannel) {
            previewController.player.stop()
            previewController.player.clearMediaItems()
        }
        isFullScreen = true
    }
    fun requestLive(channel: Channel) {
        if (activeRecordings.isNotEmpty()) pendingLiveChannel = channel else startLive(channel)
    }

    // Hold the screen awake while Live TV is playing (either fullscreen or in preview) —
    // ensures the Android TV / Fire OS screensaver never interrupts live broadcast viewing.
    DisposableEffect(screenResumed) {
        val window = context.findActivity()?.window
        if (screenResumed) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Play the currently selected channel in the preview pane. Scrolling up and down highlights
    // other channels so you can view guide info, but the preview keeps playing the current channel
    // until a new channel is explicitly selected.
    val recordingActive = activeRecordings.isNotEmpty()
    LaunchedEffect(selectedRow?.key, previewEnabled, screenResumed, recordingActive, isFullScreen) {
        val row = selectedRow ?: activeSelectedRow ?: return@LaunchedEffect
        if (isFullScreen || !previewEnabled || !screenResumed || recordingActive) {
            return@LaunchedEffect
        }
        val channel = row.primary
        val source = sources.firstOrNull { it.id == channel.sourceId }
            ?: graph.sourceRepository.byId(channel.sourceId)
        val url = graph.catalogRepository.resolvePlaybackUrl(channel, source)
        if (previewController.currentRequest?.url == url && (previewController.player.playbackState == androidx.media3.common.Player.STATE_READY || previewController.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)) {
            previewController.player.playWhenReady = true
            previewedChannelId = channel.id
            return@LaunchedEffect
        }
        previewController.play(
            PlayerController.Request(
                url = url,
                title = channel.shownName,
                userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                isLive = true,
            ),
            debounce = false,
        )
        previewedChannelId = channel.id
    }

    LaunchedEffect(isFullScreen, previewSound) {
        previewController.player.volume = if (isFullScreen || previewSound) 1f else 0f
    }

    var previewBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val playerModifier = remember(isFullScreen, previewBounds) {
        if (isFullScreen || previewBounds.isEmpty) {
            Modifier.fillMaxSize()
        } else {
            with(density) {
                Modifier
                    .offset(
                        x = previewBounds.left.toDp(),
                        y = previewBounds.top.toDp(),
                    )
                    .size(
                        width = previewBounds.width.toDp(),
                        height = previewBounds.height.toDp(),
                    )
                    .clip(RoundedCornerShape(10.dp))
            }
        }
    }

    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // Auto-return to full screen after 1 minute of inactivity in the TV guide
    LaunchedEffect(isFullScreen, channelMenu, recordTarget, showBackgroundPrompt, pendingLiveChannel, lastInteractionTime) {
        if (!isFullScreen && channelMenu == null && recordTarget == null && !showBackgroundPrompt && pendingLiveChannel == null) {
            delay(60_000L)
            isFullScreen = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                lastInteractionTime = System.currentTimeMillis()
                if (!isFullScreen && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.MediaRewind, Key.PageUp, Key.ChannelUp -> {
                            if (guideHourOffset > -168) {
                                viewModel.nudgeGuideDay(-1)
                                true
                            } else false
                        }
                        Key.MediaFastForward, Key.PageDown, Key.ChannelDown -> {
                            if (guideHourOffset < 0) {
                                viewModel.nudgeGuideDay(1)
                                true
                            } else false
                        }
                        Key.MediaPlay, Key.MediaPlayPause -> {
                            viewModel.guideToNow()
                            backScrollActive = false
                            val active = activeSelectedRow ?: rows.firstOrNull()
                            if (active != null) {
                                highlightedRow = active
                                val now = System.currentTimeMillis()
                                highlightedProgramme = active.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: active.now
                                pendingGuideFocus = true
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // ---- TiviMate-Grade Persistent Hardware Video Surface ----
        // Created once and stays alive throughout Live TV. Smoothly positions into preview card in guide, fills screen in fullscreen.
        Box(playerModifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    (android.view.LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                        useController = false
                        keepScreenOn = true
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = previewController.player
                    }
                },
                update = { pv ->
                    if (pv.player != previewController.player) {
                        pv.player = previewController.player
                    }
                    pv.resizeMode = if (isFullScreen) settings.playerResizeMode.value else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
                onRelease = { pv ->
                    pv.player = null
                },
            )
        }

        if (isFullScreen) {
            PlayerScreen(
                channelId = (selectedRow ?: highlightedRow ?: activeSelectedRow ?: activeHighlightedRow)?.primary?.id ?: (if (settings.lastChannelId > 0L) settings.lastChannelId else null),
                onBack = {
                    // Mirror of the BackHandler above: the guide returns to the channel that was
                    // actually playing fullscreen (selectedRow is synced via onChannelChange).
                    val currentId = selectedRow?.primary?.id
                        ?: highlightedRow?.primary?.id
                        ?: settings.lastChannelId
                    val targetRow = rows.firstOrNull { it.primary.id == currentId || it.variants.any { v -> v.id == currentId } }
                        ?: selectedRow
                        ?: highlightedRow
                    if (targetRow != null) {
                        selectedRow = targetRow
                        highlightedRow = targetRow
                        val now = System.currentTimeMillis()
                        highlightedProgramme = targetRow.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: targetRow.now
                    }
                    if (currentId > 0L && rows.isNotEmpty() && rows.none { it.primary.id == currentId || it.variants.any { v -> v.id == currentId } }) {
                        scope.launch {
                            val ch = graph.catalogRepository.channel(currentId)
                            if (ch != null) {
                                val prov = ChannelsViewModel.Row(
                                    primary = ch,
                                    variants = listOf(ch),
                                    now = null,
                                    next = null,
                                    programmes = emptyList(),
                                )
                                selectedRow = prov
                                highlightedRow = prov
                            }
                        }
                    }
                    // Always switch to the playing channel's category — not just when it's
                    // missing from current rows. Fixes "Back always opens Favourites" bug.
                    if (currentId > 0L) {
                        viewModel.selectCategoryForChannel(currentId)
                    }
                    nowMillis = System.currentTimeMillis()
                    viewModel.tick()
                    viewModel.guideToNow()
                    backScrollActive = false
                    pendingGuideFocus = true
                    railExpanded = false
                    isFullScreen = false
                },
                onOpenSearch = onOpenSearch,
                onOpenMovies = { isFullScreen = false; onOpenMainMenu() },
                onOpenShows = { isFullScreen = false; onOpenMainMenu() },
                onOpenRecordings = { isFullScreen = false; onOpenMainMenu() },
                onOpenSettings = onOpenSettings,
                onPlayCatchup = onPlayCatchup,
                renderPlayerView = false,
                onChannelChange = { newId ->
                    val match = rows.firstOrNull { it.primary.id == newId || it.variants.any { v -> v.id == newId } }
                    if (match != null) {
                        selectedRow = match
                        highlightedRow = match
                        val now = System.currentTimeMillis()
                        highlightedProgramme = match.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: match.now
                    } else if (rows.isNotEmpty()) {
                        scope.launch {
                            val ch = graph.catalogRepository.channel(newId)
                            if (ch != null) {
                                val provisional = ChannelsViewModel.Row(
                                    primary = ch,
                                    variants = listOf(ch),
                                    now = null,
                                    next = null,
                                    programmes = emptyList(),
                                )
                                selectedRow = provisional
                                highlightedRow = provisional
                            }
                            viewModel.selectCategoryForChannel(newId)
                        }
                    }
                },
            )
        } else {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {

        // ---- Category rail -----------------------------------------------------------------
        // Width animates to 0 while focus is in the guide (see onFocusRow) so the grid gets the
        // whole screen; d-pad LEFT from the guide's channel column slides it back (onExitLeft…).
        Column(
            Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .clipToBounds()
                .padding(vertical = 16.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        when (e.key) {
                            Key.DirectionRight -> {
                                railPreviewing = false
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                guideRestoreTick++
                                runCatching { guideFocusRequester.requestFocus() }
                                pendingGuideFocus = true
                                true
                            }
                            Key.DirectionLeft -> {
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                onOpenMainMenu()
                                true
                            }
                            else -> false
                        }
                    } else false
                },
        ) {
            Text(
                stringResource(R.string.nav_live_tv),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            // The top "Search channels" bar was removed — Search now lives in the global nav rail.

            LazyColumn(
                state = railListState,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Provider switch — only when there's more than one source. Lets the user keep
                // several playlists and flip between them (cardiodoc's request); "All" folds them.
                if (sources.size > 1) {
                    item(key = "provider-header") {
                        Text(
                            stringResource(R.string.channels_manager_source_header),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    item(key = "provider-all") {
                        RailEntry(
                            label = stringResource(R.string.channels_manager_all_sources),
                            selected = selectedSource == null,
                            onFocused = {
                                viewModel.selectSource(null)
                                railPreviewing = true
                            },
                            onClick = {
                                viewModel.selectSource(null)
                                railPreviewing = false
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                guideRestoreTick++
                                runCatching { guideFocusRequester.requestFocus() }
                                pendingGuideFocus = true
                            },
                        )
                    }
                    items(sources, key = { "src-${it.id}" }) { source ->
                        RailEntry(
                            label = source.name,
                            selected = selectedSource == source.id,
                            onFocused = {
                                viewModel.selectSource(source.id)
                                railPreviewing = true
                            },
                            onClick = {
                                viewModel.selectSource(source.id)
                                railPreviewing = false
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                guideRestoreTick++
                                runCatching { guideFocusRequester.requestFocus() }
                                pendingGuideFocus = true
                            },
                        )
                    }
                    item(key = "provider-divider") { Spacer(Modifier.height(10.dp)) }
                }
                // The rail's FocusRequester is attached by [railOpenFocusKey] — the entry the
                // open handler chose (the playing channel's category) — NOT by the guide's
                // current selection: the two differ whenever the guide sits on Favourites/All/
                // another category, which is exactly the "rail opens on Favourites" bug. Falls
                // back to the selected entry only when no open target is set or it isn't visible.
                val selectedOnFavourites = favouritesOnly && showFavouritesCategory
                val selectedOnAll = !favouritesOnly && selectedCategory == null && showAllChannelsCategory
                val selectionHasRailEntry = selectedOnFavourites || selectedOnAll || selectedCategory != null
                val focusFirstFavourites = !selectionHasRailEntry && showFavouritesCategory
                val focusFirstAll = !selectionHasRailEntry && !showFavouritesCategory && showAllChannelsCategory
                val effectiveOpenKey = railOpenFocusKey?.takeIf { key ->
                    (key == RAIL_KEY_FAVOURITES && showFavouritesCategory) ||
                        (key == RAIL_KEY_ALL_CHANNELS && showAllChannelsCategory) ||
                        categories.any { it.key == key }
                }
                val favouritesIsTarget = effectiveOpenKey == RAIL_KEY_FAVOURITES
                val allIsTarget = effectiveOpenKey == RAIL_KEY_ALL_CHANNELS
                if (showFavouritesCategory) {
                    item {
                        RailEntry(
                            label = stringResource(R.string.guide_favourites),
                            selected = favouritesOnly,
                            onFocused = {
                                if (suppressRailPreviewSelection && railOpenFocusKey != RAIL_KEY_FAVOURITES) {
                                    return@RailEntry
                                }
                                viewModel.selectFavourites()
                                suppressRailPreviewSelection = false
                                railPreviewing = true
                            },
                            onClick = {
                                viewModel.selectFavourites()
                                railPreviewing = false
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                guideRestoreTick++
                                runCatching { guideFocusRequester.requestFocus() }
                                pendingGuideFocus = true
                            },
                            modifier = if (favouritesIsTarget || (effectiveOpenKey == null && (selectedOnFavourites || focusFirstFavourites))) Modifier.focusRequester(railFocusRequester) else Modifier,
                        )
                    }
                }
                if (showAllChannelsCategory) {
                    item {
                        val allSelected = !favouritesOnly && selectedCategory == null
                        RailEntry(
                            label = stringResource(R.string.guide_all_channels),
                            selected = allSelected,
                            onFocused = {
                                if (suppressRailPreviewSelection && railOpenFocusKey != RAIL_KEY_ALL_CHANNELS) {
                                    return@RailEntry
                                }
                                viewModel.selectCategory(null)
                                suppressRailPreviewSelection = false
                                railPreviewing = true
                            },
                            onClick = {
                                viewModel.selectCategory(null)
                                railPreviewing = false
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                guideRestoreTick++
                                runCatching { guideFocusRequester.requestFocus() }
                                pendingGuideFocus = true
                            },
                            modifier = if (allIsTarget || (effectiveOpenKey == null && (selectedOnAll || focusFirstAll))) Modifier.focusRequester(railFocusRequester) else Modifier,
                        )
                    }
                }
                items(categories, key = { it.key }) { group ->
                    val groupSelected = !favouritesOnly && selectedCategory == group.key
                    RailEntry(
                        label = group.label,
                        selected = groupSelected,
                        onFocused = {
                            if (suppressRailPreviewSelection && railOpenFocusKey != group.key) {
                                return@RailEntry
                            }
                            viewModel.selectCategory(group.key)
                            suppressRailPreviewSelection = false
                            railPreviewing = true
                        },
                        onClick = {
                            viewModel.selectCategory(group.key)
                            railPreviewing = false
                            railExpanded = false
                            suppressRailPreviewSelection = false
                            guideRestoreTick++
                            runCatching { guideFocusRequester.requestFocus() }
                            pendingGuideFocus = true
                        },
                        modifier = if (effectiveOpenKey == group.key || (effectiveOpenKey == null && (groupSelected || (!selectionHasRailEntry && !focusFirstFavourites && !focusFirstAll && categories.firstOrNull()?.key == group.key)))) Modifier.focusRequester(railFocusRequester) else Modifier,
                    )
                }
            }
        }

        // ---- Preview + guide ---------------------------------------------------------------
        Column(Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                when {
                    favouritesOnly -> NoFavouritesState()
                    isSyncing || channelsPresent == null -> LoadingState(isSyncing)
                    channelsPresent == true -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    // Nothing is syncing and the catalogue is confirmed empty. With a provider
                    // configured, the last load failed or returned nothing — surface a clear error
                    // with Retry and a way back to setup instead of spinning forever.
                    hasSources -> ChannelsErrorState(onRetry = onRefresh, onEditProvider = onAddSource)
                    else -> EmptyState(onAddSource)
                }
            } else {
                // Hand the player the list you're browsing so it can zap channel up/down.
                fun goFullscreen(channel: Channel) = requestLive(channel)

                // TiviMate guide tuning: OK on a channel (or its live programme) selects it and
                // plays it in the preview pane while the guide stays open; OK again on the
                // already-selected channel goes fullscreen. The preview pane and PlayerScreen share
                // one player, so the second OK hands the already-streaming playback to the full
                // surface without a restart. Focus never leaves the guide on the first press.
                fun tuneOrFullscreen(row: ChannelsViewModel.Row) {
                    lastInteractionTime = System.currentTimeMillis()
                    if (selectedRow?.primary?.id == row.primary.id) {
                        requestLive(row.primary)
                    } else {
                        selectedRow = row
                        highlightedRow = row
                        highlightedProgramme = row.now
                    }
                }

                // Record the highlighted channel's now-programme (bounded to its end), or stop it
                // if it's already recording. Powers the preview pane's quick record dot.
                fun recordSelected() {
                    val row = highlightedRow ?: return
                    val active = activeRecordings.firstOrNull { it.channelId == row.primary.id }
                    if (active != null) {
                        graph.recordingEngine.stop(active.id)
                        Toast.makeText(context, context.getString(R.string.rec_recording_stopped), Toast.LENGTH_SHORT).show()
                    } else {
                        recordScope.launch { graph.recordingEngine.startChannel(row.primary, row.now) }
                        Toast.makeText(context, context.getString(R.string.rec_recording_started_see_tab, row.primary.shownName), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                    }
                }

                val dayLabel = when {
                    guideHourOffset == 0 -> stringResource(R.string.guide_today)
                    guideHourOffset <= -24 -> {
                        val days = (-guideHourOffset) / 24
                        if (days == 1) "Yesterday" else "${days}d ago"
                    }
                    else -> "${-guideHourOffset}h ago"
                }
                GuidePreview(
                    row = highlightedRow,
                    programme = highlightedProgramme,
                    nowMillis = nowMillis,
                    onWatch = { (selectedRow ?: highlightedRow)?.let { goFullscreen(it.primary) } },
                    onRefresh = onRefresh,
                    onAddSource = onAddSource,
                    previewPlayer = if (previewEnabled && screenResumed && !recordingActive) previewController.player else null,
                    isRecording = highlightedRow?.primary?.id?.let { id ->
                        activeRecordings.any { it.channelId == id }
                    } == true,
                    onRecord = { recordSelected() },
                    dayLabel = dayLabel,
                    canGoPrevDay = guideHourOffset > -168,
                    onPrevDay = { viewModel.nudgeGuideDay(-1) },
                    onNextDay = { viewModel.nudgeGuideDay(1) },
                    onPreviewBoundsChanged = { rect ->
                        if (rect.width > 0 && rect.height > 0 && previewBounds != rect) {
                            previewBounds = rect
                        }
                    },
                )
                // Per-channel catch-up capability, mirroring CatchupResolver: the badge shows
                // exactly when the resolver would build a catch-up URL for that channel (archive
                // flag, catch-up template, Xtream portal source, or Xtream-format stream URL).
                // Computed OFF the main thread with a linear probe — the resolver's own regex has
                // catastrophic backtracking on non-matching URLs, and running it per channel in
                // composition blocked input dispatch for seconds (ANR).
                val catchUpChannelIds by produceState(
                    initialValue = emptySet<Long>(),
                    sources,
                    rows,
                ) {
                    val byId = sources.associateBy { it.id }
                    value = withContext(Dispatchers.Default) {
                        rows.mapNotNull { row ->
                            val ch = row.primary
                            val src = byId[ch.sourceId]
                            val capable = ch.tvArchive ||
                                !ch.cmd.isNullOrBlank() ||
                                src?.kind == app.opentv.data.model.SourceKind.XTREAM ||
                                !src?.username.isNullOrBlank() ||
                                looksLikeXtreamStream(ch.streamUrl)
                            if (capable) ch.id else null
                        }.toSet()
                    }
                }
                // TiviMate-style guide header stamp: when the guide last synced + channel count.
                val epgInfoLine = if (settings.lastGuideUpdatedMillis > 0L) {
                    "EPG updated ${formatTime(settings.lastGuideUpdatedMillis)} · ${settings.lastGuideChannelCount} channels"
                } else null
                // Shared by both layouts: focus follows the highlight and collapses the rail; LEFT
                // from the leftmost element reopens the rail (consumed only when it was hidden).
                val onFocusChannel: (ChannelsViewModel.Row, Programme?) -> Unit = remember(onDismissMainMenu) {
                    { r: ChannelsViewModel.Row, prog: Programme? ->
                        lastInteractionTime = System.currentTimeMillis()
                        highlightedRow = r
                        highlightedProgramme = prog ?: r.now
                        railExpanded = false
                        onDismissMainMenu()
                    }
                }
                val onExitLeftChannel: () -> Boolean = remember {
                    {
                        backScrollActive = false
                        if (!railExpanded) {
                            // LEFT reopens the rail on the playing channel's category too.
                            // Do NOT call selectCategory() here (see the Back-open path): the row
                            // swap must not happen inside the key dispatch. The target entry's
                            // onFocused handler performs the switch after focus lands in the rail.
                            val playingChannel = (selectedRow ?: highlightedRow)?.primary
                            val playingGroup = playingChannel?.categoryId?.let { catId ->
                                categories.firstOrNull { catId in it.ids }
                            }
                            railOpenFocusKey = playingGroup?.key
                                ?: if (favouritesOnly) RAIL_KEY_FAVOURITES else selectedCategory
                            suppressRailPreviewSelection = true
                            if (playingGroup != null) {
                                railScrollToIndex = railIndexForCategoryKey(playingGroup.key)
                            } else if (railScrollToIndex < 0) {
                                // Open handlers didn't pick a target: fall back to the CURRENT
                                // selection so a hidden/resolved-later selection still focuses a
                                // real entry on the first pass.
                                railScrollToIndex = railFocusTargetIndex()
                                railOpenFocusKey = if (favouritesOnly) RAIL_KEY_FAVOURITES else selectedCategory
                            }
                            railExpanded = true
                            pendingRailFocus = true
                            true
                        } else {
                            false
                        }
                    }
                }
                if (channelLayout == AppSettings.ChannelLayout.LIST) {
                    ChannelList(
                        rows = rows,
                        selectedKey = activeHighlightedRow?.key,
                        playingKey = activeSelectedRow?.key,
                        focusRequester = guideFocusRequester,
                        onSelectRow = { row -> tuneOrFullscreen(row) },
                        onLongSelectRow = { row -> channelMenu = row },
                        onFocusRow = onFocusChannel,
                        onToggleFavourite = { viewModel.toggleFavourite(it) },
                        onExitLeftFromChannel = onExitLeftChannel,
                        onWrapToBottom = {
                            val last = rows.lastOrNull()
                            highlightedRow = last
                            highlightedProgramme = last?.now
                        },
                        onWrapToTop = {
                            val first = rows.firstOrNull()
                            highlightedRow = first
                            highlightedProgramme = first?.now
                        },
                        nowMillis = nowMillis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    GuideGrid(
                        rows = rows,
                        windowStartMillis = windowStart,
                        dayOffset = guideHourOffset / 24,
                        catchUpChannelIds = catchUpChannelIds,
                        epgInfoLine = epgInfoLine,
                        restoreTick = guideRestoreTick,
                        onTimeShifted = { timeShifted = it },
                        scrollTopTick = guideScrollTopTick,
                        previewTopRow = railPreviewing,
                        selectedKey = activeHighlightedRow?.key,
                        playingKey = activeSelectedRow?.key,
                        focusRequester = guideFocusRequester,
                        onSelectRow = { row -> tuneOrFullscreen(row) },
                        onLongSelectRow = { row -> channelMenu = row },
                        onFocusRow = onFocusChannel,
                        onProgramme = { row, programme ->
                            val liveNow = nowMillis in programme.startUtcMillis until programme.endUtcMillis
                            val isPast = programme.endUtcMillis <= nowMillis
                            if (liveNow) {
                                tuneOrFullscreen(row)
                            } else if (isPast) {
                                recordScope.launch {
                                    val source = graph.sourceRepository.byId(row.primary.sourceId)
                                    val url = if (source != null) app.opentv.core.CatchupResolver.resolve(source, row.primary, programme) else null
                                    if (url != null) {
                                        previewController.player.pause()
                                        previewController.player.stop()
                                        Toast.makeText(context, "Playing catch-up: ${programme.title}", Toast.LENGTH_SHORT).show()
                                        onPlayCatchup(
                                            "catchup:${row.primary.id}:${programme.startUtcMillis}",
                                            url,
                                            "${row.primary.shownName} — ${programme.title}",
                                            source?.userAgent ?: "OpenTV",
                                        )
                                    } else {
                                        recordTarget = row to programme
                                    }
                                }
                            } else {
                                recordTarget = row to programme
                            }
                        },
                        onToggleFavourite = { viewModel.toggleFavourite(it) },
                        onEnableBackScroll = { backScrollActive = true },
                        onJumpToLive = {
                            nowMillis = System.currentTimeMillis()
                            viewModel.tick()
                            viewModel.guideToNow()
                            backScrollActive = false
                            val active = activeSelectedRow ?: rows.firstOrNull()
                            if (active != null) {
                                highlightedRow = active
                                val now = System.currentTimeMillis()
                                highlightedProgramme = active.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: active.now
                                pendingGuideFocus = true
                            }
                        },
                        highlightedProgramme = highlightedProgramme,
                        onWrapToBottom = {
                            val last = rows.lastOrNull()
                            highlightedRow = last
                            highlightedProgramme = last?.now
                        },
                        onWrapToTop = {
                            val first = rows.firstOrNull()
                            highlightedRow = first
                            highlightedProgramme = first?.now
                        },
                        nowMillis = nowMillis,
                        backScrollActive = backScrollActive,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

    // The record menu for a programme picked in the grid.
    recordTarget?.let { (targetRow, programme) ->
        val channel = targetRow.primary
        // Which quality actually gets recorded. Defaults to the best variant, but the user can pick
        // (Sky Q's "record HD or SD"). Each variant channel carries its own stream URL, so the pick
        // flows straight through to the capture. Only surfaced when the channel has more than one.
        var chosenVariant by remember(targetRow.primary.id) { mutableStateOf(targetRow.primary) }
        // Which provider records it, when this channel exists on more than one. Recording from a
        // second provider's account is what lets you keep watching on the first — the only real way
        // around a single-connection provider (something even TiviMate can't do).
        var sourceOptions by remember(channel.id) { mutableStateOf<List<Channel>>(emptyList()) }
        LaunchedEffect(channel.id) { sourceOptions = graph.catalogRepository.recordSourceOptions(channel) }
        val multiSource = sourceOptions.size > 1
        LaunchedEffect(sourceOptions) {
            if (sourceOptions.size > 1) {
                chosenVariant = sourceOptions.firstOrNull { it.sourceId == channel.sourceId } ?: sourceOptions.first()
            }
        }
        val liveNow = nowMillis in programme.startUtcMillis until programme.endUtcMillis
        val recordingThis = activeRecordings.firstOrNull { it.channelId == channel.id }
        Dialog(onDismissRequest = { recordTarget = null }) {
            Column(
                Modifier
                    .width(440.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp),
            ) {
                Text(
                    programme.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${formatTime(programme.startUtcMillis)}–${formatTime(programme.endUtcMillis)}   ${channel.shownName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                if (multiSource) {
                    // Record from which provider — keeps the other account free to watch on.
                    Text(
                        stringResource(R.string.rec_record_from),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sourceOptions.forEach { opt ->
                            QualityChip(
                                label = sources.firstOrNull { it.id == opt.sourceId }?.name
                                    ?: stringResource(R.string.rec_source_unknown),
                                selected = opt.id == chosenVariant.id,
                                onClick = { chosenVariant = opt },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                } else if (targetRow.variants.size > 1) {
                    Text(
                        stringResource(R.string.rec_quality_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        targetRow.variants.forEach { variant ->
                            QualityChip(
                                label = variant.qualityLabel.ifBlank { stringResource(R.string.rec_quality_sd) },
                                selected = variant.id == chosenVariant.id,
                                onClick = { chosenVariant = variant },
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                val isPast = programme.endUtcMillis <= nowMillis
                // If programme is past or currently airing, offer watching from start via catch-up archive
                if (isPast || liveNow) {
                    RecordActionRow(stringResource(R.string.guide_watch_from_start), primary = true) {
                        val targetChannel = chosenVariant
                        val targetProg = programme
                        recordScope.launch {
                            val source = withContext(Dispatchers.IO) { graph.sourceRepository.byId(targetChannel.sourceId) }
                            val url = if (source != null) {
                                withContext(Dispatchers.IO) { app.opentv.core.CatchupResolver.resolve(source, targetChannel, targetProg) }
                            } else null
                            if (url == null) {
                                Toast.makeText(context, context.getString(R.string.guide_catchup_link_failed), Toast.LENGTH_SHORT).show()
                            } else {
                                previewController.player.pause()
                                previewController.player.stop()
                                onPlayCatchup(
                                    "catchup:${targetChannel.id}:${targetProg.startUtcMillis}",
                                    url,
                                    "${targetChannel.shownName} — ${targetProg.title}",
                                    source?.userAgent ?: "OpenTV",
                                )
                            }
                        }
                        recordTarget = null
                    }
                }

                when {
                    liveNow && recordingThis != null -> RecordActionRow(stringResource(R.string.rec_stop_recording), primary = false) {
                        graph.recordingEngine.stop(recordingThis.id)
                        Toast.makeText(context, context.getString(R.string.rec_recording_stopped), Toast.LENGTH_SHORT).show()
                        recordTarget = null
                    }
                    liveNow -> RecordActionRow(stringResource(R.string.rec_record_now), primary = false) {
                        recordScope.launch { graph.recordingEngine.startChannel(chosenVariant, programme) }
                        Toast.makeText(context, context.getString(R.string.rec_recording_channel, channel.shownName), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                        recordTarget = null
                    }
                    !isPast -> RecordActionRow(stringResource(R.string.rec_schedule_recording), primary = true) {
                        recordScope.launch { graph.recordingEngine.scheduleProgramme(chosenVariant, programme) }
                        Toast.makeText(context, context.getString(R.string.rec_scheduled_title, programme.title), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                        recordTarget = null
                    }
                }

                // Reminders — only for something that hasn't started yet.
                if (!isPast) {
                    var reminderSet by remember(channel.id, programme.startUtcMillis) {
                        mutableStateOf<Boolean?>(null)
                    }
                    LaunchedEffect(channel.id, programme.startUtcMillis) {
                        reminderSet =
                            graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis) != null
                    }
                    if (reminderSet == true) {
                        RecordActionRow(stringResource(R.string.guide_cancel_reminder)) {
                            recordScope.launch {
                                graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis)?.let {
                                    ReminderScheduler.cancel(context, it.id)
                                    graph.reminderRepository.delete(it.id)
                                }
                            }
                            Toast.makeText(context, context.getString(R.string.guide_reminder_removed), Toast.LENGTH_SHORT).show()
                            recordTarget = null
                        }
                    } else {
                        RecordActionRow(stringResource(R.string.guide_remind_me)) {
                            recordScope.launch { setReminder(graph, context, channel, programme, autoTune = false) }
                            Toast.makeText(context, context.getString(R.string.guide_reminder_set, programme.title), Toast.LENGTH_LONG).show()
                            recordTarget = null
                        }
                        RecordActionRow(stringResource(R.string.guide_auto_switch)) {
                            recordScope.launch { setReminder(graph, context, channel, programme, autoTune = true) }
                            Toast.makeText(context, context.getString(R.string.guide_will_switch, channel.shownName), Toast.LENGTH_LONG).show()
                            recordTarget = null
                        }
                    }
                }
                if (!isPast) {
                    RecordActionRow(stringResource(R.string.rec_record_series)) {
                        recordScope.launch {
                            graph.recordingEngine.recordSeries(channel, programme, targetRow.programmes)
                        }
                        Toast.makeText(context, context.getString(R.string.rec_series_recording_set, programme.title), Toast.LENGTH_LONG).show()
                        promptBackgroundIfNeeded()
                        recordTarget = null
                    }
                }
                RecordActionRow("Watch Live Channel") {
                    recordTarget = null
                    requestLive(channel)
                }
                RecordActionRow(stringResource(R.string.common_cancel)) { recordTarget = null }
            }
        }
    }

    // Single-connection guard: confirm before opening a live stream that would fight a recording.
    pendingLiveChannel?.let { liveChannel ->
        val recTitle = activeRecordings.firstOrNull()?.title.orEmpty()
        Dialog(onDismissRequest = { pendingLiveChannel = null }) {
            Column(
                Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
            ) {
                Text(
                    stringResource(R.string.rec_live_warn_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.rec_live_warn_body, recTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                RecordActionRow(stringResource(R.string.rec_live_warn_watch)) {
                    pendingLiveChannel = null
                    activeRecordings.forEach { graph.recordingEngine.stop(it.id) }
                    startLive(liveChannel)
                }
                RecordActionRow(stringResource(R.string.rec_live_warn_keep)) { pendingLiveChannel = null }
            }
        }
    }

    // The channel menu — opened by pressing OK on a channel. Watch, record what's on now, schedule
    // a later programme, or record the whole series. A plain vertical list, so it's reliable on any
    // remote — no fiddly timeline navigation needed.
    channelMenu?.let { menuRow ->
        val channel = menuRow.primary
        val nowProg = menuRow.now
        val recordingThis = activeRecordings.firstOrNull { it.channelId == channel.id }
        val upcoming = menuRow.programmes.filter { it.startUtcMillis > nowMillis }.take(8)
        Dialog(onDismissRequest = { channelMenu = null }) {
            Column(
                Modifier
                    .width(480.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp),
            ) {
                Text(
                    channel.shownName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nowProg?.let {
                    Text(
                        stringResource(R.string.guide_now_title, it.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(14.dp))

                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RecordActionRow(stringResource(R.string.guide_watch)) {
                        channelMenu = null
                        requestLive(channel)
                    }
                    if (nowProg != null) {
                        RecordActionRow(stringResource(R.string.guide_watch_from_start)) {
                            channelMenu = null
                            val targetProg = nowProg
                            val targetChannel = channel
                            recordScope.launch {
                                val source = withContext(Dispatchers.IO) { graph.sourceRepository.byId(targetChannel.sourceId) }
                                val url = if (source != null) {
                                    withContext(Dispatchers.IO) { app.opentv.core.CatchupResolver.resolve(source, targetChannel, targetProg) }
                                } else null
                                if (url != null) {
                                    previewController.player.pause()
                                    previewController.player.stop()
                                    onPlayCatchup(
                                        "catchup:${targetChannel.id}:${targetProg.startUtcMillis}",
                                        url,
                                        "${targetChannel.shownName} — ${targetProg.title}",
                                        source?.userAgent ?: "OpenTV",
                                    )
                                } else {
                                    Toast.makeText(context, context.getString(R.string.guide_catchup_link_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    RecordActionRow(stringResource(R.string.guide_open_external)) {
                        channelMenu = null
                        recordScope.launch {
                            val ua = graph.sourceRepository.byId(channel.sourceId)?.userAgent ?: "OpenTV/0.1 (Android)"
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(android.net.Uri.parse(channel.streamUrl), "video/*")
                                putExtra("title", channel.shownName)
                                // MX Player / VLC read the User-Agent from this header extra.
                                putExtra("headers", arrayOf("User-Agent", ua))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(intent, context.getString(R.string.guide_play_channel_with))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure {
                                Toast.makeText(context, context.getString(R.string.guide_no_external_player), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    if (recordingThis != null) {
                        RecordActionRow(stringResource(R.string.rec_stop_recording), primary = true) {
                            graph.recordingEngine.stop(recordingThis.id)
                            Toast.makeText(context, context.getString(R.string.rec_recording_stopped), Toast.LENGTH_SHORT).show()
                            channelMenu = null
                        }
                    } else {
                        RecordActionRow(stringResource(R.string.guide_record_now_playing), primary = true) {
                            recordScope.launch { graph.recordingEngine.startChannel(channel, nowProg) }
                            Toast.makeText(context, context.getString(R.string.rec_recording_started_see_tab, channel.shownName), Toast.LENGTH_LONG).show()
                            promptBackgroundIfNeeded()
                            channelMenu = null
                        }
                    }
                    if (nowProg != null) {
                        RecordActionRow(stringResource(R.string.rec_record_series_named, nowProg.title)) {
                            recordScope.launch {
                                graph.recordingEngine.recordSeries(channel, nowProg, menuRow.programmes)
                            }
                            Toast.makeText(context, context.getString(R.string.rec_series_recording_set, nowProg.title), Toast.LENGTH_LONG).show()
                            promptBackgroundIfNeeded()
                            channelMenu = null
                        }
                    }
                    if (upcoming.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.guide_later_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        upcoming.forEach { programme ->
                            RecordActionRow("${formatTime(programme.startUtcMillis)}   ${programme.title}") {
                                // Open the programme dialog so the choice is record, remind or auto-switch —
                                // not a surprise one-tap recording.
                                channelMenu = null
                                recordTarget = menuRow to programme
                            }
                        }
                    }
                    RecordActionRow(stringResource(R.string.common_cancel)) { channelMenu = null }
                }
            }
        }
    }
    }

    if (showBackgroundPrompt) {
        RecordingBackgroundDialog(
            onAllow = {
                showBackgroundPrompt = false
                context.requestIgnoreBatteryOptimizations()
            },
            onDismiss = { showBackgroundPrompt = false },
        )
    }
}

/** Inserts a reminder for a future programme and arms its alarm. No-op if one already exists. */
private suspend fun setReminder(
    graph: ServiceLocator.Graph,
    context: android.content.Context,
    channel: Channel,
    programme: Programme,
    autoTune: Boolean,
) {
    if (graph.reminderRepository.forProgramme(channel.id, programme.startUtcMillis) != null) return
    val id = graph.reminderRepository.insert(
        Reminder(
            channelId = channel.id,
            channelName = channel.shownName,
            logoUrl = channel.logoUrl,
            title = programme.title,
            startUtcMillis = programme.startUtcMillis,
            endUtcMillis = programme.endUtcMillis,
            autoTune = autoTune,
            createdAtMillis = System.currentTimeMillis(),
        ),
    )
    val triggerTime = programme.startUtcMillis.coerceAtLeast(System.currentTimeMillis() + 1000L)
    ReminderScheduler.set(context, id, triggerTime)
    if (!ReminderScheduler.canScheduleExact(context)) {
        ReminderScheduler.promptExactAlarmPermission(context)
    }
}

@Composable
private fun RecordActionRow(label: String, primary: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        primary -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        primary -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** A small focusable quality pill (FHD / HD / SD…) for the record dialog's "record HD or SD" choice. */
@Composable
private fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = fg,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

// Stable rail-entry keys: category groups use their non-empty group key; favourites and the
// all-channels pseudo entries use these sentinels while preview suppression is active.
private const val RAIL_KEY_FAVOURITES = "rail:favourites"
private const val RAIL_KEY_ALL_CHANNELS = "rail:all-channels"

@Composable
private fun RailEntry(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
        color = if (focused) Color(0xFF10171E)
        else if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused?.invoke()
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChannelRow(
    row: ChannelsViewModel.Row,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val background =
        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.background

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = row.primary.logoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            // Logo, name, guide — and nothing else. Quality is a playback decision;
            // its switch lives in the player, not as clutter on every row.
            Text(
                row.primary.shownName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.now?.let { "${formatTime(it.startUtcMillis)}  ${it.title}" }
                    // Honest rather than blank. A user who sees this on every channel knows
                    // the guide is the problem, not their provider.
                    ?: stringResource(R.string.guide_no_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.now?.let { programme ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { programme.progressAt(now) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            row.next?.let { next ->
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.guide_next_prefix, formatTime(next.startUtcMillis), next.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (row.primary.favourite) Icons.Default.Star
                else Icons.Outlined.StarOutline,
                contentDescription = if (row.primary.favourite) stringResource(R.string.common_remove_favourite) else stringResource(R.string.common_favourite),
            )
        }
    }
}

@Composable
private fun LoadingState(isSyncing: Boolean) {
    val s by app.opentv.core.StatusBus.message.collectAsState()
    val p by app.opentv.core.StatusBus.progress.collectAsState()
    val status = s
    val progress = p
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            if (progress == null) {
                CircularProgressIndicator()
            } else {
                Text(
                    "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.guide_loading_channels), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    // Actively assembling — this is work in progress, not a failure.
                    status != null -> status
                    isSyncing -> stringResource(R.string.guide_fetching_desc)
                    // Channels are already on the device; the guide is being built from them.
                    // A big provider takes a moment (longer on this debug build) — not a failure.
                    else -> stringResource(R.string.guide_building_desc)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (progress != null) {
                Spacer(Modifier.height(20.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
        }
    }
}

@Composable
private fun NoFavouritesState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.guide_no_favourites_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_no_favourites_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shown when a provider is configured but there are no channels to display and nothing is
 * syncing — i.e. the last catalogue load failed or came back empty. Replaces the endless
 * "Loading your channels" spinner with something the user can act on.
 */
@Composable
private fun ChannelsErrorState(onRetry: () -> Unit, onEditProvider: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Text(
                stringResource(R.string.guide_load_failed_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_load_failed_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(onClick = onRetry) {
                    Text(stringResource(R.string.common_try_again))
                }
                androidx.compose.material3.OutlinedButton(onClick = onEditProvider) {
                    Text(stringResource(R.string.guide_edit_provider))
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.guide_no_channels_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_add_provider_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAddSource) { Text(stringResource(R.string.guide_add_provider_button)) }
        }
    }
}

/** UTC in the database, device zone on screen. Converted here and nowhere else. */
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

private fun formatTime(utcMillis: Long): String = timeFormat.format(Date(utcMillis))

/**
 * Linear, backtracking-free probe for Xtream-format stream URLs (`host/[live/]user/pass/id`).
 * Mirrors CatchupResolver.XTREAM_URL_REGEX without its catastrophic-backtracking risk on
 * non-matching inputs — this runs once per channel over the whole list, off the main thread.
 */
private fun looksLikeXtreamStream(url: String): Boolean {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd <= 0 || !url.startsWith("http")) return false
    val path = url.substring(schemeEnd + 3).substringBefore('?').substringBefore('#').trimEnd('/')
    val segments = path.split('/')
    if (segments.size < 4) return false
    val afterHost = if (segments[1].equals("live", ignoreCase = true)) segments.drop(2) else segments.drop(1)
    return afterHost.size >= 3 && afterHost.last().isNotBlank()
}
