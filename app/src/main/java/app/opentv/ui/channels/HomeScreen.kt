/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.OptIn
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.CatchupPlayback
import app.opentv.core.CatchupResolver
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
import app.opentv.ui.components.tvFocus
import app.opentv.ui.theme.AppTheme
import app.opentv.ui.theme.LocalGuideChromeAlpha
import app.opentv.ui.RecordingBackgroundDialog
import app.opentv.ui.RecordingBackgroundPrompt
import androidx.media3.common.util.UnstableApi
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
@OptIn(UnstableApi::class)
@Composable
fun HomeScreen(
    isTelevision: Boolean,
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMainMenu: () -> Unit = {},
    onDismissMainMenu: () -> Unit = {},
    /** Whether the main nav rail (Movies/Shows/Recordings/Settings) is currently open. */
    mainMenuVisible: Boolean = false,
    onFullScreenChanged: (Boolean) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenMultiview: (Long) -> Unit = {},
    viewModel: ChannelsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    var isFullScreen by remember { mutableStateOf(false) }
    // True for the ~300ms the picture takes to shrink out of full screen. The guide is composed
    // underneath during the shrink (guideVisible below) so the video lands into a live card;
    // focus stays locked until the shrink ends (see pendingGuideFocus effect).
    var shrinkingFromFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isFullScreen) {
        onFullScreenChanged(isFullScreen)
        if (isFullScreen) {
            onDismissMainMenu()
        }
    }
    val previewEnabled by settings.guidePreviewVideo.collectAsState()
    val playerResizeMode by settings.playerResizeMode.collectAsState()
    val channelLayout by settings.channelLayout.collectAsState()
    val guideResetOnOpen by settings.guideResetOnOpen.collectAsState()

    val categories by viewModel.visibleCategoryGroups.collectAsState()
    val sections by viewModel.categorySections.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val favouritesOnly by viewModel.favouritesOnly.collectAsState()
    // Kept for the playback-URL and record-picker lookups — no longer drives the sidebar, which is
    // a tree of playlists now rather than a provider filter plus a flat category list.
    val sources by viewModel.sources.collectAsState()
    val showFavouritesCategory by settings.showFavouritesCategory.collectAsState()
    val collapsedSources by settings.collapsedSources.collectAsState()

    // Rail open targets — declared before the index helpers below, which read them. Set at the
    // moment LEFT/Back opens the rail: the entry the rail should scroll to and focus, and the
    // category it switches to (via the entry's onFocused, never inside the key dispatch).
    var railScrollToIndex by remember { mutableStateOf(-1) }
    var railOpenFocusKey by remember { mutableStateOf<String?>(null) }

    // The sidebar flattened into exactly the rows it draws — favourites, then one header per
    // playlist with its categories beneath — so scroll and focus indices are read off the real list
    // instead of being recomputed from counts. That arithmetic had to add three rows for the
    // provider section, and a collapsed group changes the row count again; deriving the rows once
    // and indexing them cannot drift the way a hand-maintained offset does.
    val railRows: List<RailRow> = remember(showFavouritesCategory, sections, collapsedSources) {
        buildList {
            if (showFavouritesCategory) add(RailRow.Favourites)
            sections.forEach { section ->
                val expanded = section.source.id.toString() !in collapsedSources
                add(RailRow.SourceHeader(section.source.id, section.source.name, expanded))
                if (expanded) section.groups.forEach { add(RailRow.Category(section.source.id, it)) }
            }
        }
    }

    /** Index of the row carrying a category key, or -1 when it is not currently drawn. */
    fun railIndexForCategoryKey(key: String?): Int {
        if (key == null) return -1
        return railRows.indexOfFirst { it is RailRow.Category && it.group.key == key }
    }

    /**
     * Index of the rail row whose [railFocusRequesters] entry should take focus and be scrolled to
     * when the rail opens — mirroring the attachment rule on the rows below. Opening the rail
     * scrolls here BEFORE
     * focus is requested, so focus lands on a real, composed entry instead of drifting onto the first
     * visible one (Favourites) and committing a selection change the user never made.
     *
     * The explicit open target ([railOpenFocusKey], set by LEFT/Back) wins over the guide's current
     * selection — they differ whenever the guide sits on another category, which is exactly the
     * "rail opens on Favourites" bug.
     */
    fun railFocusTargetIndex(): Int {
        when (val target = railOpenFocusKey) {
            null -> {}
            RAIL_KEY_FAVOURITES -> railRows.indexOfFirst { it is RailRow.Favourites }
                .takeIf { it >= 0 }?.let { return it }
            else -> railRows.indexOfFirst { it is RailRow.Category && it.group.key == target }
                .takeIf { it >= 0 }?.let { return it }
        }
        val selected = railRows.indexOfFirst { it is RailRow.Category && it.group.key == selectedCategory }
        if (selected >= 0) return selected
        if (favouritesOnly) {
            railRows.indexOfFirst { it is RailRow.Favourites }.takeIf { it >= 0 }?.let { return it }
        }
        return 0
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
    // Held as explicit State objects so the highlight can be passed DOWN to the preview and the
    // grid without this screen reading the value during composition. Reading it here recomposed
    // the whole screen on every d-pad step; now only the preview card and the affected rows do.
    val highlightedRowState = remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    var highlightedRow by highlightedRowState
    val highlightedProgrammeState = remember { mutableStateOf<Programme?>(null) }
    var highlightedProgramme by highlightedProgrammeState
    var selectedRow by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    val previewSound by settings.guidePreviewSound.collectAsState()
     var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

     LaunchedEffect(Unit) {
         snapshotFlow { highlightedRowState.value?.key }
             .distinctUntilChanged()
             .collectLatest { key ->
                 if (key == null) return@collectLatest
                 viewModel.epgRows
                     .map { it[key] }
                     .distinctUntilChanged()
                     .collect { hydrated ->
                         if (hydrated != null && highlightedRowState.value?.key == key) {
                             highlightedRow = hydrated
                             val now = System.currentTimeMillis()
                             highlightedProgramme = hydrated.programmes.firstOrNull {
                                 now in it.startUtcMillis until it.endUtcMillis
                             } ?: hydrated.now
                         }
                     }
             }
     }



    // The archive programme the shared live player is currently on (null = live). Set when a past
    // programme is picked in the guide; the same player surface shows it in the preview and
    // fullscreen, and Back keeps the guide at the scrubbed time until the viewer returns to live.
    var catchup by remember { mutableStateOf<CatchupPlayback?>(null) }

    // Recording from the guide: what's capturing now, and a scope to kick a capture off.
    val activeRecordings by graph.recordingRepository.observeActive().collectAsState(initial = emptyList())
    // Programme reminders, so the guide can stamp a bell on every block the viewer has a reminder
    // for. Keyed by (channel, slot) — the same pair a reminder is de-duped on.
    val reminders by graph.reminderRepository.observeAll().collectAsState(initial = emptyList())
    val reminderKeys = remember(reminders) {
        reminders.map { it.channelId to it.startUtcMillis }.toSet()
    }
    val scope = rememberCoroutineScope()

    /**
     * Leaves full screen the way the picture should: it shrinks into the preview card while the
     * guide is already visible underneath. Focus is handed over once the shrink lands, so d-pad
     * input cannot escape into a half-built guide mid-animation.
     */
    fun leaveFullScreen() {
        if (shrinkingFromFullScreen || !isFullScreen) {
            isFullScreen = false
            return
        }
        shrinkingFromFullScreen = true
        scope.launch {
            delay(PLAYER_TRANSITION_MILLIS.toLong())
            isFullScreen = false
            shrinkingFromFullScreen = false
        }
    }
    val recordScope = scope
    // The programme the user pressed OK on in the grid — drives the per-programme record menu.
    var recordTarget by remember { mutableStateOf<Pair<ChannelsViewModel.Row, Programme>?>(null) }
    // Idle auto-close for that menu. Every d-pad key press restarts the countdown; when it runs
    // out unanswered, the menu dismisses itself.
    var menuIdleTick by remember { mutableIntStateOf(0) }
    fun menuIdleReset() { menuIdleTick++ }
    LaunchedEffect(recordTarget, menuIdleTick) {
        if (recordTarget != null) {
            delay(MENU_IDLE_TIMEOUT_MILLIS)
            recordTarget = null
        }
    }
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
    // Category previews are DEBOUNCED through a plain Job holder, deliberately NOT a state read
    // during composition: a state key here recomposed this whole (very large) screen on every
    // focused rail entry, so walking the list still stuttered even with the rebuild itself
    // debounced. Each preview restarts the guide pipeline, so only the entry the user settles on
    // is previewed; fast walking just moves the cursor.
    var railPreviewJob by remember { mutableStateOf<Job?>(null) }
    // One FocusRequester per rail entry, keyed by row, so d-pad up/down can be driven by index
    // (see moveRailFocus below). A single shared requester could only ever point at the entry the
    // open handler chose, so vertical moves fell through to Compose's 2D focus search; when the
    // next entry wasn't composed yet (list scrolling, a group collapsing) that search walked off
    // the bottom of the LazyColumn into the guide, which collapsed the rail mid-scroll and dropped
    // the viewer back on the guide.
    val railFocusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    // The rail entry that currently holds focus — the origin for the next up/down move.
    var railFocusedKey by remember { mutableStateOf<String?>(null) }
    var railNavJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val railListState = rememberLazyListState()
    // Suppresses rail-entry focus previews while the rail is expanding but the intended
    // entry has not yet taken focus. Without this, rows changing while the guide rebuilds can
    // move focus to Favourites (or another visible entry), which would select a category
    // the user did not choose. The intended entry is carried in [railOpenFocusKey].
    var suppressRailPreviewSelection by remember { mutableStateOf(false) }
    val guideFocusRequester = remember { FocusRequester() }
    val guideListState = rememberLazyListState()
    val guideScrollState = rememberScrollState()

    // Set when LEFT reopens the rail; the effect waits for the rail to be laid out again before
    // moving focus onto it — a just-revealed node isn't focusable on the very same frame.
    var pendingRailFocus by remember { mutableStateOf(false) }
    var pendingGuideFocus by remember { mutableStateOf(false) }

    /**
     * Steps d-pad focus through the rail by index, wrapping at both ends, instead of leaving
     * up/down to Compose's 2D focus search. That search is what made the rail "randomly exit to
     * the guide": pressing down when the following entry had not been composed (it sat just past
     * the viewport, or a group had just collapsed) found no focusable below within the rail, so it
     * picked the nearest node in the guide column instead. Every guide row's focus closes the rail
     * (onFocusChannel), so the viewer was thrown out of the category list mid-scroll. Consuming the
     * move and requesting the neighbour ourselves keeps focus in the rail and loops top↔bottom.
     */
    fun moveRailFocus(isDown: Boolean) {
        if (railRows.isEmpty()) return
        val current = railRows.indexOfFirst { it.key == railFocusedKey }
        val from = if (current >= 0) current else railFocusTargetIndex().coerceIn(0, railRows.size - 1)
        val targetIndex = if (isDown) (from + 1) % railRows.size else (from - 1 + railRows.size) % railRows.size
        val targetKey = railRows[targetIndex].key
        // Move optimistically so a held key keeps stepping even before focus lands.
        railFocusedKey = targetKey
        railNavJob?.cancel()
        railNavJob = scope.launch {
            fun requestTarget(): Boolean {
                val req = railFocusRequesters[targetKey] ?: return false
                return runCatching { req.requestFocus() }.isSuccess
            }
            // When the target is off-screen it isn't composed yet, so its requester can't take
            // focus: scroll it into range first, then retry (same order GuideGrid uses).
            if (!requestTarget()) {
                runCatching { railListState.scrollToItem(targetIndex) }
                delay(30)
                for (attempt in 0..5) {
                    if (requestTarget()) break
                    delay(40)
                }
            }
        }
    }

    /**
     * Opens the category rail on the category the viewer is BROWSING — the guide's selected category,
     * or Favourites when that is active — not on the category the playing channel lives in. Opening
     * on the playing channel's category meant that after switching to another category and scrolling
     * it, LEFT yanked the rail back to where the channel plays and made the viewer scroll all the way
     * back to the category they were just in. Only a fresh guide with nothing browsed yet falls back
     * to the playing channel's category.
     *
     * Deliberately does not call selectCategory() itself: this runs inside a key dispatch, and
     * swapping the guide's rows synchronously there disposes the focused guide node mid-dispatch.
     * The category behind the rail is applied off the dispatch, once focus has landed.
     */
    fun openCategoryRail() {
        val browsing = selectedCategory
        railOpenFocusKey = when {
            favouritesOnly -> RAIL_KEY_FAVOURITES
            browsing != null -> browsing
            else -> {
                val playingChannel = (selectedRow ?: highlightedRow)?.primary
                val playingGroup = playingChannel?.categoryId?.let { catId ->
                    categories.firstOrNull { catId in it.ids }
                }
                playingGroup?.key ?: RAIL_KEY_FAVOURITES
            }
        }
        // Where to scroll if the entry isn't drawn (a collapsed playlist, or a category since
        // removed). The open itself never scrolls an entry that is already on screen — see the
        // focus effect below.
        railScrollToIndex = when (val key = railOpenFocusKey) {
            null -> railFocusTargetIndex()
            RAIL_KEY_FAVOURITES ->
                railRows.indexOfFirst { it is RailRow.Favourites }.takeIf { it >= 0 }
                    ?: railFocusTargetIndex()
            else -> railIndexForCategoryKey(key).takeIf { it >= 0 } ?: railFocusTargetIndex()
        }
        suppressRailPreviewSelection = true
        railExpanded = true
        pendingRailFocus = true
    }

    LaunchedEffect(pendingRailFocus) {
        if (pendingRailFocus) {
            // Focus the intended entry FIRST and only scroll to it if it turns out not to be
            // composed. Scrolling unconditionally on open shoved the list — and the cursor with
            // it — a beat after the rail appeared: the entry is almost always already on screen,
            // so the scroll was pure movement, read as "the cursor jumps when I press left".
            //
            // Preview suppression stays on until focus lands on the intended entry: focus
            // moving to another entry while rows are being rebuilt must not select anything.
            // It clears when the viewer actually navigates the rail, in the rail's own key
            // handler below.
            val targetIndex = if (railScrollToIndex >= 0) railScrollToIndex else railFocusTargetIndex()
            var scrolled = false
            for (attempt in 0..4) {
                val targetKey = railRows.getOrNull(targetIndex)?.key
                val req = targetKey?.let { railFocusRequesters[it] }
                val res = if (req != null) {
                    runCatching { req.requestFocus() }
                } else {
                    Result.failure(IllegalStateException("rail entry not composed yet"))
                }
                if (res.isSuccess) break
                if (!scrolled) {
                    runCatching { railListState.scrollToItem(targetIndex) }
                    scrolled = true
                }
                delay(16)
            }
            pendingRailFocus = false
        }
    }
    // Apply the category the rail opened on, off the key dispatch that requested it. The open
    // handlers deliberately avoid calling selectCategory() inside a key event (it disposes the
    // focused guide node mid-dispatch and caused an ANR storm); the switch happens here, after
    // composition has settled, and only while suppression is still armed — so a later focus drift
    // or preview navigation never re-applies a stale target.
    LaunchedEffect(railExpanded, railOpenFocusKey) {
        if (railExpanded && suppressRailPreviewSelection) {
            when (val target = railOpenFocusKey) {
                null -> {}
                RAIL_KEY_FAVOURITES -> viewModel.selectFavourites()
                else -> viewModel.selectCategory(target)
            }
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
            // End preview mode on EVERY close path (d-pad RIGHT out of the rail, Back, OK on an
            // entry, app switch) — not just OK-clicks. A stuck-on previewTopRow kept a second
            // pseudo-cursor on the guide's top row after exiting the rail, which is exactly
            // the "two highlighted blocks" bug: the pseudo-cursor + the real focused row's
            // cursor both rendered.
            railPreviewing = false
            railPreviewJob?.cancel()
        }
    }
    // Debounced category preview: restarts on every focused entry, so a fast walk through the
    // rail coalesces into a single guide rebuild once focus settles on an entry. Returns false —
    // and does nothing — when the entry is already the active category: re-selecting it rebuilt
    // the whole guide for no reason, and that rebuild also re-measured the rail list and threw it
    // back to the top, which is the second half of the "cursor jumps on open" flicker.
    fun scheduleRailPreview(key: String): Boolean {
        val alreadyActive = if (key == RAIL_KEY_FAVOURITES) {
            favouritesOnly
        } else {
            !favouritesOnly && selectedCategory == key
        }
        if (alreadyActive) return false
        railPreviewJob?.cancel()
        railPreviewJob = scope.launch {
            delay(RAIL_PREVIEW_DEBOUNCE_MILLIS)
            if (key == RAIL_KEY_FAVOURITES) viewModel.selectFavourites() else viewModel.selectCategory(key)
        }
        return true
    }
    LaunchedEffect(pendingGuideFocus, mainMenuVisible, shrinkingFromFullScreen) {
        // Never pull focus back to the guide while the main menu is open: doing so hands focus out
        // of the rail the moment it is summoned and the menu reads as "won't open at all".
        // Also wait out the shrink: the guide is visible underneath during the animation but
        // must not take focus until the picture has docked.
        if (pendingGuideFocus && !mainMenuVisible && !shrinkingFromFullScreen) {
            delay(30)
            // Only clear on success: a failed request (cell not composed yet) must retry on the
            // next trigger instead of stranding focus on whatever holds it now.
            if (runCatching { guideFocusRequester.requestFocus() }.isSuccess) {
                pendingGuideFocus = false
            }
        }
    }
    // When the main menu closes, put the guide cursor back on the playing channel. Closing the
    // category rail already restores it (its RIGHT handler requests guide focus); the main menu is
    // a separate component, so without this the guide simply takes its first focusable row.
    var wasMainMenuVisible by remember { mutableStateOf(mainMenuVisible) }
    LaunchedEffect(mainMenuVisible) {
        if (wasMainMenuVisible && !mainMenuVisible) {
            val active = selectedRow ?: highlightedRow
            if (active != null) {
                highlightedRow = active
                val now = System.currentTimeMillis()
                highlightedProgramme = active.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: active.now
            }
            guideRestoreTick++
            pendingGuideFocus = true
        }
        wasMainMenuVisible = mainMenuVisible
    }

    // ---- Back button navigation flow ---------------------------------------------------------
    BackHandler(enabled = isFullScreen) {
        // Backing out of a catch-up programme leaves the guide exactly where the viewer scrubbed
        // to, so they can pick another archived show. The next Back (the browsingAwayFromLive
        // handler below) returns the guide to now. The shared player keeps the timeshift stream in
        // the preview pane; with preview video off there is no surface for it, so stop it rather
        // than leave a hidden stream running.
        if (catchup != null) {
            if (!previewEnabled) graph.livePlayer.player.pause()
            pendingGuideFocus = true
            leaveFullScreen()
            return@BackHandler
        }
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
        // Switch to the playing channel's category ONLY when it actually differs from the
        // rows already on screen. Unconditionally calling selectCategoryForChannel re-emitted
        // selectedCategory → flatMapLatest restarted the whole pipeline (channel query +
        // quick EPG + full 48h window) on EVERY Back from fullscreen — a ~10s rebuild on a
        // 25k-channel category for nothing, since the data was already loaded.
        if (currentId > 0L && (rows.isEmpty() || rows.none { it.primary.id == currentId || it.variants.any { v -> v.id == currentId } })) {
            viewModel.selectCategoryForChannel(currentId)
        }
        nowMillis = System.currentTimeMillis()
        viewModel.tick()
        viewModel.guideToNow()
        backScrollActive = false
        scope.launch { guideScrollState.scrollTo(0) }
        pendingGuideFocus = true
        leaveFullScreen()
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

    val browsingAwayFromLive = backScrollActive || timeShifted || guideHourOffset != 0 || catchup != null
    BackHandler(enabled = !isFullScreen && !railExpanded && !mainMenuVisible && channelMenu == null && recordTarget == null && !showBackgroundPrompt && pendingLiveChannel == null) {
        if (browsingAwayFromLive) {
            nowMillis = System.currentTimeMillis()
            viewModel.tick()
            viewModel.guideToNow()
            backScrollActive = false
            scope.launch { guideScrollState.scrollTo(0) }
            // Leaving the archive: clearing the session lets the preview effect re-tune the live
            // stream now that the guide is back at "now".
            catchup = null
            val targetRow = selectedRow ?: rows.firstOrNull()
            if (targetRow != null) {
                highlightedRow = targetRow
                val now = System.currentTimeMillis()
                highlightedProgramme = targetRow.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: targetRow.now
            }
            guideRestoreTick++
        } else {
            // Open the rail on the category being browsed. Do NOT call selectCategory() here: this
            // runs inside a key-event dispatch, and swapping the guide's rows synchronously disposes
            // the focused guide node mid-dispatch — measured on-device as a >5s ANR storm (dropbox
            // data_app_anr 2026-09-08 17:17/17:18: createItemsAfterList subcompose at 140%+ CPU).
            // The category is applied off the dispatch, once focus has landed inside the rail.
            openCategoryRail()
        }
    }

    BackHandler(enabled = !isFullScreen && !mainMenuVisible && channelMenu == null && recordTarget == null && !showBackgroundPrompt && pendingLiveChannel == null && railExpanded) {
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
    // Membership test is O(1) via a key set built once per rows change. It used to be a full
    // `rows.any { it.key == highlightedRow?.key }` scan that ran on EVERY d-pad focus change —
    // for a large category that linear scan was the bulk of the guide's UI-thread cost.
    val rowKeySet = remember(rows) { rows.mapTo(HashSet(rows.size)) { it.key } }
    // State, not value: this is handed to the preview and grid, so they read it themselves and
    // only they recompose when the highlight moves.
    val activeHighlightedRowState = remember(rowKeySet, initialRow, activeSelectedRow) {
        derivedStateOf {
            highlightedRowState.value?.takeIf { it.key in rowKeySet }
                ?: initialRow
                ?: activeSelectedRow
        }
    }
    val selectedKeyState = remember(activeHighlightedRowState) {
        derivedStateOf { activeHighlightedRowState.value?.key }
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


    /**
     * Hands the viewer over to another content type from one of the player's OSD shortcuts.
     *
     * The shared live player has to be silenced and paused first. It is a process-wide singleton
     * that outlives this screen, so just switching the tab would leave it playing: with guide-preview
     * sound on — which is the default — live TV audio would carry on over the Movies grid, with no
     * video anywhere on screen to explain it. Pausing is safe rather than destructive: coming back to
     * the Live tab resumes the same stream, and the preview only re-tunes if the channel changed.
     */
    fun leaveLiveForTab(tab: String) {
        isFullScreen = false
        // Leaving the Live tab ends any archive session, so returning does not resume a paused
        // timeshift stream with the guide still parked in the past.
        catchup = null
        graph.livePlayer.player.apply {
            volume = 0f
            pause()
        }
        settings.requestHomeTab(tab)
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

                    if (!isFullScreen && previewEnabled && !isPlayingOrBuffering && catchup == null) {
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
                    previewController.player.pause()
                    previewController.player.volume = 0f
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            previewController.player.pause()
            previewController.player.volume = 0f
        }
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
        // Tuning a live channel ends any archive playback. The media item must be cleared even when
        // the same channel id is already open: while in archive the item is the timeshift stream,
        // not the live one, so a naive "already playing" check would leave it stuck in the past.
        val wasArchive = catchup != null
        catchup = null
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
        isFullScreen = true
    }
    fun requestLive(channel: Channel) {
        if (activeRecordings.isNotEmpty()) pendingLiveChannel = channel else startLive(channel)
    }

    /**
     * Plays a finished programme through the SAME shared player as live TV.
     *
     * The media item is swapped onto the provider's timeshift stream (`isLive = false`) exactly the
     * way a channel change swaps the live stream, so the preview pane and the fullscreen player are
     * the ordinary live ones — nothing navigates to a separate screen and the live player is never
     * torn down. The [catchup] session lets the player label the OSD and lets the guide stay where
     * the viewer scrubbed to when they back out.
     */
    fun playCatchup(channel: Channel, programme: Programme, onNoArchive: (() -> Unit)? = null) {
        if (!settings.catchupEnabled.value) {
            Toast.makeText(context, context.getString(R.string.guide_catchup_disabled), Toast.LENGTH_SHORT).show()
            onNoArchive?.invoke()
            return
        }
        // The guide only keeps 3 days back — older slots stay visible until the next refresh
        // but the archive behind them is unreachable from this device (TiviMate shows the same
        // expired-slot behaviour).
        val effectiveDays = settings.effectiveArchiveDays(channel.tvArchiveDays)
        if (effectiveDays > 0 &&
            programme.startUtcMillis < System.currentTimeMillis() - effectiveDays * 86_400_000L
        ) {
            Toast.makeText(context, context.getString(R.string.guide_catchup_expired), Toast.LENGTH_SHORT).show()
            onNoArchive?.invoke()
            return
        }
        recordScope.launch {
            val source = withContext(Dispatchers.IO) { graph.sourceRepository.byId(channel.sourceId) }
            val url = if (source != null) {
                withContext(Dispatchers.IO) {
                    CatchupResolver.resolve(
                        source,
                        channel,
                        programme,
                        settings.catchupCorrectionMin.value,
                    )
                }
            } else null
            if (url == null) {
                Toast.makeText(context, context.getString(R.string.guide_catchup_link_failed), Toast.LENGTH_SHORT).show()
                onNoArchive?.invoke()
                return@launch
            }
            onDismissMainMenu()
            // Point the guide at the row for this channel when it is on screen; otherwise a
            // provisional row keeps the playing channel selected while its category loads.
            val row = rows.firstOrNull {
                it.primary.id == channel.id || it.variants.any { v -> v.id == channel.id }
            } ?: ChannelsViewModel.Row(
                primary = channel,
                variants = listOf(channel),
                now = null,
                next = null,
                programmes = emptyList(),
            )
            selectedRow = row
            highlightedRow = row
            highlightedProgramme = programme
            val userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)"
            catchup = CatchupPlayback(
                channelId = channel.id,
                channelName = channel.shownName,
                programmeTitle = programme.title,
                startUtcMillis = programme.startUtcMillis,
                endUtcMillis = programme.endUtcMillis,
                url = url,
                userAgent = userAgent,
            )
            previewController.play(
                PlayerController.Request(
                    url = url,
                    title = "${channel.shownName} — ${programme.title}",
                    userAgent = userAgent,
                    isLive = false,
                ),
                debounce = false,
            )
            previewedChannelId = channel.id
            isFullScreen = true
        }
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
    LaunchedEffect(selectedRow?.key, previewEnabled, screenResumed, recordingActive, isFullScreen, catchup) {
        val row = selectedRow ?: activeSelectedRow ?: return@LaunchedEffect
        // While an archive programme is playing, the preview keeps the timeshift stream the shared
        // player is already on — re-tuning the live URL here would yank the viewer back to now.
        if (isFullScreen || !previewEnabled || !screenResumed || recordingActive || catchup != null) {
            if (!isFullScreen && catchup == null && (!previewEnabled || !screenResumed || recordingActive)) {
                previewController.player.pause()
                previewController.player.volume = 0f
            }
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
    // Where this screen's own root sits inside the Compose root. The live player is positioned from
    // the preview card's bounds, which are measured in ROOT space (boundsInRoot). The player itself
    // lives inside the guide's root, so when the main nav rail pushes the guide across, the rail's
    // width was counted twice and the video landed over the programme info. Subtracting this origin
    // converts the card bounds back into the guide's own space.
    var homeOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    // The root's own size, so the video surface can ease from the preview card out to the full
    // screen (see PersistentVideoSurface below).
    var homeSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    val lastInteractionState = remember { mutableStateOf(System.currentTimeMillis()) }
    var lastInteractionTime by lastInteractionState

    // Auto-return to full screen after 1 minute of inactivity in the TV guide.
    //
    // Observed via snapshotFlow instead of keying the effect on lastInteractionTime: that value is
    // refreshed on EVERY guide focus, so keying on it recomposed this whole screen on every d-pad
    // step. Watching it as a flow restarts the countdown without any recomposition.
    LaunchedEffect(isFullScreen, channelMenu, recordTarget, showBackgroundPrompt, pendingLiveChannel, rows.isNotEmpty()) {
        if (!isFullScreen && rows.isNotEmpty() && channelMenu == null && recordTarget == null && !showBackgroundPrompt && pendingLiveChannel == null) {
            snapshotFlow { lastInteractionState.value }
                .collectLatest {
                    delay(60_000L)
                    isFullScreen = true
                }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppTheme.palette.background)
            .onGloballyPositioned {
                homeOrigin = it.boundsInRoot().topLeft
                homeSize = it.size
            }
            .onPreviewKeyEvent { e ->
                lastInteractionTime = System.currentTimeMillis()
                if (!isFullScreen && e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        // Deliberately NO day-paging keys (MediaRewind/PageUp/ChannelUp and
                        // MediaFastForward/PageDown/ChannelDown were removed): they jumped the
                        // guide a full day on accidental presses. Day browsing stays on the
                        // header's prev/next-day buttons; HOLD LEFT remains the back-in-time
                        // scrub, and MediaPlay still snaps back to live.
                        Key.MediaPlay, Key.MediaPlayPause -> {
                             viewModel.guideToNow()
                             backScrollActive = false
                             scope.launch { guideScrollState.scrollTo(0) }
                             // Jumping back to live ends any archive playback.

                            catchup = null
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
        // ---- TiviMate-grade persistent hardware video surface ----
        // One ExoPlayer and one surface, alive for the whole Live TV session. Going full screen
        // only ever *moves* this surface (grows it out of the guide's preview card and shrinks it
        // back), so the video never re-attaches: no second decoder, no re-buffer, no black.
        //
        // During the shrink the guide is composed underneath (guideVisible) while the surface
        // stays on top by paint order (guide carries a below-zero layer); focus is handed over
        // only after docking so d-pad input cannot escape mid-animation. No elevation is ever
        // placed above the OSD — the OSD composes last and always wins.
        val surfaceFullScreen = isFullScreen && !shrinkingFromFullScreen
        val guideVisible = !isFullScreen || shrinkingFromFullScreen
        val showPlayerOsd = isFullScreen && !shrinkingFromFullScreen
        if (previewEnabled || isFullScreen || shrinkingFromFullScreen || catchup != null) {
            PersistentVideoSurface(
                player = previewController.player,
                isFullScreen = surfaceFullScreen,
                cardBounds = previewBounds,
                rootOrigin = homeOrigin,
                rootSize = homeSize,
                resizeMode = if (surfaceFullScreen) {
                    playerResizeMode
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                },
            )
        }

        if (showPlayerOsd) {
            PlayerScreen(
                channelId = (selectedRow ?: highlightedRow ?: activeSelectedRow ?: activeHighlightedRowState.value)?.primary?.id ?: (if (settings.lastChannelId > 0L) settings.lastChannelId else null),
                onBack = {
                    // Backing out of catch-up leaves the guide at the scrubbed time so another
                    // archived show can be picked; the next Back returns to now (browsingAwayFromLive).
                    // With preview video off there is no surface for the timeshift stream, so stop it.
                    if (catchup != null) {
                        if (!previewEnabled) previewController.player.pause()
                        pendingGuideFocus = true
                        leaveFullScreen()
                        return@PlayerScreen
                    }
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
                     scope.launch { guideScrollState.scrollTo(0) }
                     pendingGuideFocus = true

                    railExpanded = false
                    leaveFullScreen()
                },
                onOpenSearch = onOpenSearch,
                // These three leave the live player for another content type. Asking the tab
                // shell for its tab is what actually takes the viewer there — these used to call
                // onOpenMainMenu(), which only slides the nav rail in and leaves the Live tab
                // selected, so pressing Movies or Shows appeared to do nothing. The pressed tab is
                // requested rather than navigated to because this player renders *inside* the tab
                // shell; the shell is still composed and picks the request up as soon as it lands
                // (see AppSettings.requestHomeTab). leaveLiveForTab also silences the shared player.
                onOpenMovies = { leaveLiveForTab("movies") },
                onOpenShows = { leaveLiveForTab("shows") },
                onOpenRecordings = { leaveLiveForTab("recordings") },
                onOpenSettings = onOpenSettings,
                onOpenMultiview = { channelId ->
                    // Multiview brings its own two decoders; silence the shared player first so
                    // we never hold three streams (and three lots of provider connections) at once.
                    isFullScreen = false
                    catchup = null
                    graph.livePlayer.player.apply {
                        volume = 0f
                        pause()
                    }
                    onOpenMultiview(channelId)
                },
                catchup = catchup,
                onCatchupChange = { catchup = it },
                renderPlayerView = false,
                onChannelChange = { newId ->
                    // A genuine live tune (zap / channel-list pick) ends archive playback.
                    catchup = null
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
        }
        if (guideVisible) {
             // TiviMate-style: the category rail is a real column beside the guide, so opening it
             // makes room for the category list before focus moves into it.
             // Composed underneath during the shrink (below-zero layer keeps the video on top),

            // dimmed until the picture docks. Deliberately not focusable: a focusable container
            // traps focus on itself instead of a grid cell, which ate the first Back press at
            // every stage (guide->categories, categories->menu).
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .zIndex(-1f)
                    .alpha(if (shrinkingFromFullScreen) 0.35f else 1f),
            ) {
         AnimatedVisibility(
             visible = railExpanded,
             enter = expandHorizontally(animationSpec = tween(180)),
             exit = shrinkHorizontally(animationSpec = tween(160)),
             modifier = Modifier.width(240.dp),
         ) {
        // ---- Category rail -----------------------------------------------------------------
        // d-pad LEFT from the guide's channel column opens it; it closes once focus moves back
        // into the guide.
        Column(
            Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = LocalGuideChromeAlpha.current))
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
                                 pendingGuideFocus = true
                                 true

                            }
                            Key.DirectionLeft -> {
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                onOpenMainMenu()
                                true
                            }
                            // The first deliberate up/down move ends the "just opened" guard:
                            // from here focus previews select exactly as the viewer asks. The move
                            // itself is driven by index (and wraps) so focus can never fall off the
                            // rail into the guide, which would close the category list.
                            Key.DirectionUp, Key.DirectionDown -> {
                                suppressRailPreviewSelection = false
                                moveRailFocus(e.key == Key.DirectionDown)
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
                // Every rail entry carries its own FocusRequester so [moveRailFocus] can step to
                // any neighbour by key — including one scrolled out of view, which the old single
                // requester ([railFocusTargetIndex]'s entry) could never reach. The open handler
                // still decides where focus goes on open via [railFocusTargetIndex].
                itemsIndexed(railRows, key = { _, row -> row.key }) { _, row ->
                    val entryModifier = Modifier
                        .focusRequester(railFocusRequesters.getOrPut(row.key) { FocusRequester() })
                        .onFocusChanged {
                            if (it.isFocused) {
                                railFocusedKey = row.key
                            }
                        }
                    when (row) {
                        is RailRow.Favourites -> RailEntry(
                            label = stringResource(R.string.guide_favourites),
                            selected = favouritesOnly,
                            onFocused = {
                                if (suppressRailPreviewSelection && railOpenFocusKey != RAIL_KEY_FAVOURITES) {
                                    return@RailEntry
                                }
                                if (scheduleRailPreview(RAIL_KEY_FAVOURITES)) railPreviewing = true
                            },
                            onClick = {
                                railPreviewJob?.cancel()
                                viewModel.selectFavourites()
                                railPreviewing = false
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                guideRestoreTick++
                                 pendingGuideFocus = true

                            },
                            modifier = entryModifier,
                        )
                        is RailRow.SourceHeader -> RailEntry(
                            label = row.name,
                            selected = false,
                            // Collapsing a group must not close the rail or move the guide: the user
                            // is tidying the sidebar, not choosing something to watch.
                            onClick = { viewModel.toggleSourceCollapsed(row.sourceId) },
                            modifier = entryModifier,
                            expanded = row.expanded,
                        )
                        is RailRow.Category -> RailEntry(
                            label = row.group.label,
                            selected = !favouritesOnly && selectedCategory == row.group.key,
                            onFocused = {
                                if (suppressRailPreviewSelection && railOpenFocusKey != row.group.key) {
                                    return@RailEntry
                                }
                                if (scheduleRailPreview(row.group.key)) railPreviewing = true
                            },
                            onClick = {
                                railPreviewJob?.cancel()
                                viewModel.selectCategory(row.group.key)
                                railPreviewing = false
                                railExpanded = false
                                suppressRailPreviewSelection = false
                                guideRestoreTick++
                                 pendingGuideFocus = true

                            },
                            modifier = entryModifier,
                            nested = true,
                        )
                    }
                }
            }
        }
        }

        // ---- Preview + guide ---------------------------------------------------------------
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (rows.isEmpty()) {
                when {
                    favouritesOnly -> NoFavouritesState()
                    isSyncing || channelsPresent == null -> LoadingState(isSyncing)
                    channelsPresent == true -> {
                        // Channels are on the box but the current filter matches none (stale
                        // category/provider after a playlist change, mid-transition). A bare
                        // spinner here is a dead end for the d-pad — offer the rail instead.
                        FilterEmptyState(onBrowse = { openCategoryRail() })
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
                        // Picking a different channel leaves archive playback; the preview effect
                        // then tunes the newly chosen channel live.
                        if (catchup != null) catchup = null
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
                // Per-channel catch-up capability: the badge mirrors the resolver, so a badged
                // channel is one `playCatchup` can actually build a URL for.
                //
                // Evidence counted (see CatchupResolver.isSupported):
                //   * tvArchive — Xtream `tv_archive`, or the M3U `catchup` / `catchup-days` /
                //     `timeshift` attributes (see M3uParser).
                //   * cmd — an M3U `catchup-source` template (Stalker commands excluded).
                //   * portal capability — an Xtream source, or an M3U whose source URL carries
                //     Xtream credentials (`get.php`/`player_api.php`), or a stream URL shaped
                //     like an Xtream panel URL.
                // Nothing else does. In particular:
                //   * tvArchiveDays is the archive *window*, not a yes/no. Panels routinely report
                //     `tv_archive_duration` for channels whose `tv_archive` is 0, so treating a
                //     duration as evidence badged channels that have no archive.
                //
                // Runs once per channel over the whole list, so it is computed off the main thread.
                var catchUpChannelIds by remember(sources, rows) { mutableStateOf(emptySet<Long>()) }
                LaunchedEffect(sources, rows) {
                    catchUpChannelIds = withContext(Dispatchers.Default) {
                        val byId = sources.associateBy { it.id }
                        rows.mapNotNull { row ->
                            val ch = row.primary
                            if (CatchupResolver.isSupported(byId[ch.sourceId], ch)) ch.id else null
                        }.toSet()
                    }
                }
                GuidePreview(
                    rowState = highlightedRowState,
                    programmeState = highlightedProgrammeState,
                    nowMillis = nowMillis,
                    onWatch = { (selectedRow ?: highlightedRow)?.let { goFullscreen(it.primary) } },
                    onRefresh = onRefresh,
                    onAddSource = onAddSource,
                    previewPlayer = if (previewEnabled && screenResumed && !recordingActive) previewController.player else null,
                    dayLabel = dayLabel,
                    // Bound derived from EPG retention (see MAX_PAGE_BACK_HOURS): the deepest
                    // page's left edge is exactly the retention boundary — never a blank day.
                    canGoPrevDay = guideHourOffset > -viewModel.maxPageBackHours,
                    onPrevDay = { viewModel.nudgeGuideDay(-1) },
                    onNextDay = { viewModel.nudgeGuideDay(1) },
                    onPreviewBoundsChanged = { rect ->
                        if (rect.width > 0 && rect.height > 0 && previewBounds != rect) {
                            previewBounds = rect
                        }
                    },
                    catchUpChannelIds = catchUpChannelIds,
                )

                // TiviMate-style guide header stamp: when the guide last synced + channel count.
                val epgInfoLine = if (settings.lastGuideUpdatedMillis > 0L) {
                    "EPG updated ${formatTime(settings.lastGuideUpdatedMillis)} · ${settings.lastGuideChannelCount} channels"
                } else null
                // Shared by both layouts: focus follows the highlight and collapses the rail; LEFT
                // from the leftmost element reopens the rail (consumed only when it was hidden).
                // Deliberately does NOT dismiss the main menu. The menu is only on screen when the
                // viewer summoned it with Back, and it takes focus when it appears — but losing focus
                // to it and back hands focus to the guide for one frame, and dismissing here killed
                // the menu the instant it opened (it then stayed hidden because the shell only shows
                // the rail on Live TV while it is open). RIGHT out of the rail closes it instead.
                val onFocusChannel: (ChannelsViewModel.Row, Programme?) -> Unit = remember {
                    { r: ChannelsViewModel.Row, prog: Programme? ->
                        lastInteractionTime = System.currentTimeMillis()
                        highlightedRow = r
                        highlightedProgramme = prog ?: r.now
                        railExpanded = false
                    }
                }
                // Deliberately not remembered: this reads the live playing channel, categories and
                // selection. A keyless remember{} captured the first composition's empty state and
                // kept opening the rail on Favourites no matter what was playing.
                val onExitLeftChannel: () -> Boolean = {
                    backScrollActive = false
                    if (!railExpanded) {
                        // LEFT reopens the rail on the category being browsed (see openCategoryRail).
                        openCategoryRail()
                        true
                    } else {
                        false
                    }
                }
                if (channelLayout == AppSettings.ChannelLayout.LIST) {
                    ChannelList(
                        rows = rows,
                        epgRows = viewModel.epgRows,
                        selectedKeyState = selectedKeyState,
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
                        epgRows = viewModel.epgRows,
                        horizontalScrollState = guideScrollState,
                        providedListState = guideListState,
                        windowStartMillis = windowStart,
                        dayOffset = guideHourOffset / 24,
                        catchUpChannelIds = catchUpChannelIds,
                        reminderKeys = reminderKeys,
                        epgInfoLine = epgInfoLine,
                        restoreTick = guideRestoreTick,
                        onTimeShifted = { timeShifted = it },
                        scrollTopTick = guideScrollTopTick,
                        previewTopRow = railPreviewing,
                        selectedKeyState = selectedKeyState,
                        playingKey = activeSelectedRow?.key,
                        focusRequester = guideFocusRequester,
                        onSelectRow = { row -> tuneOrFullscreen(row) },
                        onLongSelectRow = { row -> channelMenu = row },
                        onFocusRow = onFocusChannel,
                        onProgramme = { row, programme ->
                            // Only reached for a genuine short press: GuideGrid consumes the
                            // key-up when the OK hold was long enough for the menu.
                            val liveNow = nowMillis in programme.startUtcMillis until programme.endUtcMillis
                            val isPast = programme.endUtcMillis <= nowMillis
                            if (liveNow) {
                                tuneOrFullscreen(row)
                            } else if (isPast) {
                                // Archive playback goes through the shared player; if this channel
                                // cannot build a catch-up URL, fall back to the record menu.
                                playCatchup(row.primary, programme) { recordTarget = row to programme }
                            } else {
                                recordTarget = row to programme
                            }
                        },
                        onProgrammeLongPress = { row, programme ->
                            // Short OK on a live programme keeps instant tune; long OK opens
                            // the same programme menu so "Watch from start" (catch-up),
                            // record, remind etc. stay reachable without zapping away.
                            recordTarget = row to programme
                        },
                        onToggleFavourite = { viewModel.toggleFavourite(it) },
                        onEnableBackScroll = { backScrollActive = true },
                        onJumpToLive = {
                            nowMillis = System.currentTimeMillis()
                            viewModel.tick()
                             viewModel.guideToNow()
                             backScrollActive = false
                             scope.launch { guideScrollState.scrollTo(0) }
                             val active = activeSelectedRow ?: rows.firstOrNull()

                            if (active != null) {
                                highlightedRow = active
                                val now = System.currentTimeMillis()
                                highlightedProgramme = active.programmes.firstOrNull { now in it.startUtcMillis until it.endUtcMillis } ?: active.now
                                pendingGuideFocus = true
                            }
                        },
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
                        onExitLeft = onExitLeftChannel,
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
                    .padding(24.dp)
                    // Auto-close a menu the viewer has walked away from. Any d-pad press on the
                    // dialog restarts the countdown; when it runs out unanswered the menu
                    // dismisses itself instead of sitting over the guide indefinitely. Returns
                    // false so the press still reaches the rows that handle it.
                    .onPreviewKeyEvent {
                        menuIdleReset()
                        false
                    },
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
                        playCatchup(chosenVariant, programme)
                        recordTarget = null
                    }
                }

                when {
                    liveNow && recordingThis != null -> RecordActionRow(stringResource(R.string.rec_stop_recording), primary = false) {
                        graph.recordingEngine.stop(recordingThis.id)
                        Toast.makeText(context, context.getString(R.string.rec_recording_stopped), Toast.LENGTH_SHORT).show()
                        recordTarget = null
                    }
                    liveNow -> RecordActionRow(
                        stringResource(R.string.rec_record_now),
                        primary = false,
                        leading = { ActionGlyph.RecordingDot(AppTheme.palette.recording) },
                    ) {
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
                RecordActionRow(
                    if (channel.favourite) stringResource(R.string.common_remove_favourite)
                    else stringResource(R.string.common_favourite),
                    leading = { ActionGlyph.Star(AppTheme.palette.favourite) },
                ) {
                    viewModel.toggleFavourite(targetRow)
                    recordTarget = null
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
                            playCatchup(channel, nowProg)
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
private fun RecordActionRow(
    label: String,
    primary: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    // primaryContainer/onPrimaryContainer are both accent shades in this palette, so the old
    // primary row was accent-on-accent — unreadable, and worst on the focused first row. Use the
    // solid accent with the on-accent ink in both states: the tvFocus cursor is a translucent
    // white wash, so flipping to light onSurface ink while focused is white-on-white.
    val fill = if (primary) AppTheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = when {
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(fill)
            .tvFocus(shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
        )
    }
}

/**
 * The leading marks the programme menu wears: a filled favourite star, or the recording dot.
 *
 * The glyph is coloured by what it means, not by the label it sits beside — the favourite row
 * keeps the amber star even when the label reads "Remove favourite", and the recording dot is
 * always the recording red.
 */
private object ActionGlyph {
    @Composable
    fun Star(tint: Color) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    @Composable
    fun RecordingDot(tint: Color) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

/** A small focusable quality pill (FHD / HD / SD…) for the record dialog's "record HD or SD" choice. */
@Composable
private fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) AppTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .tvFocus(shape = shape, selected = selected, onFocusChange = { focused = it })
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * One row of the guide's sidebar, in the order it is drawn.
 *
 * The sidebar is a tree — a header per playlist with its categories under it — but a LazyColumn needs
 * a flat list, and the scroll/focus indices are only right if that flat list is the single source of
 * truth. Building it once and indexing it replaces the arithmetic that used to add a fixed number of
 * rows for the provider section, which was wrong the moment a collapsed group changed the row count.
 */
private sealed interface RailRow {
    /** Stable identity for LazyColumn's keying; the focus/scroll code matches on it too. */
    val key: String

    /** The Favourites shortlist, drawn first when the setting is on. */
    data object Favourites : RailRow {
        override val key: String = RAIL_KEY_FAVOURITES
    }

    /** One playlist; [expanded] decides whether its categories are drawn beneath it. */
    data class SourceHeader(
        val sourceId: Long,
        val name: String,
        val expanded: Boolean,
    ) : RailRow {
        override val key: String = "rail:source:$sourceId"
    }

    /** A category that [sourceId] contributes. */
    data class Category(val sourceId: Long, val group: ChannelsViewModel.CategoryGroup) : RailRow {
        override val key: String = "rail:category:$sourceId:${group.key}"
    }
}

// Stable rail-entry keys: category groups use their non-empty group key; favourites uses this
// sentinel while preview suppression is active. There is no all-channels sentinel: the rail has no
// such entry.
private const val RAIL_KEY_FAVOURITES = "rail:favourites"

/** How long focus must rest on a rail entry before its category is previewed (see pendingPreviewKey). */
private const val RAIL_PREVIEW_DEBOUNCE_MILLIS = 400L

@Composable
private fun RailEntry(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    /**
     * null = a plain entry. Otherwise this row is a playlist group header, and the value says whether
     * its categories are drawn — a chevron at the end, so a collapsed group is still visible as one
     * even though its children are gone.
     */
    expanded: Boolean? = null,
    /** Drawn beneath a group header — indented so the sidebar reads as a tree. */
    nested: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .tvFocus(
                shape = RoundedCornerShape(8.dp),
                selected = selected,
                onFocusChange = { isFocused ->
                    focused = isFocused
                    if (isFocused) onFocused?.invoke()
                },
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(
                start = if (nested) 24.dp else 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                selected -> AppTheme.primary
                focused -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (expanded != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
            model = app.opentv.ui.components.logoRequest(LocalContext.current, row.primary.logoUrl, 128),
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

/**
 * Channels exist on the box but the current category/provider filter matches none of them —
 * the state a stale filter leaves behind. Focusable by design (the Button takes d-pad focus),
 * so this never strands the viewer the way the old bare spinner did.
 */
@Composable
private fun FilterEmptyState(onBrowse: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Text(
                stringResource(R.string.guide_empty_filter_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.guide_empty_filter_desc),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onBrowse) {
                Text(stringResource(R.string.guide_browse_categories))
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

/** How long the picture takes to grow out of / shrink back into the guide's preview card. */
private const val PLAYER_TRANSITION_MILLIS = 300

/** How long the programme menu waits for a d-pad press before dismissing itself. */
private const val MENU_IDLE_TIMEOUT_MILLIS = 30_000L

/** UTC in the database, device zone on screen. Converted here and nowhere else. */
private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private fun formatTime(utcMillis: Long): String = timeFormat.format(Date(utcMillis))



/**
 * The one hardware video surface for Live TV, and the reason moving between the guide's preview
 * card and full screen is instant and never goes black: the ExoPlayer and its surface are created
 * once and live for the whole screen, and this only ever *positions* the surface.
 *
 * The grow/shrink is a 0..1 factor eased between the preview card's rectangle and the full-screen
 * rectangle, so full screen is byte-for-byte the same surface the card was showing — no second
 * decoder, no re-buffer, no shutter.
 *
 * The card's own rectangle is deliberately NOT eased: opening the category rail or stepping focus
 * moves the card, and the video has to sit exactly on it rather than trail a beat behind. Only
 * [isFullScreen] drives the animation.
 */
@Composable
private fun PersistentVideoSurface(
    player: androidx.media3.exoplayer.ExoPlayer,
    isFullScreen: Boolean,
    cardBounds: androidx.compose.ui.geometry.Rect,
    rootOrigin: androidx.compose.ui.geometry.Offset,
    rootSize: androidx.compose.ui.unit.IntSize,
    resizeMode: Int,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val progress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFullScreen) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = PLAYER_TRANSITION_MILLIS,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "playerSurfaceProgress",
    )
    // Before the card has been measured (first frame, or a guide that is still loading) treat the
    // card as the whole screen rather than a 0x0 hole.
    val card = if (cardBounds.isEmpty) {
        androidx.compose.ui.geometry.Rect(
            androidx.compose.ui.geometry.Offset.Zero,
            androidx.compose.ui.geometry.Size(rootSize.width.toFloat(), rootSize.height.toFloat()),
        )
    } else {
        androidx.compose.ui.geometry.Rect(
            left = cardBounds.left - rootOrigin.x,
            top = cardBounds.top - rootOrigin.y,
            right = cardBounds.right - rootOrigin.x,
            bottom = cardBounds.bottom - rootOrigin.y,
        )
    }
    val t = progress
    val rootWidth = rootSize.width.toFloat().coerceAtLeast(1f)
    val rootHeight = rootSize.height.toFloat().coerceAtLeast(1f)
    val targetWidth = card.width * (1f - t) + rootWidth * t
    val targetHeight = card.height * (1f - t) + rootHeight * t
    val targetLeft = card.left * (1f - t)
    val targetTop = card.top * (1f - t)
    val modifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = targetLeft
            translationY = targetTop
            scaleX = (targetWidth / rootWidth).coerceAtLeast(0.01f)
            scaleY = (targetHeight / rootHeight).coerceAtLeast(0.01f)
        }
        .clip(RoundedCornerShape(with(density) { (10f * (1f - t)).dp }))
    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                    useController = false
                    keepScreenOn = true
                    this.resizeMode = resizeMode
                    this.player = player
                }
            },
            update = { pv ->
                if (pv.player != player) pv.player = player
                pv.resizeMode = resizeMode
            },
            onRelease = { pv -> pv.player = null },
        )
    }
}
