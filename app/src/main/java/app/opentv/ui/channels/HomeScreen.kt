/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import android.view.WindowManager
import androidx.compose.ui.window.Dialog
import app.opentv.core.ServiceLocator
import app.opentv.core.findActivity
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.player.PlaybackQueue
import app.opentv.player.PlayerController
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onOpenSearch: () -> Unit,
    viewModel: ChannelsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val previewEnabled by settings.guidePreviewVideo.collectAsState()

    val categories by viewModel.visibleCategoryGroups.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val favouritesOnly by viewModel.favouritesOnly.collectAsState()
    val windowStart by viewModel.windowStartMillis.collectAsState()

    // Two separate ideas, on purpose:
    //  - highlightedRow: where the d-pad is in the grid. Moves freely with up/down.
    //  - selectedRow: what the preview pane plays. Only changes when you press OK, so scrolling
    //    the list is calm and silent instead of re-tuning a stream on every keypress.
    var highlightedRow by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    var selectedRow by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }
    val previewSound by settings.guidePreviewSound.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Recording from the guide: what's capturing now, and a scope to kick a capture off.
    val activeRecordings by graph.recordingRepository.observeActive().collectAsState(initial = emptyList())
    val recordScope = rememberCoroutineScope()
    // The programme the user pressed OK on in the grid — drives the per-programme record menu.
    var recordTarget by remember { mutableStateOf<Pair<ChannelsViewModel.Row, Programme>?>(null) }
    // The channel whose OK menu (Watch / Record / Schedule) is open.
    var channelMenu by remember { mutableStateOf<ChannelsViewModel.Row?>(null) }

    // Re-evaluate "now" once a minute so progress bars advance without leaving the screen.
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            viewModel.tick()
            delay(60_000)
        }
    }

    // Keep both valid as the list changes (e.g. switching category): stay on the same channel if
    // it's still present, otherwise fall back to the top of the new list.
    LaunchedEffect(rows) {
        highlightedRow = rows.firstOrNull { it.key == highlightedRow?.key } ?: rows.firstOrNull()
        selectedRow = rows.firstOrNull { it.key == selectedRow?.key } ?: rows.firstOrNull()
    }

    // ---- Live preview player -----------------------------------------------------------------
    // One muted player, reused. It only ever decodes while the guide is the foreground screen,
    // and is stopped before any hand-off to full-screen, so the box never runs two decoders at
    // once — the thing that used to lock up cheap sticks.
    val previewScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val previewController = remember {
        PlayerController(context, previewScope, graph.httpClient, subtitlesEnabled = false)
            .also { it.player.volume = 0f }
    }
    DisposableEffect(Unit) {
        onDispose {
            previewController.release()
            previewScope.cancel()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var screenResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> screenResumed = true
                Lifecycle.Event.ON_PAUSE -> {
                    screenResumed = false
                    previewController.stop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Hold the screen awake while the guide's live preview is playing — otherwise the box's
    // screensaver fires while you're browsing with a channel running in the preview pane.
    DisposableEffect(previewEnabled, screenResumed) {
        val window = context.findActivity()?.window
        if (previewEnabled && screenResumed) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Preview audio follows the setting; muted by default so browsing stays quiet.
    LaunchedEffect(previewSound) {
        previewController.player.volume = if (previewSound) 1f else 0f
    }

    // Tune the preview to the highlighted channel, so it follows the d-pad as you browse — the
    // standard TV-guide behaviour. Debounced, so holding a direction doesn't re-tune every step.
    LaunchedEffect(highlightedRow?.key, previewEnabled, screenResumed) {
        val row = highlightedRow
        if (!previewEnabled || !screenResumed || row == null) {
            previewController.stop()
            return@LaunchedEffect
        }
        val channel = row.primary
        val source = graph.sourceRepository.byId(channel.sourceId)
        previewController.play(
            PlayerController.Request(
                url = channel.streamUrl,
                title = channel.displayName,
                userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                isLive = true,
            ),
            debounce = true,
        )
    }

    Row(Modifier.fillMaxSize()) {

        // ---- Category rail -----------------------------------------------------------------
        Column(
            Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp),
        ) {
            Text(
                "Live TV",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))

            RailEntry(
                label = "🔍  Search channels",
                selected = false,
                onClick = onOpenSearch,
            )
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    RailEntry(
                        label = "★ Favourites",
                        selected = favouritesOnly,
                        onClick = viewModel::selectFavourites,
                    )
                }
                item {
                    RailEntry(
                        label = "All channels",
                        selected = !favouritesOnly && selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                    )
                }
                items(categories, key = { it.key }) { group ->
                    RailEntry(
                        label = group.label,
                        selected = !favouritesOnly && selectedCategory == group.key,
                        onClick = { viewModel.selectCategory(group.key) },
                    )
                }
            }
        }

        // ---- Preview + guide ---------------------------------------------------------------
        Column(Modifier.weight(1f)) {
            if (rows.isEmpty()) {
                when {
                    favouritesOnly -> NoFavouritesState()
                    // A large provider takes a while to sync, and showing "No channels"
                    // during it reads as failure — which is how someone concludes an app
                    // is broken thirty seconds after installing it.
                    isSyncing || hasSources -> LoadingState(isSyncing)
                    else -> EmptyState(onAddSource)
                }
            } else {
                // Hand the player the list you're browsing so it can zap channel up/down.
                fun goFullscreen(channel: Channel) {
                    PlaybackQueue.items = rows.map {
                        PlaybackQueue.Item(it.primary.id, it.primary.displayName, it.primary.logoUrl, it.primary.number)
                    }
                    previewController.stop()
                    onPlayChannel(channel)
                }

                // Record the highlighted channel's now-programme (bounded to its end), or stop it
                // if it's already recording. Powers the preview pane's quick record dot.
                fun recordSelected() {
                    val row = highlightedRow ?: return
                    val active = activeRecordings.firstOrNull { it.channelId == row.primary.id }
                    if (active != null) {
                        graph.recordingEngine.stop(active.id)
                    } else {
                        recordScope.launch { graph.recordingEngine.startChannel(row.primary, row.now) }
                    }
                }

                GuidePreview(
                    row = highlightedRow,
                    nowMillis = nowMillis,
                    onWatch = { highlightedRow?.let { goFullscreen(it.primary) } },
                    onRefresh = onRefresh,
                    onAddSource = onAddSource,
                    previewPlayer = if (previewEnabled) previewController.player else null,
                    isRecording = highlightedRow?.primary?.id?.let { id ->
                        activeRecordings.any { it.channelId == id }
                    } == true,
                    onRecord = { recordSelected() },
                )
                GuideGrid(
                    rows = rows,
                    windowStartMillis = windowStart,
                    selectedKey = highlightedRow?.key,
                    // OK on a channel opens its menu: Watch, Record now, Schedule a later show,
                    // Record series. The preview already follows the highlight as you browse.
                    onSelectRow = { row -> channelMenu = row },
                    onFocusRow = { highlightedRow = it },
                    onProgramme = { row, programme -> recordTarget = row to programme },
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 12.dp),
                )
            }
        }
    }

    // The record menu for a programme picked in the grid.
    recordTarget?.let { (targetRow, programme) ->
        val channel = targetRow.primary
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
                    "${formatTime(programme.startUtcMillis)}–${formatTime(programme.endUtcMillis)}   ${channel.displayName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                when {
                    liveNow && recordingThis != null -> RecordActionRow("Stop recording") {
                        graph.recordingEngine.stop(recordingThis.id); recordTarget = null
                    }
                    liveNow -> RecordActionRow("● Record now", primary = true) {
                        recordScope.launch { graph.recordingEngine.startChannel(channel, programme) }
                        recordTarget = null
                    }
                    else -> RecordActionRow("● Schedule recording", primary = true) {
                        recordScope.launch { graph.recordingEngine.scheduleProgramme(channel, programme) }
                        recordTarget = null
                    }
                }
                RecordActionRow("Record whole series") {
                    recordScope.launch {
                        graph.recordingEngine.recordSeries(channel, programme, targetRow.programmes)
                    }
                    recordTarget = null
                }
                RecordActionRow("Watch channel") {
                    recordTarget = null
                    PlaybackQueue.items = rows.map {
                        PlaybackQueue.Item(it.primary.id, it.primary.displayName, it.primary.logoUrl, it.primary.number)
                    }
                    previewController.stop()
                    onPlayChannel(channel)
                }
                RecordActionRow("Cancel") { recordTarget = null }
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
                    channel.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                nowProg?.let {
                    Text(
                        "Now: ${it.title}",
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
                    RecordActionRow("▶  Watch") {
                        channelMenu = null
                        PlaybackQueue.items = rows.map {
                            PlaybackQueue.Item(it.primary.id, it.primary.displayName, it.primary.logoUrl, it.primary.number)
                        }
                        previewController.stop()
                        onPlayChannel(channel)
                    }
                    if (recordingThis != null) {
                        RecordActionRow("■  Stop recording", primary = true) {
                            graph.recordingEngine.stop(recordingThis.id); channelMenu = null
                        }
                    } else {
                        RecordActionRow("●  Record what's on now", primary = true) {
                            recordScope.launch { graph.recordingEngine.startChannel(channel, nowProg) }
                            channelMenu = null
                        }
                    }
                    if (nowProg != null) {
                        RecordActionRow("Record whole series — ${nowProg.title}") {
                            recordScope.launch {
                                graph.recordingEngine.recordSeries(channel, nowProg, menuRow.programmes)
                            }
                            channelMenu = null
                        }
                    }
                    if (upcoming.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Schedule a later programme",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        upcoming.forEach { programme ->
                            RecordActionRow("${formatTime(programme.startUtcMillis)}   ${programme.title}") {
                                recordScope.launch { graph.recordingEngine.scheduleProgramme(channel, programme) }
                                channelMenu = null
                            }
                        }
                    }
                    RecordActionRow("Cancel") { channelMenu = null }
                }
            }
        }
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

@Composable
private fun RailEntry(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
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
                row.primary.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.now?.let { "${formatTime(it.startUtcMillis)}  ${it.title}" }
                    // Honest rather than blank. A user who sees this on every channel knows
                    // the guide is the problem, not their provider.
                    ?: "No guide information",
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
                    "Next  ${formatTime(next.startUtcMillis)}  ${next.title}",
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
                contentDescription = if (row.primary.favourite) "Remove favourite" else "Favourite",
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
            Text("Loading your channels", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    // Actively assembling — this is work in progress, not a failure.
                    status != null -> status
                    isSyncing ->
                        "Fetching the channel list and guide from your provider. A large " +
                            "provider can take a couple of minutes the first time."
                    // Channels are already on the device; the guide is being built from them.
                    // A big provider takes a moment (longer on this debug build) — not a failure.
                    else ->
                        "Building your guide from the channels saved on this device. On a big " +
                            "provider this takes a moment — it'll appear shortly."
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
            Text("No favourites yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Press the star on any channel and it lands here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No channels yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add a provider to get started.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAddSource) { Text("Add a provider") }
        }
    }
}

/** UTC in the database, device zone on screen. Converted here and nowhere else. */
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(utcMillis: Long): String = timeFormat.format(Date(utcMillis))
