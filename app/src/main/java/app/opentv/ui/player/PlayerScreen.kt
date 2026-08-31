/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.player

import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.core.SleepTimer
import app.opentv.core.findActivity
import app.opentv.core.requestIgnoreBatteryOptimizations
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.Source
import app.opentv.data.model.shownName
import app.opentv.player.PlaybackQueue
import app.opentv.player.PlayerController
import app.opentv.ui.RecordingBackgroundDialog
import app.opentv.ui.RecordingBackgroundPrompt
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen live playback.
 *
 * The video fills the screen with no chrome. A single control bar slides up from the bottom and
 * holds everything — transport (play/pause, rewind, forward), and pickers for subtitles, audio,
 * quality and aspect ratio. It hides after a few seconds and any remote button brings it back.
 * Nothing is ever left permanently painted over the picture.
 *
 * Subtitles and audio come from the actual tracks in the stream ([PlayerController.tracks]) and
 * are selected explicitly — that is the fix for "captions on but nothing shows", which happens
 * when the renderer is merely enabled and left to guess a language.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: Long?,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenMovies: () -> Unit = {},
    onOpenShows: () -> Unit = {},
    onOpenRecordings: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    renderPlayerView: Boolean = true,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val scope = rememberCoroutineScope()
    val subtitlesDefault by settings.subtitlesEnabled.collectAsState()
    val controller = remember {
        graph.livePlayer.also {
            it.player.volume = 1f
        }
    }
    val state by controller.state.collectAsState()
    val tracks by controller.tracks.collectAsState()
    // What's recording right now, so the Record button can show as armed for this channel.
    val activeRecordings by graph.recordingRepository.observeActive().collectAsState(initial = emptyList())

    // Hold the screen awake while the player is on screen. A view-level keepScreenOn flag isn't
    // reliable on every TV box, so we set the window flag on the Activity directly — that's what
    // actually stops the system screensaver from firing mid-programme. keepScreenOn stays on too,
    // as a belt-and-braces backstop.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
        }
    }

    val queue = remember { PlaybackQueue.items }
    var variants by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var currentId by remember { mutableStateOf<Long?>(null) }
    // The channel we were on before this one — powers the "Last channel" recall in the list.
    var previousId by remember { mutableStateOf<Long?>(null) }
    // Digits typed on the remote accumulate here, then jump to that channel number after a beat.
    var numberEntry by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(settings.playerResizeMode.value) }

    var controlsVisible by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var channelListVisible by remember { mutableStateOf(false) }
    var showActionButtonsRow by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    // Offered once per session the first time the user records here while OpenTV isn't exempt from
    // battery optimisation, so the capture survives the screen sleeping. Never blocks recording.
    var showBackgroundPrompt by remember { mutableStateOf(false) }

    var currentChannel by remember { mutableStateOf<Channel?>(null) }
    var currentSource by remember { mutableStateOf<Source?>(null) }
    var currentCategoryName by remember { mutableStateOf<String?>(null) }
    var currentProg by remember { mutableStateOf<Programme?>(null) }
    var nextProg by remember { mutableStateOf<Programme?>(null) }
    var queueProgrammes by remember { mutableStateOf<Map<Long, Programme>>(emptyMap()) }
    val recentChannelIds by settings.recentChannelIds.collectAsState()
    var recentChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val is24 = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val headerDateTimeFmt = remember(is24) {
        if (is24) SimpleDateFormat("EEE, MMM d, HH:mm", Locale.getDefault())
        else SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault())
    }
    val timeFmt = remember(is24) {
        if (is24) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    // Picture-in-picture. While the player is on it is "eligible" to shrink to a floating window
    // (pressing Home does it, handled in MainActivity); [inPip] drives hiding all the chrome.
    val inPip by app.opentv.core.PipState.inPip.collectAsState()
    val pipSupported = remember {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE,
            )
    }
    DisposableEffect(Unit) {
        app.opentv.core.PipState.eligible = true
        onDispose {
            app.opentv.core.PipState.eligible = false
            app.opentv.core.PipState.isPlaying = false
        }
    }
    LaunchedEffect(paused) { app.opentv.core.PipState.isPlaying = !paused }
    val barFocus = remember { FocusRequester() }
    val historyFocus = remember { FocusRequester() }
    val actionButtonsFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    var currentFocusedRow by remember { mutableIntStateOf(0) }

    val enabledSubMenuButtons by settings.enabledSubMenuButtons.collectAsState()
    val audioDelayMs by settings.audioDelayMs.collectAsState()
    var showMultiviewDialog by remember { mutableStateOf(false) }
    var showChannelOptionsDialog by remember { mutableStateOf(false) }
    var selectedSubtitleLabel by remember { mutableStateOf("Off") }
    var selectedAudioLabel by remember { mutableStateOf("Stereo") }

    var menuOpenedAt by remember { mutableLongStateOf(0L) }
    var videoSizeText by remember { mutableStateOf("") }
    var fpsText by remember { mutableStateOf("") }
    var videoCodecText by remember { mutableStateOf("") }
    var audioCodecText by remember { mutableStateOf("") }

    DisposableEffect(controller.player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                paused = !isPlaying
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoSizeText = "${videoSize.width}x${videoSize.height}"
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val videoGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                val videoFormat = videoGroup?.let { g ->
                    (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }
                }
                if (videoFormat != null) {
                    if (videoFormat.width > 0 && videoFormat.height > 0) {
                        videoSizeText = "${videoFormat.width}x${videoFormat.height}"
                    }
                    if (videoFormat.frameRate > 0f) {
                        fpsText = "${videoFormat.frameRate.toInt()} fps"
                    }
                    val mime = videoFormat.sampleMimeType ?: ""
                    videoCodecText = when {
                        mime.contains("avc", ignoreCase = true) || mime.contains("h264", ignoreCase = true) -> "H.264"
                        mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> "HEVC"
                        mime.contains("vp9", ignoreCase = true) -> "VP9"
                        mime.contains("av01", ignoreCase = true) || mime.contains("av1", ignoreCase = true) -> "AV1"
                        mime.isNotEmpty() -> mime.substringAfterLast("/").uppercase()
                        else -> ""
                    }
                }

                val audioGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                val audioFormat = audioGroup?.let { g ->
                    (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }
                }
                if (audioFormat != null) {
                    val ch = when (audioFormat.channelCount) {
                        6 -> "5.1"
                        2 -> "Stereo"
                        1 -> "Mono"
                        else -> if (audioFormat.channelCount > 0) "${audioFormat.channelCount}ch" else ""
                    }
                    val mime = audioFormat.sampleMimeType ?: ""
                    val codec = when {
                        mime.contains("mp4a-latm", ignoreCase = true) || mime.contains("aac", ignoreCase = true) -> "AAC"
                        mime.contains("ac3", ignoreCase = true) || mime.contains("eac3", ignoreCase = true) -> "AC3"
                        mime.contains("opus", ignoreCase = true) -> "Opus"
                        mime.isNotEmpty() -> mime.substringAfterLast("/").uppercase()
                        else -> ""
                    }
                    audioCodecText = listOf(codec, ch).filter { it.isNotEmpty() }.joinToString(" ")
                    selectedAudioLabel = ch.ifEmpty {
                        audioFormat.language?.takeIf { it.isNotBlank() }?.uppercase() ?: "Stereo"
                    }
                }

                val textGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
                val textFormat = textGroup?.let { g ->
                    (0 until g.length).firstOrNull { g.isTrackSelected(it) }?.let { g.getTrackFormat(it) }
                }
                selectedSubtitleLabel = textFormat?.language?.takeIf { it.isNotBlank() }?.uppercase()
                    ?: textFormat?.label?.takeIf { it.isNotBlank() }
                    ?: if (textGroup != null) "On" else "Off"
            }
        }

        val vs = controller.player.videoSize
        if (vs.width > 0 && vs.height > 0) {
            videoSizeText = "${vs.width}x${vs.height}"
        }

        controller.player.addListener(listener)
        onDispose {
            controller.player.removeListener(listener)
        }
    }

    fun reveal() {
        menuOpenedAt = System.currentTimeMillis()
        controlsVisible = true
        showActionButtonsRow = false
        currentFocusedRow = 0
        interaction++
    }

    fun tuneTo(channel: Channel) {
        currentId = channel.id
        paused = false
        settings.lastChannelId = channel.id
        settings.recordChannelWatched(channel.id)
        scope.launch {
            val source = graph.sourceRepository.byId(channel.sourceId)
            // Xtream/M3U carry a ready URL; a Stalker channel's URL is minted here from its cmd.
            val url = graph.catalogRepository.resolvePlaybackUrl(channel, source)
            controller.play(
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

    fun playChannelId(id: Long) {
        controlsVisible = false
        if (currentId == id && (controller.player.playbackState == androidx.media3.common.Player.STATE_READY || controller.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)) {
            return
        }
        // Remember where we came from so "Last channel" can bounce straight back. Quality switches
        // go through tuneTo directly, so they never count as a channel change here.
        currentId?.let { if (it != id) previousId = it }
        currentId = id
        scope.launch {
            val channel = graph.catalogRepository.channel(id) ?: return@launch
            variants = graph.catalogRepository.variants(channel)
            val target = variants.firstOrNull { it.id == channel.id } ?: channel
            tuneTo(target)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (!app.opentv.core.PipState.inPip.value && !paused) {
                        controller.player.playWhenReady = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun zapBy(delta: Int) {
        if (queue.isEmpty()) return
        val cur = queue.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        val next = (cur + delta).coerceIn(0, queue.size - 1)
        if (next != cur) playChannelId(queue[next].id)
    }

    fun toggleRecord() {
        val active = activeRecordings.firstOrNull { it.channelId == currentId }
        if (active != null) {
            graph.recordingEngine.stop(active.id)
        } else {
            val channel = variants.firstOrNull { it.id == currentId } ?: return
            scope.launch { graph.recordingEngine.startChannel(channel) }
            if (RecordingBackgroundPrompt.shouldShow(context)) {
                RecordingBackgroundPrompt.markShown()
                showBackgroundPrompt = true
            }
        }
        interaction++
    }

    LaunchedEffect(channelId) {
        val id = channelId ?: return@LaunchedEffect
        playChannelId(id)
    }

    val playRequest by app.opentv.core.PlayRequests.channelId.collectAsState()
    LaunchedEffect(playRequest) {
        val reqId = playRequest
        if (reqId != null && reqId > 0L) {
            app.opentv.core.PlayRequests.consume()
            if (currentId != reqId) {
                playChannelId(reqId)
            }
        }
    }

    LaunchedEffect(currentId, nowMillis) {
        val id = currentId ?: return@LaunchedEffect
        val channel = graph.catalogRepository.channel(id)
        currentChannel = channel
        if (channel != null) {
            currentSource = graph.sourceRepository.byId(channel.sourceId)
            val catId = channel.categoryId
            if (catId != null) {
                val catNames = graph.database.categories().namesFor(setOf(catId))
                currentCategoryName = catNames.firstOrNull()?.name
            } else {
                currentCategoryName = null
            }
            val epgId = channel.epgChannelId
            if (epgId != null) {
                val up = graph.epgRepository.upcoming(epgId, nowMillis, limit = 4)
                currentProg = up.firstOrNull { nowMillis in it.startUtcMillis until it.endUtcMillis } ?: up.firstOrNull()
                nextProg = if (currentProg != null) {
                    up.firstOrNull { it.startUtcMillis >= (currentProg?.endUtcMillis ?: 0L) }
                } else up.getOrNull(1)
            } else {
                currentProg = null
                nextProg = null
            }
        }
    }

    LaunchedEffect(recentChannelIds, currentId) {
        withContext(Dispatchers.IO) {
            val ids = (listOfNotNull(currentId) + recentChannelIds).distinct()
            val loaded = ids.mapNotNull { graph.catalogRepository.channel(it) }
            withContext(Dispatchers.Main) {
                recentChannels = loaded
            }
        }
    }

    LaunchedEffect(recentChannels, nowMillis) {
        if (recentChannels.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val nowProgs = graph.epgRepository.observeNow(nowMillis).firstOrNull() ?: emptyList()
            val progByEpg = nowProgs.associateBy { it.epgChannelId }
            val map = mutableMapOf<Long, Programme>()
            for (ch in recentChannels) {
                val eId = ch.epgChannelId
                if (eId != null && progByEpg.containsKey(eId)) {
                    map[ch.id] = progByEpg[eId]!!
                }
            }
            withContext(Dispatchers.Main) {
                queueProgrammes = map
            }
        }
    }

    // Sleep timer: when the armed deadline passes, stop and leave the player. Re-arming from
    // settings restarts this effect with the new deadline.
    val sleepDeadline by SleepTimer.deadline.collectAsState()
    LaunchedEffect(sleepDeadline) {
        val d = sleepDeadline ?: return@LaunchedEffect
        val wait = d - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        SleepTimer.clear()
        controller.stop()
        onBack()
    }

    // Apply the saved captions default once the stream's tracks arrive. Only auto-selects when
    // nothing is chosen yet, so it never overrides a track the user picked by hand.
    LaunchedEffect(tracks) {
        val hasText = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
        val textChosen = tracks.groups.any { g ->
            g.type == C.TRACK_TYPE_TEXT && (0 until g.length).any { g.isTrackSelected(it) }
        }
        if (subtitlesDefault && hasText && !textChosen) controller.setSubtitlesEnabled(true)
    }

    // Auto-hide the bar after a few seconds — but never while paused or with a picker open.
    LaunchedEffect(controlsVisible, interaction, state, paused, panel, showActionButtonsRow) {
        if (controlsVisible && !paused && panel == Panel.NONE && !showActionButtonsRow &&
            state is PlayerController.State.Playing
        ) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            controlsVisible = false
        }
    }

    // Focus: a picker's first row when one is open, otherwise the action row if revealed, otherwise the history bar.
    LaunchedEffect(controlsVisible, panel, showActionButtonsRow) {
        if (controlsVisible) {
            delay(16)
            runCatching {
                when {
                    panel != Panel.NONE -> panelFocus.requestFocus()
                    showActionButtonsRow -> actionButtonsFocus.requestFocus()
                    else -> barFocus.requestFocus()
                }
            }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    // Pressing the Back button:
    // 1. Closes picker panel
    // 2. Or closes the action buttons row and returns focus to the history row
    // 3. Or closes the channel list
    // 4. Or closes controls
    // 5. Or returns to the TV Guide
    BackHandler {
        when {
            panel != Panel.NONE -> panel = Panel.NONE
            showActionButtonsRow -> {
                showActionButtonsRow = false
                currentFocusedRow = 1
                scope.launch {
                    delay(16)
                    runCatching { historyFocus.requestFocus() }
                }
            }
            channelListVisible -> channelListVisible = false
            controlsVisible -> controlsVisible = false
            else -> {
                onBack()
            }
        }
    }

    // Number entry: once digits stop coming, jump to that channel number in the browsing list.
    LaunchedEffect(numberEntry) {
        if (numberEntry.isEmpty()) return@LaunchedEffect
        delay(NUMBER_ENTRY_TIMEOUT_MILLIS)
        val num = numberEntry.toIntOrNull()
        numberEntry = ""
        val target = num?.let { n -> queue.firstOrNull { it.number == n } }
        if (target != null) playChannelId(target.id)
    }

    // Focus the channel list when it opens; hand focus back to the video catcher when it closes.
    LaunchedEffect(channelListVisible) {
        if (channelListVisible) {
            delay(16)
            runCatching { listFocus.requestFocus() }
        } else if (!controlsVisible) {
            runCatching { rootFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (renderPlayerView) Color.Black else Color.Transparent)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = keyToDigit(event.key)
                when {
                    // Never swallow Back/Escape — they must reach the back handler.
                    event.key == Key.Back || event.key == Key.Escape -> false
                    // Typing a channel number jumps to it, TiviMate-style.
                    digit != null -> {
                        numberEntry = (numberEntry + digit).take(4)
                        reveal(); interaction++; true
                    }
                    // A picker or the channel list owns the whole d-pad while it's up.
                    channelListVisible || panel != Panel.NONE -> {
                        interaction++
                        false
                    }
                    // With the menu/controls visible:
                    controlsVisible -> {
                        interaction++
                        // Pressing DOWN while on the history buttons (row 1) reveals the action buttons row (row 2)
                        if (event.key == Key.DirectionDown && currentFocusedRow == 1 && !showActionButtonsRow && panel == Panel.NONE && !channelListVisible) {
                            showActionButtonsRow = true
                            currentFocusedRow = 2
                            scope.launch {
                                delay(32)
                                runCatching { actionButtonsFocus.requestFocus() }
                            }
                            return@onPreviewKeyEvent true
                        }
                        // Pressing UP while on the action buttons row (row 2) hides the action buttons row and returns to history buttons (row 1)
                        if (event.key == Key.DirectionUp && showActionButtonsRow && currentFocusedRow == 2 && panel == Panel.NONE && !channelListVisible) {
                            showActionButtonsRow = false
                            currentFocusedRow = 1
                            scope.launch {
                                delay(32)
                                runCatching { historyFocus.requestFocus() }
                            }
                            return@onPreviewKeyEvent true
                        }
                        false
                    }
                    // When in full-screen (controls hidden):
                    // Dedicated Channel Up / Page Up (ONN 4k box remote) or D-Pad Up:
                    event.key == Key.ChannelUp ||
                    event.key == Key.PageUp ||
                    event.nativeKeyEvent.keyCode == 166 || // KEYCODE_CHANNEL_UP
                    event.nativeKeyEvent.keyCode == 92 ||  // KEYCODE_PAGE_UP
                    event.key == Key.DirectionUp
                    -> { zapBy(-1); reveal(); true }

                    // Dedicated Channel Down / Page Down (ONN 4k box remote) or D-Pad Down:
                    event.key == Key.ChannelDown ||
                    event.key == Key.PageDown ||
                    event.nativeKeyEvent.keyCode == 167 || // KEYCODE_CHANNEL_DOWN
                    event.nativeKeyEvent.keyCode == 93 ||  // KEYCODE_PAGE_DOWN
                    event.key == Key.DirectionDown
                    -> { zapBy(1); reveal(); true }

                    // Center/Enter/OK/Info button on remote: reveals the OSD menu without pausing playback.
                    event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter ||
                    event.key == Key.Info ||
                    event.nativeKeyEvent.keyCode == 165 // KEYCODE_INFO
                    -> {
                        reveal()
                        true
                    }

                    // Immersive shortcuts & Guide key when hidden:
                    event.key == Key.DirectionLeft ||
                    event.key == Key.Guide ||
                    event.nativeKeyEvent.keyCode == 172 // KEYCODE_GUIDE
                    -> { if (queue.isNotEmpty()) channelListVisible = true; true }

                    event.key == Key.DirectionRight -> {
                        val targetId = previousId
                            ?: recentChannels.firstOrNull { it.id != currentId }?.id
                            ?: recentChannelIds.firstOrNull { it != currentId }
                        if (targetId != null && targetId != currentId) {
                            playChannelId(targetId)
                        }
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .then(if (!controlsVisible && panel == Panel.NONE && !channelListVisible) Modifier.focusable() else Modifier)
            .pointerInput(Unit) {
                detectTapGestures { if (controlsVisible) controlsVisible = false else reveal() }
            },
    ) {
        if (renderPlayerView) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val targetResizeMode = resizeMode
                    (android.view.LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        subtitleView?.setUserDefaultStyle()
                        subtitleView?.setUserDefaultTextSize()
                        this.resizeMode = targetResizeMode
                        player = controller.player
                    }
                },
                update = { pv ->
                    if (pv.player != controller.player) {
                        pv.player = controller.player
                    }
                    pv.resizeMode = resizeMode
                },
            )
        }

        // The channel number as you type it, top-right, until it resolves.
        if (numberEntry.isNotEmpty() && !inPip) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            ) {
                Text(numberEntry, color = Color.White, style = MaterialTheme.typography.displaySmall)
            }
        }

        when (val current = state) {
            is PlayerController.State.Buffering -> {
                if (!controller.player.isPlaying && controller.player.playbackState != androidx.media3.common.Player.STATE_READY) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(current.title, color = Color.White)
                        }
                    }
                }
            }

            is PlayerController.State.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(48.dp),
                    ) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            current.message,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { controller.retry() }) { Text(stringResource(R.string.common_try_again)) }
                    }
                }
            }

            else -> Unit
        }

        val channelTitle = when (val s = state) {
            is PlayerController.State.Buffering -> s.title
            is PlayerController.State.Playing -> s.title
            is PlayerController.State.Error -> s.title
            else -> ""
        }

        // Top Header: Source & Category on left, Date & Time on right
        AnimatedVisibility(
            visible = controlsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val groupText = buildString {
                    currentSource?.name?.takeIf { it.isNotBlank() }?.let { append(it) }
                    if (isNotEmpty() && !currentCategoryName.isNullOrBlank()) append(" • ")
                    currentCategoryName?.takeIf { it.isNotBlank() }?.let { append(it) }
                }.ifEmpty { currentChannel?.shownName ?: "" }

                Text(
                    text = groupText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f),
                )

                Text(
                    text = headerDateTimeFmt.format(Date(nowMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
        }

        // Bottom Sub Menu Overlay
        AnimatedVisibility(
            visible = controlsVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f),
                                Color.Black.copy(alpha = 0.92f),
                                Color.Black.copy(alpha = 0.98f),
                            )
                        )
                    )
                    .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 12.dp),
            ) {
                // Secondary Option Panel (Subtitles / Audio / Quality / Aspect)
                if (panel != Panel.NONE) {
                    OptionPanel(
                        panel = panel,
                        controller = controller,
                        settings = settings,
                        tracks = tracks,
                        variants = variants,
                        currentId = currentId,
                        resizeMode = resizeMode,
                        onResize = { resizeMode = it; settings.setPlayerResizeMode(it) },
                        onTune = { tuneTo(it) },
                        firstFocus = panelFocus,
                        onDone = { panel = Panel.NONE; interaction++ },
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // ---- Middle Info Section: Logo + Programme details ----
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Channel Logo Badge
                    Box(
                        Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = currentChannel?.logoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(0.85f),
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        // Line 1: Active Show Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentProg?.title ?: currentChannel?.shownName ?: channelTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        // Line 2: Times, remaining duration, channel number/name, telemetry badges
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (currentProg != null) {
                                val startStr = timeFmt.format(Date(currentProg!!.startUtcMillis))
                                val endStr = timeFmt.format(Date(currentProg!!.endUtcMillis))
                                val remainingMins = ((currentProg!!.endUtcMillis - nowMillis) / 60_000L).coerceAtLeast(0)

                                Text(
                                    text = "$startStr – $endStr",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "  —  $remainingMins min   ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            val numStr = currentChannel?.number?.let { "$it " } ?: ""
                            val chName = currentChannel?.shownName ?: channelTitle
                            Text(
                                text = "$numStr$chName",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (videoSizeText.isNotEmpty()) {
                                Spacer(Modifier.width(12.dp))
                                StatBadge(videoSizeText)
                            }
                            if (fpsText.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                StatBadge(fpsText)
                            }
                            if (audioCodecText.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                StatBadge(audioCodecText)
                            }
                            if (videoCodecText.isNotEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                StatBadge(videoCodecText)
                            }
                        }

                        // Line 3: Next Show Preview
                        if (nextProg != null) {
                            Spacer(Modifier.height(2.dp))
                            val nextStart = timeFmt.format(Date(nextProg!!.startUtcMillis))
                            val nextEnd = timeFmt.format(Date(nextProg!!.endUtcMillis))
                            Text(
                                text = "$nextStart – $nextEnd   ${nextProg!!.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ---- Cyan Timeline Progress Bar ----
                val progFraction = currentProg?.progressAt(nowMillis) ?: 0f
                LiveTimelineBar(
                    progress = progFraction,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // ---- Progress Time (Left), 5 Center Controls, and Live + Record (Right) ----
                val isRecording = activeRecordings.any { it.channelId == currentId }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    // Left: Programme progress time (e.g. 24:29 / 1:00:00)
                    val prog = currentProg
                    if (prog != null && prog.durationMillis > 0L) {
                        val elapsed = (nowMillis - prog.startUtcMillis).coerceIn(0L, prog.durationMillis)
                        val total = prog.durationMillis
                        Text(
                            text = "${formatDurationMs(elapsed)} / ${formatDurationMs(total)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 2.dp),
                        )
                    }

                    // Center: 5 transport buttons (SkipPrevious, FastRewind, Play/Pause, FastForward, SkipNext)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Skip Previous (|◀) - Jumps to start of programme
                        TransportButton(
                            icon = Icons.Filled.SkipPrevious,
                            contentDescription = stringResource(R.string.player_rewind),
                            size = 38.dp,
                            iconSize = 20.dp,
                            onFocusChanged = { if (it) currentFocusedRow = 0 },
                            onClick = {
                                controller.seekBackward()
                                interaction++
                            },
                        )

                        // Fast Rewind (◀◀) -10s
                        TransportButton(
                            icon = Icons.Filled.FastRewind,
                            contentDescription = stringResource(R.string.player_rewind),
                            size = 38.dp,
                            iconSize = 20.dp,
                            onFocusChanged = { if (it) currentFocusedRow = 0 },
                            onClick = {
                                controller.seekBackward()
                                val cur = controller.player.currentPosition
                                controller.player.seekTo((cur - 10_000L).coerceAtLeast(0L))
                                interaction++
                            },
                        )

                        // Play / Pause (|| / ▶) - Solid white circle with dark icon
                        TransportButton(
                            icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (paused) stringResource(R.string.player_play) else stringResource(R.string.player_pause),
                            size = 46.dp,
                            iconSize = 24.dp,
                            isPrimary = true,
                            focusRequester = barFocus,
                            onFocusChanged = { if (it) currentFocusedRow = 0 },
                            onClick = {
                                val targetPlaying = paused
                                controller.player.playWhenReady = targetPlaying
                                paused = !targetPlaying
                                interaction++
                            },
                        )

                        // Fast Forward (▶▶) +10s
                        TransportButton(
                            icon = Icons.Filled.FastForward,
                            contentDescription = stringResource(R.string.player_forward),
                            size = 38.dp,
                            iconSize = 20.dp,
                            onFocusChanged = { if (it) currentFocusedRow = 0 },
                            onClick = {
                                controller.seekForward()
                                val cur = controller.player.currentPosition
                                val dur = controller.player.duration
                                if (dur > 0) {
                                    controller.player.seekTo((cur + 10_000L).coerceAtMost(dur))
                                } else {
                                    controller.player.seekTo(cur + 10_000L)
                                }
                                interaction++
                            },
                        )

                        // Skip Next (▶|) - Jumps to live edge
                        TransportButton(
                            icon = Icons.Filled.SkipNext,
                            contentDescription = stringResource(R.string.player_forward),
                            size = 38.dp,
                            iconSize = 20.dp,
                            onFocusChanged = { if (it) currentFocusedRow = 0 },
                            onClick = {
                                controller.player.seekToDefaultPosition()
                                controller.player.playWhenReady = true
                                paused = false
                                interaction++
                            },
                        )
                    }

                    // Right: [LIVE] badge button + Record circular button
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // LIVE Badge Button
                        LiveBadgeButton(
                            onFocusChanged = { if (it) currentFocusedRow = 0 },
                            onClick = {
                                controller.player.seekToDefaultPosition()
                                controller.player.playWhenReady = true
                                paused = false
                                Toast.makeText(context, "LIVE", Toast.LENGTH_SHORT).show()
                                interaction++
                            },
                        )

                        // Record Button
                        TransportButton(
                            icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                            contentDescription = if (isRecording) stringResource(R.string.rec_stop_recording) else stringResource(R.string.player_record),
                            size = 38.dp,
                            iconSize = 20.dp,
                            iconTint = if (isRecording) Color(0xFFE53935) else Color.White,
                            onFocusChanged = { if (it) currentFocusedRow = 0 },
                            onClick = {
                                toggleRecord()
                                interaction++
                            },
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // ---- Watched Channels History Carousel ----
                val quickListState = rememberLazyListState()
                LazyRow(
                    state = quickListState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = if (showActionButtonsRow) 6.dp else 2.dp),
                ) {
                    // Card 1: TV guide
                    item(key = "quick-guide") {
                        QuickActionCard(
                            icon = Icons.Filled.ViewStream,
                            label = stringResource(R.string.player_tv_guide),
                            focusRequester = if (recentChannels.isEmpty()) historyFocus else null,
                            onFocusChanged = { if (it) currentFocusedRow = 1 },
                            onClick = {
                                controlsVisible = false
                                onBack()
                            },
                        )
                    }

                    // Card 2: History (Last channel)
                    item(key = "quick-history") {
                        QuickActionCard(
                            icon = Icons.Filled.History,
                            label = stringResource(R.string.player_history),
                            onFocusChanged = { if (it) currentFocusedRow = 1 },
                            onClick = {
                                val targetId = previousId ?: recentChannels.firstOrNull { it.id != currentId }?.id
                                if (targetId != null) playChannelId(targetId)
                            },
                        )
                    }

                    // Cards 3+: Watched Channels History (newest first)
                    itemsIndexed(recentChannels, key = { _, ch -> "recent-ch-${ch.id}" }) { idx, ch ->
                        QuickChannelCard(
                            channel = ch,
                            programme = queueProgrammes[ch.id],
                            isCurrent = ch.id == currentId,
                            focusRequester = if (idx == 0) historyFocus else null,
                            onFocusChanged = { if (it) currentFocusedRow = 1 },
                            onClick = { playChannelId(ch.id) },
                        )
                    }

                    // Card End: Clear History Button
                    if (recentChannels.isNotEmpty()) {
                        item(key = "quick-clear-history") {
                            QuickActionCard(
                                icon = Icons.Filled.Delete,
                                label = stringResource(R.string.history_clear),
                                onFocusChanged = { if (it) currentFocusedRow = 1 },
                                onClick = {
                                    settings.clearRecentChannels()
                                    Toast.makeText(context, context.getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                }

                // ---- Downward Reveal for Action Buttons ----
                AnimatedVisibility(
                    visible = showActionButtonsRow,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val subMenuListState = rememberLazyListState()

                    LazyRow(
                        state = subMenuListState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        var isFirstItem = true
                        AppSettings.SubMenuButton.entries.forEach { btn ->
                            if (enabledSubMenuButtons.contains(btn)) {
                                val attachFocus = isFirstItem
                                isFirstItem = false
                                item(key = "sub-btn-${btn.key}") {
                                    when (btn) {
                                        AppSettings.SubMenuButton.SEARCH -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Search,
                                                label = stringResource(R.string.submenu_search),
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    controlsVisible = false
                                                    onOpenSearch()
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.MOVIES -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Movie,
                                                label = stringResource(R.string.submenu_movies),
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    controlsVisible = false
                                                    onOpenMovies()
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.SHOWS -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Tv,
                                                label = stringResource(R.string.submenu_shows),
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    controlsVisible = false
                                                    onOpenShows()
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.RECORDINGS -> {
                                            val recordingThis = activeRecordings.any { it.channelId == currentId }
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.FiberManualRecord,
                                                label = if (recordingThis) "Recording" else stringResource(R.string.submenu_recordings),
                                                isSelected = recordingThis,
                                                iconTint = if (recordingThis) Color(0xFFE53935) else null,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = { toggleRecord() },
                                            )
                                        }
                                        AppSettings.SubMenuButton.MULTIVIEW -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.GridView,
                                                label = stringResource(R.string.submenu_multiview),
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = { showMultiviewDialog = true },
                                            )
                                        }
                                        AppSettings.SubMenuButton.QUALITY -> {
                                            val qualLabel = if (videoSizeText.isNotEmpty()) videoSizeText.replace("x", " × ") else "Quality"
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Videocam,
                                                label = qualLabel,
                                                isSelected = panel == Panel.QUALITY,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    panel = if (panel == Panel.QUALITY) Panel.NONE else Panel.QUALITY
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.AUDIO -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.VolumeUp,
                                                label = selectedAudioLabel,
                                                isSelected = panel == Panel.AUDIO,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    panel = if (panel == Panel.AUDIO) Panel.NONE else Panel.AUDIO
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.AUDIO_DELAY -> {
                                            val delayLabel = if (audioDelayMs == 0) "0 ms" else if (audioDelayMs > 0) "+$audioDelayMs ms" else "$audioDelayMs ms"
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.SyncAlt,
                                                label = delayLabel,
                                                isSelected = panel == Panel.AUDIO_DELAY,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    panel = if (panel == Panel.AUDIO_DELAY) Panel.NONE else Panel.AUDIO_DELAY
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.SUBTITLES -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.ClosedCaption,
                                                label = selectedSubtitleLabel,
                                                isSelected = panel == Panel.SUBTITLES,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    panel = if (panel == Panel.SUBTITLES) Panel.NONE else Panel.SUBTITLES
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.ASPECT_RATIO -> {
                                            val aspectLabel = when (resizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Normal"
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Fill"
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
                                                else -> "Normal"
                                            }
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.AspectRatio,
                                                label = aspectLabel,
                                                isSelected = panel == Panel.ASPECT,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    panel = if (panel == Panel.ASPECT) Panel.NONE else Panel.ASPECT
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.CHANNELS_LIST -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.FormatListBulleted,
                                                label = stringResource(R.string.submenu_channels_list),
                                                isSelected = channelListVisible,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    channelListVisible = !channelListVisible
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.FAVORITES -> {
                                            val isFav = currentChannel?.favourite == true
                                            SubMenuButtonCard(
                                                icon = if (isFav) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                label = if (isFav) stringResource(R.string.submenu_in_favorites) else stringResource(R.string.submenu_add_favorites),
                                                isSelected = isFav,
                                                iconTint = if (isFav) Color(0xFFFFD54F) else null,
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    val ch = currentChannel
                                                    if (ch != null) {
                                                        val newFav = !ch.favourite
                                                        scope.launch {
                                                            graph.catalogRepository.setChannelFavourite(ch.id, newFav)
                                                            currentChannel = ch.copy(favourite = newFav)
                                                            withContext(Dispatchers.Main) {
                                                                val msg = if (newFav) "Added to Favorites" else "Removed from Favorites"
                                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.CHANNEL_OPTIONS -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.SettingsSuggest,
                                                label = stringResource(R.string.submenu_channel_options),
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = { showChannelOptionsDialog = true },
                                            )
                                        }
                                        AppSettings.SubMenuButton.SETTINGS -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Settings,
                                                label = stringResource(R.string.submenu_settings),
                                                focusRequester = if (attachFocus) actionButtonsFocus else null,
                                                onFocusChanged = { if (it) currentFocusedRow = 2 },
                                                onClick = {
                                                    controlsVisible = false
                                                    onOpenSettings()
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Left-side transparent channel list — d-pad Left opens it, pick a channel to switch.
        AnimatedVisibility(
            visible = channelListVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            val currentIndex = queue.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            val listState = rememberLazyListState()
            LaunchedEffect(Unit) { runCatching { listState.scrollToItem(currentIndex) } }
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(380.dp)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    stringResource(R.string.common_channels),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                // A quick "jump back to the channel you just left" pin, TiviMate-style.
                val lastItem = previousId?.let { pid -> queue.firstOrNull { it.id == pid } }
                if (lastItem != null && lastItem.id != currentId) {
                    ChannelListRow(
                        item = lastItem,
                        playing = false,
                        focusRequester = null,
                        leadingLabel = stringResource(R.string.player_last),
                        onClick = {
                            playChannelId(lastItem.id)
                            channelListVisible = false
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                }
                LazyColumn(state = listState) {
                    itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                        ChannelListRow(
                            item = item,
                            playing = item.id == currentId,
                            focusRequester = if (index == currentIndex) listFocus else null,
                            onClick = {
                                playChannelId(item.id)
                                channelListVisible = false
                            },
                        )
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

    if (showMultiviewDialog) {
        AlertDialog(
            onDismissRequest = { showMultiviewDialog = false },
            title = { Text(stringResource(R.string.submenu_multiview_title), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.submenu_multiview_desc),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showMultiviewDialog = false }) {
                    Text(stringResource(R.string.common_done), color = Color(0xFF26C6DA), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF18222C),
        )
    }

    if (showChannelOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showChannelOptionsDialog = false },
            title = { Text(stringResource(R.string.submenu_channel_options_title), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val ch = currentChannel
                    val src = currentSource
                    Text("Channel: ${ch?.shownName ?: "Live TV"}", color = Color.White, fontWeight = FontWeight.SemiBold)
                    if (ch?.number != null) Text("Number: ${ch.number}", color = Color.White.copy(alpha = 0.8f))
                    if (src != null) Text("Provider: ${src.name}", color = Color.White.copy(alpha = 0.8f))
                    if (videoSizeText.isNotEmpty()) Text("Resolution: $videoSizeText $fpsText", color = Color.White.copy(alpha = 0.8f))
                    if (audioCodecText.isNotEmpty()) Text("Audio: $audioCodecText", color = Color.White.copy(alpha = 0.8f))
                    if (videoCodecText.isNotEmpty()) Text("Video Codec: $videoCodecText", color = Color.White.copy(alpha = 0.8f))
                }
            },
            confirmButton = {
                TextButton(onClick = { showChannelOptionsDialog = false }) {
                    Text(stringResource(R.string.common_done), color = Color(0xFF26C6DA), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF18222C),
        )
    }
}

@Composable
private fun ChannelListRow(
    item: PlaybackQueue.Item,
    playing: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    leadingLabel: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        playing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        else -> Color.Transparent
    }
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A "Last" tag, or the channel number if the provider gives one — a fixed-width slot so
        // the logos and names line up down the list.
        Text(
            leadingLabel ?: item.number?.toString().orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = fg.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier.width(40.dp),
        )
        AsyncImage(
            model = item.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(item.name, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class Panel { NONE, SUBTITLES, AUDIO, QUALITY, ASPECT, AUDIO_DELAY }

@OptIn(UnstableApi::class)
@Composable
private fun OptionPanel(
    panel: Panel,
    controller: PlayerController,
    settings: app.opentv.core.AppSettings,
    tracks: Tracks,
    variants: List<Channel>,
    currentId: Long?,
    resizeMode: Int,
    onResize: (Int) -> Unit,
    onTune: (Channel) -> Unit,
    firstFocus: FocusRequester,
    onDone: () -> Unit,
) {
    val offLabel = stringResource(R.string.player_subtitles_off)
    val standardLabel = stringResource(R.string.player_quality_standard)
    val fitLabel = stringResource(R.string.player_aspect_fit)
    val fillLabel = stringResource(R.string.player_aspect_fill)
    val stretchLabel = stringResource(R.string.player_aspect_stretch)
    val audioDelayMs by settings.audioDelayMs.collectAsState()
    val options: List<Option> = when (panel) {
        Panel.SUBTITLES -> buildSubtitleOptions(controller, settings, tracks, offLabel, onDone)
        Panel.AUDIO -> buildAudioOptions(controller, tracks, onDone)
        Panel.QUALITY -> variants.map { v ->
            Option(v.qualityLabel.ifEmpty { standardLabel }, v.id == currentId) { onTune(v); onDone() }
        }
        Panel.ASPECT -> listOf(
            Option(fitLabel, resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                onResize(AspectRatioFrameLayout.RESIZE_MODE_FIT); onDone()
            },
            Option(fillLabel, resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                onResize(AspectRatioFrameLayout.RESIZE_MODE_ZOOM); onDone()
            },
            Option(stretchLabel, resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                onResize(AspectRatioFrameLayout.RESIZE_MODE_FILL); onDone()
            },
        )
        Panel.AUDIO_DELAY -> listOf(
            Option("-200 ms", audioDelayMs == -200) { settings.setAudioDelayMs(-200); onDone() },
            Option("-100 ms", audioDelayMs == -100) { settings.setAudioDelayMs(-100); onDone() },
            Option("-50 ms", audioDelayMs == -50) { settings.setAudioDelayMs(-50); onDone() },
            Option("0 ms (Default)", audioDelayMs == 0) { settings.setAudioDelayMs(0); onDone() },
            Option("+50 ms", audioDelayMs == 50) { settings.setAudioDelayMs(50); onDone() },
            Option("+100 ms", audioDelayMs == 100) { settings.setAudioDelayMs(100); onDone() },
            Option("+200 ms", audioDelayMs == 200) { settings.setAudioDelayMs(200); onDone() },
            Option("+300 ms", audioDelayMs == 300) { settings.setAudioDelayMs(300); onDone() },
            Option("+500 ms", audioDelayMs == 500) { settings.setAudioDelayMs(500); onDone() },
        )
        Panel.NONE -> emptyList()
    }

    val title = when (panel) {
        Panel.SUBTITLES -> stringResource(R.string.player_subtitles)
        Panel.AUDIO -> stringResource(R.string.player_audio)
        Panel.QUALITY -> stringResource(R.string.player_quality)
        Panel.ASPECT -> stringResource(R.string.player_aspect_ratio_title)
        Panel.AUDIO_DELAY -> stringResource(R.string.submenu_audio_delay_title)
        Panel.NONE -> ""
    }

    Column(
        Modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (options.isEmpty()) {
                Text(stringResource(R.string.player_none_available), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
            }
            options.forEachIndexed { index, option ->
                OptionRow(
                    label = option.label,
                    selected = option.selected,
                    onClick = option.onClick,
                    focusRequester = if (index == 0) firstFocus else null,
                )
            }
        }
    }
}

private data class Option(val label: String, val selected: Boolean, val onClick: () -> Unit)

@OptIn(UnstableApi::class)
private fun buildSubtitleOptions(
    controller: PlayerController,
    settings: app.opentv.core.AppSettings,
    tracks: Tracks,
    offLabel: String,
    onDone: () -> Unit,
): List<Option> {
    val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val anySelected = textGroups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
    val list = mutableListOf<Option>()
    list += Option(offLabel, !anySelected) {
        controller.disableText()
        settings.setSubtitlesEnabled(false)
        onDone()
    }
    textGroups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            list += Option(trackLabel(format.label, format.language, list.size), group.isTrackSelected(i)) {
                controller.selectTrack(group, i)
                settings.setSubtitlesEnabled(true)
                onDone()
            }
        }
    }
    return list
}

@OptIn(UnstableApi::class)
private fun buildAudioOptions(
    controller: PlayerController,
    tracks: Tracks,
    onDone: () -> Unit,
): List<Option> {
    val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val list = mutableListOf<Option>()
    audioGroups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            list += Option(trackLabel(format.label, format.language, list.size), group.isTrackSelected(i)) {
                controller.selectTrack(group, i)
                onDone()
            }
        }
    }
    return list
}

private fun trackLabel(label: String?, language: String?, index: Int): String {
    if (!label.isNullOrBlank()) return label
    if (!language.isNullOrBlank() && language != "und") {
        return runCatching { Locale(language).displayLanguage.ifBlank { language } }.getOrDefault(language)
    }
    return "Track ${index + 1}"
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_selected), tint = fg)
        }
    }
}

@Composable
private fun StatBadge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(108.dp)
            .height(84.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color(0xFF1E2833).copy(alpha = 0.88f),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else Modifier.border(0.5.dp, Color(0xFF2C3E50), RoundedCornerShape(8.dp)),
            )
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (focused) Color(0xFF10171E) else Color.White,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color = if (focused) Color(0xFF10171E) else Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickChannelCard(
    channel: Channel,
    programme: Programme?,
    isCurrent: Boolean,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(148.dp)
            .height(84.dp)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else if (isCurrent) Color(0xFF1A2F3E).copy(alpha = 0.95f)
                else Color(0xFF1E2833).copy(alpha = 0.88f),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else if (isCurrent) Modifier.border(1.5.dp, Color(0xFF26C6DA), RoundedCornerShape(8.dp))
                else Modifier.border(0.5.dp, Color(0xFF2C3E50), RoundedCornerShape(8.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = channel.shownName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (focused || isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (focused) Color(0xFF10171E) else if (isCurrent) Color(0xFF26C6DA) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = programme?.title ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = if (focused) Color(0xFF37474F) else Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LiveTimelineBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val widthDp = remember(widthPx) { with(density) { widthPx.toDp() } }
    val safeProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        )

        Box(
            Modifier
                .fillMaxWidth(safeProgress)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF26C6DA)),
        )

        if (widthDp > 0.dp) {
            val dotOffset = (widthDp - 10.dp) * safeProgress
            Box(
                Modifier
                    .padding(start = dotOffset.coerceAtLeast(0.dp))
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun LiveBadgeButton(
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Color.White else Color(0xFF101720).copy(alpha = 0.65f)
    val contentColor = if (focused) Color(0xFF10171E) else Color.White
    val borderColor = if (focused) Color.White else Color.White.copy(alpha = 0.55f)

    Box(
        modifier = Modifier
            .height(28.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.Bold,
            color = contentColor,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    focusRequester: FocusRequester? = null,
    isPrimary: Boolean = false,
    iconTint: Color? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color.White
        isPrimary -> Color.White
        else -> Color(0xFF101720).copy(alpha = 0.65f)
    }
    val icTint = when {
        focused -> if (iconTint == Color(0xFFE53935)) Color(0xFFE53935) else Color(0xFF10171E)
        isPrimary -> Color(0xFF10171E)
        iconTint != null -> iconTint
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .size(size)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clip(CircleShape)
            .background(bg)
            .then(
                if (focused) Modifier.border(2.5.dp, if (isPrimary) Color(0xFF26C6DA) else Color.White, CircleShape)
                else if (isPrimary) Modifier.border(1.dp, Color.White, CircleShape)
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape)
            )
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = icTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun BarChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val content = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = if (focused) content else (iconTint ?: content))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SubMenuButtonCard(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    iconTint: Color? = null,
    focusRequester: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color(0xFFFFFFFF)
        isSelected -> Color(0xFF1E3A4B)
        else -> Color(0xFF18222C)
    }
    val icTint = when {
        focused -> Color(0xFF10171E)
        iconTint != null -> iconTint
        isSelected -> Color(0xFF26C6DA)
        else -> Color.White
    }
    val textColor = when {
        focused -> Color.White
        isSelected -> Color(0xFF26C6DA)
        else -> Color.White.copy(alpha = 0.85f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 68.dp)
            .padding(horizontal = 2.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(bg)
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, CircleShape)
                    else if (isSelected) Modifier.border(1.5.dp, Color(0xFF26C6DA), CircleShape)
                    else Modifier.border(1.dp, Color(0xFF263442), CircleShape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = icTint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
            fontWeight = if (focused || isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val CONTROLS_TIMEOUT_MILLIS = 5_000L
private const val NUMBER_ENTRY_TIMEOUT_MILLIS = 2_000L

/** Maps a remote's number keys (top row and numeric keypad) to a digit, or null for other keys. */
private fun keyToDigit(key: Key): Char? = when (key) {
    Key.Zero, Key.NumPad0 -> '0'
    Key.One, Key.NumPad1 -> '1'
    Key.Two, Key.NumPad2 -> '2'
    Key.Three, Key.NumPad3 -> '3'
    Key.Four, Key.NumPad4 -> '4'
    Key.Five, Key.NumPad5 -> '5'
    Key.Six, Key.NumPad6 -> '6'
    Key.Seven, Key.NumPad7 -> '7'
    Key.Eight, Key.NumPad8 -> '8'
    Key.Nine, Key.NumPad9 -> '9'
    else -> null
}
