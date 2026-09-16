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
import app.opentv.ui.components.tvFocus
import app.opentv.ui.theme.AppTheme
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
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
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
import app.opentv.core.CatchupPlayback
import app.opentv.core.CatchupResolver
import app.opentv.core.DisplayRefresh
import app.opentv.core.RecentChannelRef
import app.opentv.core.RecentChannels
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
import kotlinx.coroutines.Job
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
enum class OsdTier {
    SHORTCUTS, // Tier 0 (Down from History)
    HISTORY,   // Tier 1 (Default on OK)
    CONTROLS,  // Tier 2 (Up from History)
    TIMELINE,  // Tier 3 (Up from Controls)
}

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
    /** The archive programme the shared player is on, when the guide launched catch-up. */
    catchup: CatchupPlayback? = null,
    /** Reports catch-up starting/ending (e.g. "Watch from start") back to the guide. */
    onCatchupChange: (CatchupPlayback?) -> Unit = {},
    renderPlayerView: Boolean = true,
    onChannelChange: ((Long) -> Unit)? = null,
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
            if (renderPlayerView) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                view.keepScreenOn = false
            }
        }
    }

    var queue by remember { mutableStateOf(PlaybackQueue.items) }
    LaunchedEffect(PlaybackQueue.items) {
        if (PlaybackQueue.items.isNotEmpty()) {
            queue = PlaybackQueue.items
        }
    }
    var variants by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var currentId by remember { mutableStateOf(channelId) }
    // The channel we were on before this one — powers the "Last channel" recall in the list.
    var previousId by remember { mutableStateOf<Long?>(null) }
    // Digits typed on the remote accumulate here, then jump to that channel number after a beat.
    var numberEntry by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(settings.playerResizeMode.value) }

    var controlsVisible by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var channelListVisible by remember { mutableStateOf(false) }
    var osdTier by remember { mutableStateOf(OsdTier.HISTORY) }

    // TiviMate-style timeline: each Left/Right press is an immediate skip (10s taps, accelerating
    // while held). The pip on the bar tracks the player's real position, so every skip is visible.
    // Playback seeks on every press — that's the point; the OSD stays up while keys repeat.
    var interaction by remember { mutableIntStateOf(0) }
    // Offered once per session the first time the user records here while OpenTV isn't exempt from
    // battery optimisation, so the capture survives the screen sleeping. Never blocks recording.
    var showBackgroundPrompt by remember { mutableStateOf(false) }

    var currentChannel by remember { mutableStateOf<Channel?>(null) }
    var currentSource by remember { mutableStateOf<Source?>(null) }
    var currentCategoryName by remember { mutableStateOf<String?>(null) }
    var currentProg by remember { mutableStateOf<Programme?>(null) }
    var nextProg by remember { mutableStateOf<Programme?>(null) }
    // Archive programme the shared player is on (null = live). Seeded from the guide's session and
    // kept in step with it; "Watch from start" can promote the current channel into catch-up too.
    var activeCatchup by remember { mutableStateOf(catchup) }
    LaunchedEffect(catchup) { activeCatchup = catchup }
    var queueProgrammes by remember { mutableStateOf<Map<Long, Programme>>(emptyMap()) }
    val recentChannelRefs by settings.recentChannelRefs.collectAsState()
    var recentChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val headerDateTimeFmt = remember { SimpleDateFormat("EEE, MMM d, h:mm a", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

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
    val timelineFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }

    val enabledSubMenuButtons by settings.enabledSubMenuButtons.collectAsState()
    // The shortcut row is walked by index rather than left to the focus system, so Left/Right can
    // wrap around: at the end it loops to the start and vice versa. Each visible button carries its
    // own requester for that; [subMenuFocusedIndex] is the source of truth for where we are.
    val visibleSubMenuButtons = remember(enabledSubMenuButtons) {
        AppSettings.SubMenuButton.entries.filter { it in enabledSubMenuButtons }
    }
    val subMenuFocusRequesters = remember { mutableMapOf<AppSettings.SubMenuButton, FocusRequester>() }
    val subMenuListState = rememberLazyListState()
    var subMenuFocusedIndex by remember { mutableIntStateOf(0) }
    var subMenuNavJob by remember { mutableStateOf<Job?>(null) }

    /**
     * Steps d-pad focus through the shortcut row by index and wraps at both ends. Driving it by
     * index rather than letting Compose's 2D focus search run off the end means Right on the last
     * shortcut returns to the first, and Left on the first goes to the last.
     */
    fun moveSubMenuFocus(isRight: Boolean) {
        val count = visibleSubMenuButtons.size
        if (count == 0) return
        val from = subMenuFocusedIndex.coerceIn(0, count - 1)
        val targetIndex = if (isRight) (from + 1) % count else (from - 1 + count) % count
        val targetButton = visibleSubMenuButtons[targetIndex]
        // Move optimistically so a held key keeps stepping before focus lands.
        subMenuFocusedIndex = targetIndex
        subMenuNavJob?.cancel()
        subMenuNavJob = scope.launch {
            fun requestTarget(): Boolean {
                val req = subMenuFocusRequesters[targetButton] ?: return false
                return runCatching { req.requestFocus() }.isSuccess
            }
            // A button past the viewport hasn't been composed yet, so its requester can't take
            // focus: scroll it into range first, then retry.
            if (!requestTarget()) {
                runCatching { subMenuListState.scrollToItem(targetIndex) }
                delay(30)
                for (attempt in 0..5) {
                    if (requestTarget()) break
                    delay(40)
                }
            }
        }
    }

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
                        // Match the panel to the stream — 50 Hz for a 50 fps channel — so a panning
                        // camera stops hitching once a second. Opt-in: see DisplayRefresh.
                        if (settings.matchRefreshRate.value) {
                            context.findActivity()?.let { DisplayRefresh.match(it, videoFormat.frameRate) }
                        }
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
            // Hand the display back to the system, so a box is never left on 50 Hz chosen by a
            // channel that stopped playing. No-op when nothing was requested.
            context.findActivity()?.let { DisplayRefresh.restore(it) }
        }
    }

    fun reveal() {
        menuOpenedAt = System.currentTimeMillis()
        controlsVisible = true
        panel = Panel.NONE
        osdTier = OsdTier.HISTORY
        interaction++
    }

    fun watchFromStart() {
        val ch = currentChannel ?: return
        val prog = currentProg
        scope.launch {
            val source = withContext(Dispatchers.IO) { graph.sourceRepository.byId(ch.sourceId) }
            val effectiveProg = prog ?: run {
                val now = System.currentTimeMillis()
                val halfHourMs = 30 * 60 * 1000L
                val start = (now / halfHourMs) * halfHourMs
                Programme(
                    id = 0L,
                    feedId = 0L,
                    epgChannelId = ch.epgChannelId ?: ch.streamId,
                    title = ch.shownName,
                    description = "",
                    startUtcMillis = start,
                    endUtcMillis = start + halfHourMs,
                    category = null,
                )
            }
            val catchupUrl = if (source != null) {
                withContext(Dispatchers.IO) { CatchupResolver.resolve(source, ch, effectiveProg) }
            } else null

            if (catchupUrl != null) {
                // Play the archive in the SAME player, not a separate screen. Recording the session
                // lets the OSD label it and lets the guide keep its scrubbed position on Back.
                val session = CatchupPlayback(
                    channelId = ch.id,
                    channelName = ch.shownName,
                    programmeTitle = effectiveProg.title,
                    startUtcMillis = effectiveProg.startUtcMillis,
                    endUtcMillis = effectiveProg.endUtcMillis,
                    url = catchupUrl,
                    userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                )
                activeCatchup = session
                onCatchupChange(session)
                controller.play(
                    PlayerController.Request(
                        url = catchupUrl,
                        title = "${ch.shownName} — ${effectiveProg.title}",
                        userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                        startPositionMillis = 0L,
                        isLive = false,
                    ),
                    debounce = false,
                )
                Toast.makeText(context, "Catch-up: Playing from start", Toast.LENGTH_SHORT).show()
            } else {
                controller.player.seekTo(0L)
                controller.player.playWhenReady = true
                paused = false
                Toast.makeText(context, "Playing from start", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun tuneTo(channel: Channel) {
        currentId = channel.id
        currentChannel = channel
        paused = false
        if (activeCatchup != null) {
            activeCatchup = null
            onCatchupChange(null)
        }
        settings.lastChannelId = channel.id
        settings.recordChannelWatched(channel.sourceId, channel.streamId)
        onChannelChange?.invoke(channel.id)
        scope.launch {
            val (source, url) = withContext(Dispatchers.IO) {
                val src = graph.sourceRepository.byId(channel.sourceId)
                val resolvedUrl = graph.catalogRepository.resolvePlaybackUrl(channel, src)
                src to resolvedUrl
            }
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
        // Zapping (or picking from the channel list) leaves archive playback. Force a retune even if
        // the same channel id is already open, because the media item is currently the timeshift
        // stream, not the live one.
        val wasArchive = activeCatchup != null
        if (wasArchive) {
            activeCatchup = null
            onCatchupChange(null)
        }
        val isAlreadyPlaying = !wasArchive && (currentId == id || settings.lastChannelId == id) &&
            controller.player.currentMediaItem != null &&
            (controller.player.playbackState == androidx.media3.common.Player.STATE_READY ||
             controller.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)

        if (isAlreadyPlaying) {
            currentId = id
            return
        }
        // Cut old channel immediately so it never lingers on screen
        controller.player.stop()
        controller.player.clearMediaItems()
        currentId?.let {
            if (it != id) {
                previousId = it
                // Remember it past this player instance too, so "previous channel" still works
                // after leaving and re-entering from a different category.
                PlaybackQueue.previousChannelId = it
            }
        }
        currentId = id
        scope.launch {
            val (target, source, url) = withContext(Dispatchers.IO) {
                // A stored id can outlive the row it names. ChannelDao.deleteStale drops every
                // channel a provider did not list in the latest sync, and one that comes back is
                // INSERTED, so it returns under a *new* id; deleting and re-adding a playlist
                // reassigns them all at once. Resolving strictly by id therefore finds nothing while
                // the channel itself is alive — and because the previous stream was already cut
                // above, the player would sit on a blank screen with no error to explain it.
                //
                // This is the path boot-to-last-channel takes, since it resumes settings
                // .lastChannelId, a bare row id. Fall back to the newest watched ref instead: it is
                // the same channel (watch history is written on every tune right beside the id), and
                // its source+stream key is precisely what survives a re-sync — see RecentChannels.
                // The id is repaired further down on success, so the next launch resolves first try.
                val ch = graph.catalogRepository.channel(id)
                    ?: graph.catalogRepository.channelsForRefs(settings.recentChannelRefs.value).firstOrNull()
                    ?: return@withContext null
                val vars = graph.catalogRepository.variants(ch)
                val tgt = vars.firstOrNull { it.id == ch.id } ?: ch
                val src = graph.sourceRepository.byId(tgt.sourceId)
                val resolvedUrl = graph.catalogRepository.resolvePlaybackUrl(tgt, src)
                Triple(tgt, src, resolvedUrl)
            } ?: run {
                // Nothing to tune: the id is gone and the history holds nothing the catalogue can
                // still resolve. Say so, rather than leaving a silently black screen. Guarded on the
                // id so a channel the user has zapped to meanwhile keeps the player to itself.
                if (currentId == id) {
                    Toast.makeText(context, "That channel is no longer available", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            currentChannel = target
            paused = false
            settings.lastChannelId = target.id
            settings.recordChannelWatched(target.sourceId, target.streamId)
            onChannelChange?.invoke(target.id)

            controller.play(
                PlayerController.Request(
                    url = url,
                    title = target.shownName,
                    userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                    isLive = true,
                ),
                debounce = false,
            )
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (!app.opentv.core.PipState.inPip.value) {
                        val targetId = currentId ?: channelId ?: (if (settings.lastChannelId > 0L) settings.lastChannelId else null)
                        val isPlayingOrBuffering = controller.player.currentMediaItem != null &&
                            (controller.player.playbackState == androidx.media3.common.Player.STATE_READY ||
                             controller.player.playbackState == androidx.media3.common.Player.STATE_BUFFERING)

                        if (targetId != null && targetId > 0L && !isPlayingOrBuffering) {
                            playChannelId(targetId)
                        } else if (!paused) {
                            controller.player.playWhenReady = true
                        }
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

    // The guide hands over the list you were browsing, but it is not the only way into the
    // player and that handoff is never cleared. When it is empty, channel up/down would
    // silently do nothing, so fall back to the channels of the source we are playing, in the
    // same sortIndex order the guide lists them in.
    LaunchedEffect(currentChannel?.sourceId) {
        if (queue.isNotEmpty()) return@LaunchedEffect
        val sourceId = currentChannel?.sourceId ?: return@LaunchedEffect
        val channels = withContext(Dispatchers.IO) {
            graph.database.channels().observe(sourceId, null).firstOrNull()
        }.orEmpty()
        if (channels.isNotEmpty()) {
            // `number` is the list position here, matching what the guide hands over — the
            // field feeds numeric entry and the on-screen channel number, so the two entry
            // paths have to fill it the same way.
            queue = channels.mapIndexed { index, ch ->
                PlaybackQueue.Item(ch.id, ch.shownName, ch.logoUrl, index + 1)
            }
        }
    }

    /**
     * Steps one channel along the list, the way a remote's CH+ / CH- does: [delta] of +1 moves
     * forward through the list and -1 back, so d-pad up is channel up and d-pad down is channel
     * down. Order is the list's own order — whatever order the channels are shown in — never a
     * channel number. Stops at either end rather than wrapping, so the first and last channel
     * say so by doing nothing.
     */
    fun zapBy(delta: Int) {
        if (queue.isEmpty()) return
        val cur = queue.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        val next = (cur + delta).coerceIn(0, queue.size - 1)
        if (next != cur) playChannelId(queue[next].id)
    }

    /**
     * Steps back through the stream and reports whether it took the seek.
     *
     * Live HLS with a DVR window — what Xtream live gives us — accepts this, which is how a live
     * channel can be rewound without changing stream. A stream with no window, such as plain
     * progressive TS, reports itself unseekable; the caller then falls back to what left used to
     * do rather than swallowing the press.
     *
     * The caller must NOT reveal the OSD: with the bar up, left/right belong to the history
     * carousel, so revealing would make the second press of a quick double-press scroll the list
     * instead of rewinding further.
     */
    fun seekBackBy(stepMillis: Long): Boolean {
        if (!controller.isSeekable) return false
        val cur = controller.player.currentPosition
        controller.player.seekTo((cur - stepMillis).coerceAtLeast(0L))
        return true
    }

    /**
     * Seeks by [deltaMillis] within a seekable stream, clamped to [0, duration]. Used by the
     * ±30-second channel-up/down scrub while an archive programme plays.
     */
    fun seekBy(deltaMillis: Long) {
        val p = controller.player
        val dur = p.duration
        val target = if (dur > 0L) (p.currentPosition + deltaMillis).coerceIn(0L, dur)
        else (p.currentPosition + deltaMillis).coerceAtLeast(0L)
        p.seekTo(target)
    }

    /**
     * Steps the viewer back [stepMillis], reporting whether the press was handled.
     *
     * Two ways back, tried in order:
     *
     *  1. Inside the stream already open — the whole story for VOD, for a catch-up item already
     *     playing, and for live HLS whose playlist reaches back far enough. See [seekBackBy].
     *  2. Through the provider's catch-up archive. A plain live stream sits at the live edge, so
     *     there is no window behind it to step into and the local seek is refused — but a catch-up
     *     channel can be asked for *any* past minute as its own timeshift stream. That is what makes
     *     "back 10 seconds" work on live TV, and it is what TiviMate does: the timeshift URL for the
     *     programme covering the target time, opened at the offset inside that programme. The player
     *     is not recreated — the same instance swaps media item the way a channel change does — and
     *     because a timeshift stream is a finished recording, the timeline becomes real, so the
     *     skip buttons and the timeline pip work properly from then on too.
     *
     * False means no attempt was made at all, so the caller can keep the key's old meaning. A
     * channel that looks catch-up capable but whose URL cannot be built reports through a toast
     * instead of falling back, because by then the press has already been claimed.
     */
    fun stepBack(stepMillis: Long): Boolean {
        if (seekBackBy(stepMillis)) return true

        val ch = currentChannel ?: return false
        val source = currentSource
        val now = System.currentTimeMillis()
        val target = now - stepMillis
        // Stepping past the provider's retention only earns a 404, so refuse locally rather than
        // spending a request on it.
        val archiveStart = if (ch.tvArchiveDays > 0) now - ch.tvArchiveDays * 86_400_000L else Long.MIN_VALUE
        if (target < archiveStart) return false
        // Trust the channel's own flags, its catch-up template, or a portal source — whose channels
        // routinely do catch-up without ever saying so in the playlist. This is the same rule the
        // guide's badge uses before offering a finished programme.
        val capable = CatchupResolver.isSupported(source, ch) ||
            (source != null && CatchupResolver.isSourceCapable(source))
        if (!capable) return false

        scope.launch {
            val prog = withContext(Dispatchers.IO) {
                val candidates = ch.epgCandidates.ifEmpty { listOfNotNull(ch.epgChannelId ?: ch.streamId) }
                // One millisecond wide, so this returns the single programme covering the target.
                graph.epgRepository.windowForChannels(candidates, target, target + 1L)
                    .values.firstOrNull()?.firstOrNull()
                    ?: currentProg?.takeIf { target in it.startUtcMillis until it.endUtcMillis }
            }
            val resolveSource = source ?: withContext(Dispatchers.IO) {
                graph.sourceRepository.byId(ch.sourceId)
            }
            val url = if (prog == null || resolveSource == null) null else {
                withContext(Dispatchers.IO) { CatchupResolver.resolve(resolveSource, ch, prog) }
            }
            if (prog == null || resolveSource == null || url == null) {
                Toast.makeText(context, "No catch-up for this channel", Toast.LENGTH_SHORT).show()
                return@launch
            }
            // The offset is what puts the viewer exactly [stepMillis] back instead of at the top of
            // the programme, which is what "Watch from start" (Replay) is for.
            controller.play(
                PlayerController.Request(
                    url = url,
                    title = ch.shownName,
                    userAgent = resolveSource.userAgent ?: "OpenTV/0.1 (Android)",
                    startPositionMillis = (target - prog.startUtcMillis).coerceAtLeast(0L),
                    isLive = false,
                ),
                debounce = false,
            )
            paused = false
            Toast.makeText(context, "Rewind ${stepMillis / 1000}s", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    /**
     * Back to the live edge.
     *
     * Usually that is just the end of the open stream. After [stepBack] has moved into the archive,
     * though, the player is holding a *catch-up* item whose end is the end of that programme rather
     * than now, so the live edge is a re-tune of the live URL, not a seek. The media item is swapped
     * on the same player, so it costs one connection — the same as changing channel.
     */
    fun goLive() {
        val ch = currentChannel
        val inArchive = controller.currentRequest?.isLive == false
        // Returning to the live edge also ends the archive session the guide tracks.
        if (activeCatchup != null) {
            activeCatchup = null
            onCatchupChange(null)
        }
        if (inArchive && ch != null) {
            scope.launch {
                val (source, url) = withContext(Dispatchers.IO) {
                    val src = graph.sourceRepository.byId(ch.sourceId)
                    src to graph.catalogRepository.resolvePlaybackUrl(ch, src)
                }
                controller.play(
                    PlayerController.Request(
                        url = url,
                        title = ch.shownName,
                        userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                        isLive = true,
                    ),
                    debounce = false,
                )
            }
        } else {
            controller.player.seekToDefaultPosition()
            controller.player.playWhenReady = true
        }
        paused = false
        Toast.makeText(context, "LIVE", Toast.LENGTH_SHORT).show()
        interaction++
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
        // While an archive programme plays, the shared player is already on the timeshift stream:
        // re-tuning the live channel here would yank the viewer back to now.
        if (activeCatchup == null) playChannelId(id)
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

    LaunchedEffect(currentId, nowMillis, activeCatchup) {
        val id = currentId ?: return@LaunchedEffect
        // Archive playback labels the OSD with the PROGRAMME BEING WATCHED, not whatever is on the
        // channel now — the guide's "now" lookup below would otherwise show the wrong title.
        val archive = activeCatchup
        if (archive != null) {
            withContext(Dispatchers.IO) {
                val channel = graph.catalogRepository.channel(archive.channelId)
                val source = channel?.let { graph.sourceRepository.byId(it.sourceId) }
                val catName = channel?.categoryId?.let { catId ->
                    graph.database.categories().namesFor(setOf(catId)).firstOrNull()?.name
                }
                withContext(Dispatchers.Main) {
                    currentChannel = channel
                    currentSource = source
                    currentCategoryName = catName
                    currentProg = Programme(
                        id = 0L,
                        feedId = 0L,
                        epgChannelId = channel?.epgChannelId ?: "",
                        title = archive.programmeTitle,
                        description = "",
                        startUtcMillis = archive.startUtcMillis,
                        endUtcMillis = archive.endUtcMillis,
                        category = null,
                    )
                    nextProg = null
                }
            }
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            val channel = graph.catalogRepository.channel(id)
            val source = channel?.let { graph.sourceRepository.byId(it.sourceId) }
            val catName = channel?.categoryId?.let { catId ->
                graph.database.categories().namesFor(setOf(catId)).firstOrNull()?.name
            }
            val candidates = channel?.epgCandidates.orEmpty()
            val up = candidates.firstNotNullOfOrNull { epgId ->
                graph.epgRepository.upcoming(epgId, nowMillis, limit = 4).takeIf { it.isNotEmpty() }
            }.orEmpty()
            val curr = up.firstOrNull { nowMillis in it.startUtcMillis until it.endUtcMillis } ?: up.firstOrNull()
            val next = if (curr != null) {
                up.firstOrNull { it.startUtcMillis >= curr.endUtcMillis }
            } else up.getOrNull(1)
            val (cProg, nProg) = curr to next

            withContext(Dispatchers.Main) {
                currentChannel = channel
                currentSource = source
                currentCategoryName = catName
                currentProg = cProg
                nextProg = nProg
            }
        }
    }

    LaunchedEffect(recentChannelRefs, currentId) {
        withContext(Dispatchers.IO) {
            // Bring the history across from the row ids older versions stored, once. Ids a sync has
            // already invalidated cannot be converted to refs and are dropped — they were lost
            // already, and there is nothing left to point at.
            val legacyIds = settings.legacyRecentChannelIds()
            if (legacyIds.isNotEmpty()) {
                val byId = graph.catalogRepository.channelsByIds(legacyIds).associateBy { it.id }
                settings.adoptRecentChannelRefs(
                    legacyIds.mapNotNull { id ->
                        byId[id]?.let { RecentChannelRef(it.sourceId, it.streamId) }
                    },
                )
            }

            // The channel playing now leads the bar even when it is not in the history yet: opened
            // straight from the guide, or the history was cleared while it played.
            val current = currentId?.let { graph.catalogRepository.channel(it) }
            val currentRef = current?.let { RecentChannelRef(it.sourceId, it.streamId) }
            val refs = (listOfNotNull(currentRef) + recentChannelRefs).distinct()
            val loaded = graph.catalogRepository.channelsForRefs(refs)

            // Only entries whose playlist has been deleted are retired. A channel the catalogue does
            // not hold this instant is not gone — the next sync brings it back, and a ref resolves
            // it there just as well because a ref is not the row id. Sweeping on absence, which is
            // what this used to do, is what emptied this bar of channels that were still there.
            val dead = RecentChannels.refsToForget(
                recentChannelRefs,
                graph.catalogRepository.existingSourceIds(),
            )
            if (dead.isNotEmpty()) settings.forgetRecentChannels(dead)

            withContext(Dispatchers.Main) {
                recentChannels = loaded
            }
        }
    }

    LaunchedEffect(recentChannels, nowMillis) {
        if (recentChannels.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val epgIds = recentChannels.mapNotNull { it.epgChannelId }.toSet()
            val nowProgs = graph.epgRepository.nowForChannels(epgIds, nowMillis)
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

    // Auto-hide the bar after a few seconds — but never while paused or with a picker open or scrubbing timeline.
    LaunchedEffect(controlsVisible, interaction, state, paused, panel, osdTier) {
        if (controlsVisible && !paused && panel == Panel.NONE && osdTier != OsdTier.TIMELINE &&
            state is PlayerController.State.Playing
        ) {
            delay(CONTROLS_TIMEOUT_MILLIS)
            controlsVisible = false
        }
    }

    // Focus: requests appropriate focus based on panel or osdTier
    LaunchedEffect(controlsVisible, panel, osdTier) {
        if (controlsVisible) {
            delay(16)
            runCatching {
                when {
                    panel != Panel.NONE -> panelFocus.requestFocus()
                    osdTier == OsdTier.TIMELINE -> timelineFocus.requestFocus()
                    osdTier == OsdTier.CONTROLS -> barFocus.requestFocus()
                    osdTier == OsdTier.SHORTCUTS -> {
                        subMenuFocusedIndex = 0
                        subMenuFocusRequesters[visibleSubMenuButtons.firstOrNull()]?.requestFocus()
                    }
                    else -> historyFocus.requestFocus()
                }
            }
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    // Pressing the Back button steps through tiers or closes OSD
    BackHandler {
        when {
            panel != Panel.NONE -> panel = Panel.NONE
            channelListVisible -> channelListVisible = false
            controlsVisible -> {
                when (osdTier) {
                    OsdTier.TIMELINE -> {
                        osdTier = OsdTier.CONTROLS
                        scope.launch { delay(16); runCatching { barFocus.requestFocus() } }
                    }
                    OsdTier.CONTROLS -> {
                        osdTier = OsdTier.HISTORY
                        scope.launch { delay(16); runCatching { historyFocus.requestFocus() } }
                    }
                    OsdTier.SHORTCUTS -> {
                        osdTier = OsdTier.HISTORY
                        scope.launch { delay(16); runCatching { historyFocus.requestFocus() } }
                    }
                    OsdTier.HISTORY -> {
                        controlsVisible = false
                    }
                }
            }
            else -> onBack()
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
            .background(Color.Transparent)
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
                        when (osdTier) {
                            OsdTier.TIMELINE -> {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        osdTier = OsdTier.CONTROLS
                                        scope.launch { delay(16); runCatching { barFocus.requestFocus() } }
                                        true
                                    }
                                    Key.DirectionLeft -> {
                                        // Same two-stage step as the D-pad-left handler below, so
                                        // rewinding with the timeline focused works on live TV too
                                        // rather than only where the player already has a window.
                                        stepBack(scrubStepMillis(event.nativeKeyEvent.repeatCount))
                                        interaction++
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        val cur = controller.player.currentPosition
                                        val step = scrubStepMillis(event.nativeKeyEvent.repeatCount)
                                        val dur = controller.player.duration
                                        val target = if (dur > 0) (cur + step).coerceIn(0L, dur) else cur + step
                                        controller.player.seekTo(target)
                                        interaction++
                                        true
                                    }
                                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                        val targetPlaying = paused
                                        controller.player.playWhenReady = targetPlaying
                                        paused = !targetPlaying
                                        true
                                    }
                                    else -> false
                                }
                            }
                            OsdTier.CONTROLS -> {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        osdTier = OsdTier.TIMELINE
                                        scope.launch { delay(16); runCatching { timelineFocus.requestFocus() } }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        osdTier = OsdTier.HISTORY
                                        scope.launch { delay(16); runCatching { historyFocus.requestFocus() } }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            OsdTier.HISTORY -> {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        osdTier = OsdTier.CONTROLS
                                        scope.launch { delay(16); runCatching { barFocus.requestFocus() } }
                                        true
                                    }
                                    Key.DirectionDown -> {
                                        osdTier = OsdTier.SHORTCUTS
                                        subMenuFocusedIndex = 0
                                        scope.launch {
                                            delay(16)
                                            runCatching {
                                                subMenuFocusRequesters[visibleSubMenuButtons.firstOrNull()]?.requestFocus()
                                            }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                            OsdTier.SHORTCUTS -> {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        osdTier = OsdTier.HISTORY
                                        scope.launch { delay(16); runCatching { historyFocus.requestFocus() } }
                                        true
                                    }
                                    // Left/Right step by index and wrap, so the row loops instead of
                                    // dead-ending at either edge.
                                    Key.DirectionLeft -> {
                                        moveSubMenuFocus(false)
                                        true
                                    }
                                    Key.DirectionRight -> {
                                        moveSubMenuFocus(true)
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }
                    }
                    // While an ARCHIVE programme plays, channel up/down scrub the recording ±30s
                    // instead of zapping — there is no "next channel" while watching a finished show.
                    activeCatchup != null && (
                        event.key == Key.ChannelUp ||
                        event.key == Key.PageUp ||
                        event.nativeKeyEvent.keyCode == 166 || // KEYCODE_CHANNEL_UP
                        event.nativeKeyEvent.keyCode == 92 ||  // KEYCODE_PAGE_UP
                        event.key == Key.DirectionUp
                    ) -> { seekBy(30_000L); true }

                    activeCatchup != null && (
                        event.key == Key.ChannelDown ||
                        event.key == Key.PageDown ||
                        event.nativeKeyEvent.keyCode == 167 || // KEYCODE_CHANNEL_DOWN
                        event.nativeKeyEvent.keyCode == 93 ||  // KEYCODE_PAGE_DOWN
                        event.key == Key.DirectionDown
                    ) -> { seekBy(-30_000L); true }

                    // D-Pad Up, or a dedicated Channel Up / Page Up (ONN 4k box remote), while
                    // full-screen: the next channel up the list — the next channel number.
                    //
                    // Deliberately does not reveal the OSD. It used to, and on the ONN remote that
                    // made channel up/down behave like a menu key: every press zapped the channel
                    // *and* threw the history bar and the shortcut row up over the picture, so the
                    // key read as "open the bar" rather than "next channel". A zap is a channel
                    // change; the bar is still on OK.
                    event.key == Key.ChannelUp ||
                    event.key == Key.PageUp ||
                    event.nativeKeyEvent.keyCode == 166 || // KEYCODE_CHANNEL_UP
                    event.nativeKeyEvent.keyCode == 92 ||  // KEYCODE_PAGE_UP
                    event.key == Key.DirectionUp
                    -> { zapBy(1); true }

                    // D-Pad Down, or a dedicated Channel Down / Page Down: the channel before it.
                    // Same reasoning as above — no reveal.
                    event.key == Key.ChannelDown ||
                    event.key == Key.PageDown ||
                    event.nativeKeyEvent.keyCode == 167 || // KEYCODE_CHANNEL_DOWN
                    event.nativeKeyEvent.keyCode == 93 ||  // KEYCODE_PAGE_DOWN
                    event.key == Key.DirectionDown
                    -> { zapBy(-1); true }

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

                    // D-pad left while watching: back through the stream, TiviMate-style. The
                    // step is 10s a press and scales with the key repeat, so holding backs up
                    // further. With the bar showing, left/right belong to the history carousel
                    // instead — that branch is handled above this one.
                    event.key == Key.DirectionLeft -> {
                        // stepBack covers both cases: the DVR/playlist window when the stream has one,
                        // and the provider's catch-up archive when it does not — which is the norm for
                        // live TV. Only when neither is available does left keep its old meaning of
                        // opening the channel list.
                        if (!stepBack(scrubStepMillis(event.nativeKeyEvent.repeatCount))) {
                            if (queue.isNotEmpty()) channelListVisible = true
                        }
                        true
                    }

                    // The Guide key — and the ONN box's dedicated key — still open the channel
                    // list, which is where left used to lead on every stream.
                    event.key == Key.Guide ||
                    event.nativeKeyEvent.keyCode == 172 // KEYCODE_GUIDE
                    -> { if (queue.isNotEmpty()) channelListVisible = true; true }

                    // In archive, Right is the matching forward skip to Left's stepBack (10s a press,
                    // scaling with key repeat), not "previous channel".
                    activeCatchup != null && event.key == Key.DirectionRight -> {
                        seekBy(scrubStepMillis(event.nativeKeyEvent.repeatCount))
                        true
                    }

                    event.key == Key.DirectionRight -> {
                        // The channel watched before this one. previousId only knows about swaps
                        // made inside this visit, so fall back to the one recorded across player
                        // instances before reaching for the watch history.
                        val targetId = previousId
                            ?: PlaybackQueue.previousChannelId.takeIf { it > 0L && it != currentId }
                            // Resolved channels only. This used to fall back to a raw stored id when
                            // the bar had not resolved yet — the very kind of id that goes stale when
                            // a sync rebuilds a row, so the fallback could only fail when it mattered
                            // (see core/RecentChannels.kt).
                            ?: recentChannels.firstOrNull { it.id != currentId }?.id
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
                onRelease = { pv ->
                    pv.player = null
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
                                    text = if (activeCatchup != null) "  —  Catch-up  "
                                    else "  —  $remainingMins min   ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            val chNum = queue.firstOrNull { it.id == currentChannel?.id }?.number ?: currentChannel?.number
                            val numStr = chNum?.let { "$it " } ?: ""
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

                // ---- Yellow / Gold Timeline Progress Bar (TiviMate Style) ----
                // The pip tracks the player's REAL position, so every skip is visible instantly.
                // Falls back to the programme wall-clock fraction when the stream reports no
                // window length.
                val progFraction = currentProg?.progressAt(nowMillis) ?: 0f
                val playerDur = controller.player.duration
                val barFraction = if (playerDur > 0) {
                    (controller.player.currentPosition.toFloat() / playerDur.toFloat()).coerceIn(0f, 1f)
                } else progFraction
                LiveTimelineBar(
                    progress = barFraction,
                    isFocused = osdTier == OsdTier.TIMELINE,
                    focusRequester = timelineFocus,
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- Player Buttons Row (Tier 2 / Tier 3: Up from History) ----
                AnimatedVisibility(
                    visible = osdTier == OsdTier.CONTROLS || osdTier == OsdTier.TIMELINE,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    val isRecording = activeRecordings.any { it.channelId == currentId }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        // Left: position / length. Player scale when the stream reports a window
                        // (moves with every skip); programme wall-clock as the fallback.
                        val prog = currentProg
                        val durationMs = when {
                            playerDur > 0L -> playerDur
                            prog != null && prog.durationMillis > 0L -> prog.durationMillis
                            else -> 0L
                        }
                        val elapsedMs = when {
                            playerDur > 0L -> controller.player.currentPosition.coerceIn(0L, playerDur)
                            prog != null && prog.durationMillis > 0L ->
                                (nowMillis - prog.startUtcMillis).coerceIn(0L, durationMs)
                            else -> controller.player.currentPosition.coerceAtLeast(0L)
                        }
                        Text(
                            text = "${formatDurationMs(elapsedMs)} / ${formatDurationMs(durationMs)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 2.dp),
                        )

                        // Center: 5 transport buttons
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Skip Previous (|◀) - Jump to start
                            TransportButton(
                                icon = Icons.Filled.SkipPrevious,
                                contentDescription = stringResource(R.string.player_rewind),
                                size = 38.dp,
                                iconSize = 20.dp,
                                onClick = {
                                    controller.player.seekTo(0L)
                                    interaction++
                                },
                            )

                            // Fast Rewind (◀◀) - 10s
                            TransportButton(
                                icon = Icons.Filled.FastRewind,
                                contentDescription = stringResource(R.string.player_rewind),
                                size = 38.dp,
                                iconSize = 20.dp,
                                onClick = {
                                    // stepBack, not a blind seek: on a live stream there is often
                                    // nothing behind the playhead to seek into, and stepBack is what
                                    // reaches into the provider's archive in that case. See it for
                                    // the two-stage rule.
                                    stepBack(10_000L)
                                    interaction++
                                },
                            )

                            // Play / Pause (|| / ▶) - 46dp solid white circle with dark icon (focused by default in Tier 2)
                            TransportButton(
                                icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = if (paused) stringResource(R.string.player_play) else stringResource(R.string.player_pause),
                                size = 46.dp,
                                iconSize = 24.dp,
                                isPrimary = true,
                                focusRequester = barFocus,
                                onClick = {
                                    val targetPlaying = paused
                                    controller.player.playWhenReady = targetPlaying
                                    paused = !targetPlaying
                                    interaction++
                                },
                            )

                            // Fast Forward (▶▶) + 10s
                            TransportButton(
                                icon = Icons.Filled.FastForward,
                                contentDescription = stringResource(R.string.player_forward),
                                size = 38.dp,
                                iconSize = 20.dp,
                                onClick = {
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

                            // Skip Next (▶|) - Jump to live edge
                            TransportButton(
                                icon = Icons.Filled.SkipNext,
                                contentDescription = stringResource(R.string.player_forward),
                                size = 38.dp,
                                iconSize = 20.dp,
                                onClick = {
                                    // goLive, not a bare seekToDefaultPosition: after a rewind the
                                    // open item is a catch-up stream, and its end is the end of that
                                    // programme, not now.
                                    goLive()
                                },
                            )
                        }

                        // Right: ↺ Watch from start + [LIVE] + ● Record
                        Row(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Watch from start button (↺) — hidden in archive, where the viewer is
                            // already watching a finished programme from its start.
                            if (activeCatchup == null) {
                                TransportButton(
                                    icon = Icons.Filled.Replay,
                                    contentDescription = "Watch from start",
                                    size = 38.dp,
                                    iconSize = 20.dp,
                                    onClick = {
                                        watchFromStart()
                                        interaction++
                                    },
                                )
                            }

                            // LIVE Badge Button
                            LiveBadgeButton(
                                onClick = {
                                    goLive()
                                },
                            )

                            // Record Button
                            TransportButton(
                                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                contentDescription = if (isRecording) stringResource(R.string.rec_stop_recording) else stringResource(R.string.player_record),
                                size = 38.dp,
                                iconSize = 20.dp,
                                iconTint = if (isRecording) AppTheme.palette.recording else Color.White,
                                onClick = {
                                    toggleRecord()
                                    interaction++
                                },
                            )
                        }
                    }
                }

                // ---- Watched Channels History Carousel (Tier 1: Default on OK) ----
                AnimatedVisibility(
                    visible = osdTier != OsdTier.SHORTCUTS,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .alpha(if (osdTier == OsdTier.HISTORY) 1f else 0.42f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(if (osdTier == OsdTier.HISTORY) 8.dp else 4.dp))
                        val quickListState = rememberLazyListState()
                        LazyRow(
                            state = quickListState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            // Card 1: TV guide
                            item(key = "quick-guide") {
                                QuickActionCard(
                                    icon = Icons.Filled.ViewStream,
                                    label = stringResource(R.string.player_tv_guide),
                                    focusRequester = historyFocus,
                                    onClick = {
                                        controlsVisible = false
                                        onBack()
                                    },
                                )
                            }

                            // Cards 2+: Watched Channels History (newest first). The "History" card
                            // that used to open with this one jumped to the previous channel; the
                            // carousel already lists every channel in that history, and left/right
                            // on the remote still tunes to the previous one.
                            itemsIndexed(recentChannels, key = { _, ch -> "recent-ch-${ch.id}" }) { _, ch ->
                                QuickChannelCard(
                                    channel = ch,
                                    programme = queueProgrammes[ch.id],
                                    isCurrent = ch.id == currentId,
                                    onClick = { playChannelId(ch.id) },
                                    onLongClick = {
                                        settings.forgetRecentChannels(
                                            listOf(RecentChannelRef(ch.sourceId, ch.streamId)),
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.history_removed, ch.shownName),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                )
                            }

                            // Card End: Clear History Button
                            if (recentChannels.isNotEmpty()) {
                                item(key = "quick-clear-history") {
                                    QuickActionCard(
                                        icon = Icons.Filled.Delete,
                                        label = stringResource(R.string.history_clear),
                                        onClick = {
                                            settings.clearRecentChannels()
                                            Toast.makeText(context, context.getString(R.string.history_cleared), Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                }
                            }
                        }

                        // Subtle down chevron indicator (v) when in Tier 1
                        if (osdTier == OsdTier.HISTORY) {
                            Spacer(Modifier.height(2.dp))
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Shortcuts below",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                // ---- Shortcuts Row (Tier 0: Down from History) ----
                AnimatedVisibility(
                    visible = osdTier == OsdTier.SHORTCUTS,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    LazyRow(
                        state = subMenuListState,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        visibleSubMenuButtons.forEachIndexed { index, btn ->
                            item(key = "sub-btn-${btn.key}") {
                                when (btn) {
                                        AppSettings.SubMenuButton.SEARCH -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Search,
                                                label = stringResource(R.string.submenu_search),
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                iconTint = if (recordingThis) AppTheme.palette.recording else null,
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
                                                onClick = { toggleRecord() },
                                            )
                                        }
                                        AppSettings.SubMenuButton.MULTIVIEW -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.GridView,
                                                label = stringResource(R.string.submenu_multiview),
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
                                                onClick = { showMultiviewDialog = true },
                                            )
                                        }
                                        AppSettings.SubMenuButton.QUALITY -> {
                                            val qualLabel = if (videoSizeText.isNotEmpty()) videoSizeText.replace("x", " × ") else "Quality"
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Videocam,
                                                label = qualLabel,
                                                isSelected = panel == Panel.QUALITY,
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
                                                onClick = {
                                                    panel = if (panel == Panel.QUALITY) Panel.NONE else Panel.QUALITY
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.AUDIO -> {
                                            SubMenuButtonCard(
                                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                                label = selectedAudioLabel,
                                                isSelected = panel == Panel.AUDIO,
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
                                                onClick = {
                                                    panel = if (panel == Panel.ASPECT) Panel.NONE else Panel.ASPECT
                                                    interaction++
                                                },
                                            )
                                        }
                                        AppSettings.SubMenuButton.CHANNELS_LIST -> {
                                            SubMenuButtonCard(
                                                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                                label = stringResource(R.string.submenu_channels_list),
                                                isSelected = channelListVisible,
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                iconTint = if (isFav) AppTheme.palette.favourite else null,
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
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
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
                                                onClick = { showChannelOptionsDialog = true },
                                            )
                                        }
                                        AppSettings.SubMenuButton.SETTINGS -> {
                                            SubMenuButtonCard(
                                                icon = Icons.Filled.Settings,
                                                label = stringResource(R.string.submenu_settings),
                                                focusRequester = subMenuFocusRequesters.getOrPut(btn) { FocusRequester() },
                                                onFocusChanged = { if (it) subMenuFocusedIndex = index },
                                                onClick = {
                                                    controlsVisible = false
                                                    onOpenSettings()
                                                },
                                            )
                                        }
                                        else -> Unit
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
                    Text(stringResource(R.string.common_done), color = AppTheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    Text(stringResource(R.string.common_done), color = AppTheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    val fg = AppTheme.palette.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .tvFocus(
                shape = RoundedCornerShape(0.dp),
                selected = playing,
                onFocusChange = { focused = it },
            )
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
    val shape = RoundedCornerShape(10.dp)
    val fg = AppTheme.palette.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(AppTheme.palette.onSurface.copy(alpha = 0.08f))
            .tvFocus(shape = shape, selected = selected, onFocusChange = { focused = it })
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = fg, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_selected), tint = AppTheme.primary)
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

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .width(108.dp)
            .height(76.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(AppTheme.palette.selectedSurface.copy(alpha = 0.85f))
            .tvFocus(shape = shape, onFocusChange = { focused = it; onFocusChanged(it) })
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
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color = Color.White,
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
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // Set while OK is held, so the release that follows a long press is swallowed instead of
    // being passed on as a click too.
    var longPressed by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(136.dp)
            .height(76.dp)
            .clip(shape)
            .background(
                if (isCurrent) AppTheme.palette.selectedSurface
                else AppTheme.palette.chromeCell.copy(alpha = 0.90f),
            )
            .tvFocus(
                shape = shape,
                selected = isCurrent,
                onFocusChange = { focused = it; onFocusChanged(it) },
            )
            .focusable()
            // Holding OK forgets the channel. A remote auto-repeats a held d-pad centre, so the
            // first repeat is the long press; the release afterwards is swallowed rather than
            // passed on, so a hold does not also fire the short-press click.
            .onPreviewKeyEvent { e ->
                val forget = onLongClick
                val isCenter = e.key == Key.DirectionCenter ||
                    e.key == Key.Enter ||
                    e.key == Key.NumPadEnter
                when {
                    forget == null || !isCenter -> false
                    e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 -> {
                        longPressed = true
                        forget()
                        true
                    }
                    e.type == KeyEventType.KeyUp && longPressed -> {
                        longPressed = false
                        true
                    }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
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
                    .size(28.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = channel.shownName,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = programme?.title ?: "",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = if (focused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.65f),
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
    isFocused: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val widthDp = remember(widthPx) { with(density) { widthPx.toDp() } }
    val safeProgress = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // Key handling lives in PlayerScreen's root onPreviewKeyEvent (TIMELINE tier): the
            // root preview always consumes Left/Right/Enter here first, so a handler on the bar
            // itself would be dead code. Always focusable — the tier effect focuses it on entry,
            // and a bar that is only focusable-when-focused could never take focus at all.
            .focusable()
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)),
        )

        Box(
            Modifier
                .fillMaxWidth(safeProgress)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppTheme.primary),
        )

        if (widthDp > 0.dp) {
            val pipSize = if (isFocused) 18.dp else 10.dp
            val dotOffset = ((widthDp - pipSize) * safeProgress).coerceAtLeast(0.dp)
            Box(
                Modifier
                    .padding(start = dotOffset)
                    .size(pipSize)
                    .clip(CircleShape)
                    .background(AppTheme.palette.onSurface)
                    .then(
                        if (isFocused) Modifier.border(2.5.dp, AppTheme.palette.cursorBorder, CircleShape)
                        else Modifier
                    ),
            )
        }
    }
}

/** TiviMate-style accelerating seek step: taps move 10s; holding ramps 10s → 20s → 30s → 60s. */
private fun scrubStepMillis(repeatCount: Int): Long = when {
    repeatCount >= 8 -> 60_000L
    repeatCount >= 4 -> 30_000L
    repeatCount >= 2 -> 20_000L
    else -> 10_000L
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
    val shape = RoundedCornerShape(6.dp)
    val contentColor = AppTheme.palette.onSurface

    Box(
        modifier = Modifier
            .height(28.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(AppTheme.palette.chipSurface.copy(alpha = 0.65f))
            .tvFocus(shape = shape, onFocusChange = { focused = it; onFocusChanged(it) })
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
        isPrimary -> AppTheme.primary
        focused -> AppTheme.palette.cursorFill
        else -> AppTheme.palette.chipSurface.copy(alpha = 0.65f)
    }
    val icTint = when {
        isPrimary -> AppTheme.palette.onFocusSurface
        iconTint != null -> iconTint
        else -> AppTheme.palette.onSurface
    }
    val ring = when {
        focused && isPrimary -> AppTheme.palette.onFocusSurface
        focused -> AppTheme.palette.cursorBorder
        isPrimary -> AppTheme.palette.cursorBorder
        else -> AppTheme.palette.outlineVariant
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
            .border(if (focused) 2.5.dp else 1.dp, ring, CircleShape)
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
    val shape = RoundedCornerShape(12.dp)
    val content = AppTheme.palette.onSurface

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(shape)
            .background(AppTheme.palette.chipSurface.copy(alpha = 0.6f))
            .tvFocus(
                shape = shape,
                selected = selected,
                onFocusChange = { focused = it },
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
    val icTint = when {
        iconTint != null -> iconTint
        isSelected -> AppTheme.primary
        else -> AppTheme.palette.onSurface
    }
    val textColor = when {
        isSelected -> AppTheme.primary
        else -> AppTheme.palette.onSurface.copy(alpha = 0.85f)
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
                .background(AppTheme.palette.chrome)
                .tvFocus(shape = CircleShape, selected = isSelected),
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
