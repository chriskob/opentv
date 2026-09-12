/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import app.opentv.ui.theme.AppTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.DisplayRefresh
import app.opentv.core.ServiceLocator
import app.opentv.core.SleepTimer
import app.opentv.core.findActivity
import app.opentv.data.model.Episode
import app.opentv.player.PlayerController
import app.opentv.player.StreamInfo
import app.opentv.player.declaredQuality
import app.opentv.player.streamInfoOf
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a movie or episode: a single non-live stream, with resume and a proper transport bar.
 *
 * The bar is drawn in Compose rather than left to the Media3 view's own controller, because that
 * controller needs the embedded view to hold d-pad focus to be summoned — which it doesn't, on a
 * TV inside Compose, so it reads as "no controls". This is the same self-drawn, auto-hiding bar as
 * the live player, with a seek bar added since a film needs scrubbing.
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    mediaKey: String,
    streamUrl: String,
    title: String,
    userAgent: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    // The playing item lives in state rather than being read straight off the route arguments,
    // because "next episode" swaps it in place: the screen stays composed and simply plays the next
    // URL, which is what makes the hand-off instant.
    var item by remember { mutableStateOf(VodItem(mediaKey, streamUrl, title)) }

    // A recording that's still being written plays through the tail-following source. It behaves a
    // little differently in the transport: the "length" is how much has been recorded so far, which
    // keeps growing, and seeking is bounded to that recorded extent.
    val growingRec = remember(item.url) { item.url.startsWith("optvrec://") }
    // The SMB source lets a recording stored on a NAS play and seek in-app; harmless for the
    // http/file URLs of ordinary VOD.
    val controller = remember {
        PlayerController(
            context, scope, graph.streamingHttpClient, subtitlesEnabled = false,
            smbDataSourceFactory = app.opentv.player.SmbDataSource.Factory(graph.settings),
            // Lets an `optvrec://<id>` recording play while it's still being written.
            growingDataSourceFactory =
                app.opentv.player.GrowingRecordingDataSource.Factory(context.applicationContext),
            liveRecording = growingRec,
        )
    }
    // Skip forward/back within the recorded portion. For a growing recording ExoPlayer won't report
    // the item as seekable (no fixed length), so we seek directly, clamped to what's on disk.
    fun seekRelative(deltaMs: Long) {
        val p = controller.player
        val ceiling = if (growingRec) p.bufferedPosition.coerceAtLeast(0L)
        else (p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, ceiling))
    }
    val state by controller.state.collectAsState()
    val tracks by controller.tracks.collectAsState()

    var paused by remember { mutableStateOf(false) }
    var vodPanel by remember { mutableStateOf(VodPanel.NONE) }
    val panelFocus = remember { FocusRequester() }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    // Which buttons this screen shows, and a focus target per button so the bar can hand focus to
    // whatever is actually composed — the set is user-configurable, so it is not knowable here.
    val enabledButtons by settings.enabledVodButtons.collectAsState()
    val buttonFocus = remember {
        AppSettings.VodPlayerButton.entries.associateWith { FocusRequester() }
    }

    // What the decoder is actually receiving — see StreamInfo. Empty until the renderers report in.
    var streamInfo by remember { mutableStateOf(StreamInfo.UNKNOWN) }
    var subtitleLabel by remember { mutableStateOf("") }

    // Header metadata. Looked up from the media key rather than passed through the route, so every
    // entry point (movie, episode, catch-up, add-on stream) gets the same header for free.
    var posterUrl by remember { mutableStateOf<String?>(null) }
    var metaLine by remember { mutableStateOf("") }
    var declaredLabel by remember { mutableStateOf<String?>(null) }

    // The queue behind the next-episode button, and the countdown once an episode finishes.
    var currentEpisode by remember { mutableStateOf<Episode?>(null) }
    var episodeQueue by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var upNextVisible by remember { mutableStateOf(false) }
    var upNextSeconds by remember { mutableIntStateOf(UP_NEXT_SECONDS) }

    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var aspectMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // The next episode in broadcast order, or null for a film, a catch-up stream, or the last
    // episode of a series. Read by the end-of-item listener, which is why it is wrapped below.
    val nextEpisode: Episode? = remember(episodeQueue, currentEpisode) {
        val cur = currentEpisode ?: return@remember null
        val ordered = episodeQueue.sortedWith(compareBy({ it.season }, { it.episodeNumber }))
        val index = ordered.indexOfFirst { it.id == cur.id }
        if (index < 0) null else ordered.getOrNull(index + 1)
    }
    val latestNext = rememberUpdatedState(nextEpisode)

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val timelineFocus = remember { FocusRequester() }
    val rootFocus = remember { FocusRequester() }

    fun reveal() { controlsVisible = true; interaction++ }

    suspend fun savePosition() {
        val player = controller.player
        val pos = player.currentPosition
        val dur = player.duration.takeIf { it > 0 } ?: return
        if (pos > 5_000) {
            graph.playbackPositions.upsert(
                app.opentv.data.model.PlaybackPosition(
                    profileId = settings.activeProfileId.value,
                    mediaKey = item.mediaKey,
                    positionMillis = pos,
                    durationMillis = dur,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * Plays another episode of the same series in place.
     *
     * The item is state, so this is just an assignment: the screen stays composed, the position of
     * the episode being left is saved first, and the playback effect (keyed on the media key) does
     * the actual handing over.
     */
    fun playEpisode(ep: Episode) {
        scope.launch {
            savePosition()
            item = VodItem(
                mediaKey = "$EPISODE_KEY_PREFIX${ep.id}",
                url = ep.streamUrl,
                title = "S${ep.season}E${ep.episodeNumber} · ${ep.title}",
            )
            paused = false
            vodPanel = VodPanel.NONE
            upNextVisible = false
            reveal()
        }
    }

    /** Jump to the next episode, from the up-next card or the next-episode button. */
    fun playNext() {
        nextEpisode?.let { playEpisode(it) }
    }

    /**
     * Hands focus to the transport bar.
     *
     * Which button that is cannot be hard-coded: the set is user-configurable, so the play/pause
     * requester may not be attached to anything. Falls back to the first composed button, and does
     * nothing at all if the user has turned every button off.
     */
    fun focusBar() {
        val target = AppSettings.VodPlayerButton.PLAY_PAUSE
            .takeIf { it in enabledButtons }
            ?.let { buttonFocus[it] }
            ?: AppSettings.VodPlayerButton.entries
                .firstOrNull { it in enabledButtons }
                ?.let { buttonFocus[it] }
        runCatching { target?.requestFocus() }
    }

    // Keep the screen awake during playback — see the note in PlayerScreen; a film is exactly when
    // the screensaver must not fire. Window flag is the reliable path; keepScreenOn is a backstop.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
            scope.launch { savePosition() }
            controller.release()
            scope.cancel()
        }
    }

    // Match the display to the film or episode. After live sport this is where a wrong mode shows
    // most: 23.976 fps on a 60 Hz panel is 3:2 pulldown, and a mode the rate divides exactly is the
    // difference between a film and a film that steps slightly every so often.
    DisposableEffect(controller.player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                streamInfo = streamInfoOf(controller.player.currentTracks, videoSize)
            }

            override fun onTracksChanged(tracks: Tracks) {
                // The format readout: read off the tracks the player actually selected, never off the
                // title, so "4K" in a provider's metadata cannot dress up a 720p stream.
                streamInfo = streamInfoOf(tracks, controller.player.videoSize)
                subtitleLabel = selectedTextLabel(tracks)

                if (!settings.matchRefreshRate.value) return
                val group = tracks.groups.firstOrNull {
                    it.type == C.TRACK_TYPE_VIDEO && it.isSelected
                } ?: return
                val format = (0 until group.length)
                    .firstOrNull { group.isTrackSelected(it) }
                    ?.let { group.getTrackFormat(it) }
                    ?: return
                if (format.frameRate <= 0f) return
                context.findActivity()?.let { DisplayRefresh.match(it, format.frameRate) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // A finished episode offers the next one rather than dropping to a black frame.
                if (playbackState == Player.STATE_ENDED && latestNext.value != null) {
                    upNextSeconds = UP_NEXT_SECONDS
                    upNextVisible = true
                }
            }
        }
        // Seed from what the player already knows, so re-entering a title shows its format at once
        // rather than a beat after the first frame arrives.
        streamInfo = streamInfoOf(controller.player.currentTracks, controller.player.videoSize)
        subtitleLabel = selectedTextLabel(controller.player.currentTracks)
        controller.player.addListener(listener)
        onDispose {
            controller.player.removeListener(listener)
            // Hand the display back to the system rather than leaving the box on the film's mode.
            context.findActivity()?.let { DisplayRefresh.restore(it) }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    if (!app.opentv.core.PipState.inPip.value) {
                        controller.player.pause()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    if (!app.opentv.core.PipState.inPip.value && !paused) {
                        controller.player.play()
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

    // Header metadata, and — for an episode — the queue its next-episode button walks. Both come
    // from the local catalogue, so a movie plays with its poster and an episode with its still,
    // without threading extra arguments through the navigation graph.
    LaunchedEffect(item.mediaKey) {
        currentEpisode = null
        episodeQueue = emptyList()
        posterUrl = null
        metaLine = ""
        declaredLabel = declaredQuality(item.title)

        val episodeId = item.mediaKey.episodeRowId()
        val movieId = item.mediaKey.movieRowId()
        if (episodeId != null) {
            val ep = graph.catalogRepository.episode(episodeId) ?: return@LaunchedEffect
            currentEpisode = ep
            posterUrl = ep.stillUrl
            declaredQuality(ep.title)?.let { declaredLabel = it }
            metaLine = listOfNotNull(
                context.getString(R.string.vod_season, ep.season),
                context.getString(R.string.vod_episode, ep.episodeNumber),
                ep.durationSeconds?.takeIf { it > 0 }?.let { formatRuntime(it) },
            ).joinToString(META_SEPARATOR)

            var list = graph.catalogRepository.observeEpisodes(ep.sourceId, ep.seriesId).first()
            if (list.isEmpty()) {
                // Reached without passing through the series screen — the detail screen is what
                // normally loads these, so load them here before the next-episode button needs them.
                graph.sourceRepository.byId(ep.sourceId)?.let { source ->
                    graph.catalogRepository.ensureEpisodes(source, ep.seriesId)
                    list = graph.catalogRepository.observeEpisodes(ep.sourceId, ep.seriesId).first()
                }
            }
            episodeQueue = list
        } else if (movieId != null) {
            val movie = graph.catalogRepository.movie(movieId) ?: return@LaunchedEffect
            posterUrl = movie.posterUrl
            declaredQuality(movie.name)?.let { declaredLabel = it }
            metaLine = listOfNotNull(
                movie.year?.takeIf { it > 0 }?.toString(),
                movie.genre?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() },
                movie.durationSeconds?.takeIf { it > 0 }?.let { formatRuntime(it) },
            ).joinToString(META_SEPARATOR)
        }
    }

    LaunchedEffect(item.mediaKey) {
        // Ensure background Live TV player is completely stopped while catch-up/VOD plays
        graph.livePlayer.player.pause()
        graph.livePlayer.player.stop()

        // Nothing is known about the new item yet, and the previous item's readout would be wrong
        // for it — clear rather than show a stale resolution.
        streamInfo = StreamInfo.UNKNOWN
        subtitleLabel = ""

        val resumeFrom = graph.playbackPositions.get(settings.activeProfileId.value, item.mediaKey)
            ?.takeIf { !it.isFinished }?.positionMillis ?: 0L
        controller.play(
            PlayerController.Request(
                url = item.url,
                title = item.title,
                userAgent = userAgent,
                startPositionMillis = resumeFrom,
                isLive = false,
            ),
            debounce = false,
        )
        while (isActive) {
            delay(15_000)
            savePosition()
        }
    }

    // Up-next countdown: an episode that ends hands over on its own, unless the viewer intervenes.
    // Keyed on visibility so it starts fresh the moment the card appears.
    LaunchedEffect(upNextVisible) {
        if (!upNextVisible) return@LaunchedEffect
        upNextSeconds = UP_NEXT_SECONDS
        while (isActive && upNextSeconds > 0) {
            delay(1_000)
            upNextSeconds--
        }
        if (upNextSeconds <= 0) {
            playNext()
            upNextVisible = false
        }
    }

    // Sleep timer: leave the film when the armed deadline passes (position is saved on dispose).
    val sleepDeadline by SleepTimer.deadline.collectAsState()
    LaunchedEffect(sleepDeadline) {
        val d = sleepDeadline ?: return@LaunchedEffect
        val wait = d - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        SleepTimer.clear()
        onBack()
    }

    // Poll position/duration for the seek bar while the film plays.
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!scrubbing) {
                positionMs = controller.player.currentPosition.coerceAtLeast(0)
                // A growing recording has no fixed length; the recorded-so-far extent (how far you
                // can skip ahead) is what's been read into the buffer.
                durationMs = if (growingRec) controller.player.bufferedPosition.coerceAtLeast(0)
                else controller.player.duration.takeIf { it > 0 } ?: 0
            }
            delay(500)
        }
    }

    // Auto-hide when playing and not paused/scrubbing and no picker open. A growing recording dips in
    // and out of Buffering as it rides the write head, so for that case Buffering counts as "playing"
    // here — otherwise a single hiccup would pin the transport bar on screen for the rest of the watch.
    LaunchedEffect(controlsVisible, interaction, state, paused, scrubbing, vodPanel) {
        val activelyPlaying = state is PlayerController.State.Playing ||
            (growingRec && state is PlayerController.State.Buffering)
        if (controlsVisible && !paused && !scrubbing && vodPanel == VodPanel.NONE && activelyPlaying) {
            delay(5_000)
            controlsVisible = false
        }
    }

    LaunchedEffect(controlsVisible, vodPanel) {
        if (controlsVisible) {
            delay(40)
            if (vodPanel != VodPanel.NONE) runCatching { panelFocus.requestFocus() } else focusBar()
        } else {
            runCatching { rootFocus.requestFocus() }
        }
    }

    BackHandler {
        val activelyPlaying = state is PlayerController.State.Playing ||
            (growingRec && state is PlayerController.State.Buffering)
        when {
            // A pending hand-over is the most immediate thing BACK can cancel: cancelling it means
            // "stay on this episode", which is a different intent from closing the controls.
            upNextVisible -> upNextVisible = false
            vodPanel != VodPanel.NONE -> vodPanel = VodPanel.NONE
            controlsVisible && activelyPlaying && !paused -> controlsVisible = false
            else -> onBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Back || event.key == Key.Escape -> false
                    event.type == KeyEventType.KeyDown && !controlsVisible -> {
                        when (event.key) {
                            Key.MediaRewind, Key.DirectionLeft -> {
                                seekRelative(-10_000L)
                                positionMs = controller.player.currentPosition
                                reveal()
                                true
                            }
                            Key.MediaFastForward, Key.DirectionRight -> {
                                seekRelative(10_000L)
                                positionMs = controller.player.currentPosition
                                reveal()
                                true
                            }
                            Key.MediaPlayPause -> {
                                paused = !paused
                                controller.player.playWhenReady = !paused
                                reveal()
                                true
                            }
                            else -> { reveal(); true }
                        }
                    }
                    event.type == KeyEventType.KeyDown -> {
                        when (event.key) {
                            Key.MediaRewind -> {
                                seekRelative(-10_000L)
                                positionMs = controller.player.currentPosition
                                interaction++
                                true
                            }
                            Key.MediaFastForward -> {
                                seekRelative(10_000L)
                                positionMs = controller.player.currentPosition
                                interaction++
                                true
                            }
                            Key.MediaPlayPause -> {
                                paused = !paused
                                controller.player.playWhenReady = !paused
                                interaction++
                                true
                            }
                            else -> { interaction++; false }
                        }
                    }
                    else -> false
                }
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { if (controlsVisible) controlsVisible = false else reveal() }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    player = controller.player
                    resizeMode = aspectMode
                }
            },
            update = { pv ->
                if (pv.player != controller.player) {
                    pv.player = controller.player
                }
                // Applied from state, so the Aspect button changes the picture without the screen
                // ever holding a reference to the view.
                pv.resizeMode = aspectMode
            },
            onRelease = { pv ->
                pv.player = null
            },
        )

        when (val current = state) {
            is PlayerController.State.Buffering ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(current.title, color = Color.White)
                    }
                }

            is PlayerController.State.Error ->
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(48.dp),
                    ) {
                        Text(current.title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text(current.message, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { controller.retry() }) { Text(stringResource(R.string.common_try_again)) }
                    }
                }

            else -> Unit
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    .padding(horizontal = 28.dp, vertical = 20.dp),
            ) {
                when (vodPanel) {
                    VodPanel.NONE -> Unit
                    VodPanel.SUBTITLES, VodPanel.AUDIO -> TrackPanel(
                        panel = vodPanel,
                        controller = controller,
                        tracks = tracks,
                        firstFocus = panelFocus,
                        onDone = { vodPanel = VodPanel.NONE; interaction++ },
                    )
                    VodPanel.SPEED -> ChoicePanel(
                        title = stringResource(R.string.player_speed),
                        options = SPEED_CHOICES.map { (value, label) -> label to (playbackSpeed == value) },
                        firstFocus = panelFocus,
                        onPick = { index ->
                            val (value, _) = SPEED_CHOICES[index]
                            playbackSpeed = value
                            controller.player.setPlaybackSpeed(value)
                            vodPanel = VodPanel.NONE
                            interaction++
                        },
                    )
                    VodPanel.ASPECT -> ChoicePanel(
                        title = stringResource(R.string.player_aspect_ratio_title),
                        options = listOf(
                            stringResource(R.string.player_aspect_fit) to
                                (aspectMode == AspectRatioFrameLayout.RESIZE_MODE_FIT),
                            stringResource(R.string.player_aspect_fill) to
                                (aspectMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
                            stringResource(R.string.player_aspect_stretch) to
                                (aspectMode == AspectRatioFrameLayout.RESIZE_MODE_FILL),
                        ),
                        firstFocus = panelFocus,
                        onPick = { index ->
                            aspectMode = when (index) {
                                0 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            }
                            vodPanel = VodPanel.NONE
                            interaction++
                        },
                    )
                    VodPanel.FORMAT -> StreamInfoPanel(
                        info = streamInfo,
                        declared = declaredLabel,
                        durationMs = durationMs,
                        firstFocus = panelFocus,
                    )
                    VodPanel.NEXT_EPISODE -> NextEpisodePanel(
                        episodes = episodeQueue,
                        currentId = currentEpisode?.id,
                        nextId = nextEpisode?.id,
                        firstFocus = panelFocus,
                        onPick = { playEpisode(it) },
                    )
                }
                if (vodPanel != VodPanel.NONE) Spacer(Modifier.height(14.dp))

                // ---- Header: poster, badge + title + meta line, and the format readout ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isCatchup = item.mediaKey.startsWith("catchup:")
                    if (!posterUrl.isNullOrBlank()) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val badgeText = when {
                                growingRec -> stringResource(R.string.rec_watching_live_badge)
                                isCatchup -> "CATCH-UP"
                                else -> "VOD"
                            }
                            val badgeColor = if (growingRec) Color(0xFFE53935) else AppTheme.primary
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.2f))
                                    .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                    color = badgeColor,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (metaLine.isNotEmpty()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (streamInfo.hasVideo) {
                        Spacer(Modifier.width(18.dp))
                        StreamFormatReadout(streamInfo)
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Interactive Timeline Seekbar (Focusable, D-pad 10s intervals)
                InteractiveVodTimeline(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    bufferedMs = controller.player.bufferedPosition.coerceAtLeast(0L),
                    focusRequester = timelineFocus,
                    onSeekRelative = { delta ->
                        seekRelative(delta)
                        positionMs = controller.player.currentPosition
                        interaction++
                    },
                    onSeekTo = { targetMs ->
                        controller.player.seekTo(targetMs)
                        positionMs = targetMs
                        interaction++
                    },
                    onNavigateDown = {
                        focusBar()
                    },
                    onTogglePlayPause = {
                        paused = !paused
                        controller.player.playWhenReady = !paused
                        interaction++
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Timestamp labels: Elapsed / Total (-Remaining)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDuration(positionMs),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    val remaining = (durationMs - positionMs).coerceAtLeast(0L)
                    val durationText = if (durationMs > 0L) {
                        "${formatDuration(durationMs)}   (-${formatDuration(remaining)})"
                    } else {
                        formatDuration(durationMs)
                    }
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ---- Transport + option buttons ----
                // One labelled row: transport on the near side of the divider, the things you set
                // once (speed, subtitles, audio, format, aspect, next episode) on the far side. The
                // format button's label *is* the stream's resolution tier, so what is playing is
                // readable without opening anything. Which buttons appear is the user's choice —
                // see AppSettings.VodPlayerButton.
                val barButtons = AppSettings.VodPlayerButton.entries.filter { it in enabledButtons }
                val optionsStart = barButtons.indexOfFirst { it.ordinal >= AppSettings.VodPlayerButton.SPEED.ordinal }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionUp) {
                                timelineFocus.requestFocus()
                                true
                            } else false
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    barButtons.forEachIndexed { index, button ->
                        if (index > 0) {
                            if (index == optionsStart && optionsStart > 0) {
                                Spacer(Modifier.width(16.dp))
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .height(30.dp)
                                        .background(Color.White.copy(alpha = 0.18f)),
                                )
                                Spacer(Modifier.width(16.dp))
                            } else {
                                Spacer(Modifier.width(10.dp))
                            }
                        }
                        when (button) {
                            AppSettings.VodPlayerButton.REWIND -> VodButtonCard(
                                icon = Icons.Filled.FastRewind,
                                label = stringResource(R.string.player_skip_back),
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    seekRelative(-SKIP_MILLIS)
                                    positionMs = controller.player.currentPosition
                                    interaction++
                                },
                            )
                            AppSettings.VodPlayerButton.PLAY_PAUSE -> VodButtonCard(
                                icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                label = if (paused) stringResource(R.string.common_play) else stringResource(R.string.player_pause),
                                isPrimary = true,
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    paused = !paused
                                    controller.player.playWhenReady = !paused
                                    interaction++
                                },
                            )
                            AppSettings.VodPlayerButton.FORWARD -> VodButtonCard(
                                icon = Icons.Filled.FastForward,
                                label = stringResource(R.string.player_skip_forward),
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    seekRelative(SKIP_MILLIS)
                                    positionMs = controller.player.currentPosition
                                    interaction++
                                },
                            )
                            AppSettings.VodPlayerButton.SPEED -> VodButtonCard(
                                icon = Icons.Filled.Speed,
                                label = speedLabel(playbackSpeed),
                                isSelected = playbackSpeed != 1f,
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    vodPanel = if (vodPanel == VodPanel.SPEED) VodPanel.NONE else VodPanel.SPEED
                                    interaction++
                                },
                            )
                            AppSettings.VodPlayerButton.SUBTITLES -> VodButtonCard(
                                icon = Icons.Filled.ClosedCaption,
                                label = subtitleLabel.ifEmpty { stringResource(R.string.player_subtitles_off) },
                                isSelected = vodPanel == VodPanel.SUBTITLES,
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    vodPanel = if (vodPanel == VodPanel.SUBTITLES) VodPanel.NONE else VodPanel.SUBTITLES
                                    interaction++
                                },
                            )

                            AppSettings.VodPlayerButton.AUDIO -> VodButtonCard(
                                icon = Icons.Filled.Audiotrack,
                                label = audioButtonLabel(streamInfo, stringResource(R.string.player_audio)),
                                isSelected = vodPanel == VodPanel.AUDIO,
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    vodPanel = if (vodPanel == VodPanel.AUDIO) VodPanel.NONE else VodPanel.AUDIO
                                    interaction++
                                },
                            )
                            AppSettings.VodPlayerButton.FORMAT -> VodButtonCard(
                                icon = Icons.Filled.Videocam,
                                label = streamInfo.tierLabel.ifEmpty { stringResource(R.string.player_format) },
                                isSelected = vodPanel == VodPanel.FORMAT,
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    vodPanel = if (vodPanel == VodPanel.FORMAT) VodPanel.NONE else VodPanel.FORMAT
                                    interaction++
                                },
                            )
                            AppSettings.VodPlayerButton.ASPECT -> VodButtonCard(
                                icon = Icons.Filled.AspectRatio,
                                label = stringResource(
                                    when (aspectMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> R.string.player_aspect_fill
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> R.string.player_aspect_stretch
                                        else -> R.string.player_aspect_fit
                                    },
                                ),
                                isSelected = aspectMode != AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                focusRequester = buttonFocus[button],
                                onClick = {
                                    vodPanel = if (vodPanel == VodPanel.ASPECT) VodPanel.NONE else VodPanel.ASPECT
                                    interaction++
                                },
                            )
                            AppSettings.VodPlayerButton.NEXT_EPISODE -> VodButtonCard(
                                icon = Icons.Filled.SkipNext,
                                label = stringResource(R.string.player_next_short),
                                focusRequester = buttonFocus[button],
                                onClick = { playNext() },
                            )
                        }
                    }
                }
            }
        }

        // Up-next card. It sits outside the auto-hiding controls on purpose: it is an offer, not a
        // control, and it has to be visible while the transport bar is down. It stays out of the
        // focus order too — the countdown drives it, BACK declines it, and the next-episode button
        // in the bar is how you jump deliberately.
        val upcoming = nextEpisode
        if (upNextVisible && upcoming != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(28.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.9f))
                    .border(1.dp, AppTheme.primary.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, tint = AppTheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.player_up_next),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = AppTheme.primary,
                    )
                    Text(
                        text = "S${upcoming.season}E${upcoming.episodeNumber} · ${upcoming.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 380.dp),
                    )
                }
                Spacer(Modifier.width(18.dp))
                Text(
                    text = stringResource(R.string.player_up_next_in, upNextSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

@Composable
private fun InteractiveVodTimeline(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    focusRequester: FocusRequester,
    onSeekRelative: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onNavigateDown: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val widthDp = remember(widthPx) { with(density) { widthPx.toDp() } }
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedProgress = if (durationMs > 0L) (bufferedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionLeft, Key.MediaRewind -> {
                            onSeekRelative(-10_000L)
                            true
                        }
                        Key.DirectionRight, Key.MediaFastForward -> {
                            onSeekRelative(10_000L)
                            true
                        }
                        Key.DirectionDown -> {
                            onNavigateDown()
                            true
                        }
                        Key.Enter, Key.DirectionCenter -> {
                            onTogglePlayPause()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (size.width > 0 && durationMs > 0L) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekTo((fraction * durationMs).toLong())
                    }
                }
            }
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackHeight = if (isFocused) 6.dp else 4.dp

        // Background track
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f)),
        )

        // Buffered track
        if (bufferedProgress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(bufferedProgress)
                    .height(trackHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.35f)),
            )
        }

        // Played track (Cyan, TiviMate style)
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(trackHeight)
                .clip(RoundedCornerShape(3.dp))
                .background(AppTheme.primary),
        )

        // Cursor / Thumb
        if (widthDp.value > 0f) {
            val thumbSize = if (isFocused) 16.dp else 10.dp
            val travel = (widthDp.value - thumbSize.value).coerceAtLeast(0f)
            val dotOffset = (travel * progress).dp
            Box(
                Modifier
                    .padding(start = dotOffset)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White)
                    .then(
                        if (isFocused) Modifier.border(2.5.dp, AppTheme.primary, CircleShape)
                        else Modifier
                    ),
            )
        }
    }
}

/**
 * A compact, labelled player button: a circle with a caption underneath.
 *
 * The caption is the point. An icon-only row makes the viewer open each button to find out what it
 * does; here the label carries the live state — `1.5×`, the subtitle language, `AAC 5.1`, the
 * resolution tier — so the whole bar can be read at a glance from the sofa.
 */
@Composable
private fun VodButtonCard(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val circle = if (isPrimary) 50.dp else 42.dp
    val glyph = if (isPrimary) 24.dp else 19.dp
    val bg = when {
        focused -> Color.White
        isSelected -> Color(0xFF1E3A4B)
        isPrimary -> Color.White.copy(alpha = 0.94f)
        else -> Color(0xFF101720).copy(alpha = 0.72f)
    }
    val fg = when {
        focused -> Color(0xFF10171E)
        isSelected -> AppTheme.primary
        isPrimary -> Color(0xFF10171E)
        else -> Color.White
    }
    val outline = when {
        focused -> Modifier.border(2.dp, AppTheme.primary, CircleShape)
        isSelected -> Modifier.border(1.5.dp, AppTheme.primary, CircleShape)
        isPrimary -> Modifier.border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
        else -> Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(min = 58.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(circle)
                .clip(CircleShape)
                .background(bg)
                .then(outline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = fg,
                modifier = Modifier.size(glyph),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = if (focused || isSelected || isPrimary) FontWeight.Bold else FontWeight.Medium,
            color = if (focused) Color.White else Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A list of choices in the same shell as the audio and subtitle pickers — used for speed, aspect
 * ratio and the next-episode list, so every panel in this player looks like the same thing.
 */
@Composable
private fun ChoicePanel(
    title: String,
    options: List<Pair<String, Boolean>>,
    firstFocus: FocusRequester,
    onPick: (Int) -> Unit,
) {
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            options.forEachIndexed { index, (label, selected) ->
                TrackRow(label, selected, { onPick(index) }, if (index == 0) firstFocus else null)
            }
        }
    }
}

/**
 * The format panel: what the stream actually is, read from the decoder rather than from metadata.
 *
 * Rows with nothing to say are left out, so a provider that reports no bitrate shows fewer rows
 * instead of a row of dashes.
 */
@Composable
private fun StreamInfoPanel(
    info: StreamInfo,
    declared: String?,
    durationMs: Long,
    firstFocus: FocusRequester,
) {
    val rows = buildList {
        if (info.tierAndScanLabel.isNotEmpty()) {
            add(stringResource(R.string.player_video_format) to info.tierAndScanLabel)
        }
        if (info.compactResolutionLabel.isNotEmpty()) {
            add(stringResource(R.string.player_resolution) to info.compactResolutionLabel)
        }
        if (info.fpsLabel.isNotEmpty()) add(stringResource(R.string.player_frame_rate) to info.fpsLabel)
        if (info.videoSummary.isNotEmpty()) add(stringResource(R.string.player_video_codec) to info.videoSummary)
        if (info.dynamicRange.isNotEmpty()) add(stringResource(R.string.player_dynamic_range) to info.dynamicRange)
        if (info.audioSummary.isNotEmpty()) add(stringResource(R.string.player_audio_codec) to info.audioSummary)
        declared?.let { add(stringResource(R.string.player_advertised) to it) }
        if (durationMs > 0L) add(stringResource(R.string.player_runtime) to formatDuration(durationMs))
        info.estimatedSize(durationMs)?.let { add(stringResource(R.string.player_estimated_size) to it) }
    }

    Column(
        Modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.player_stream_info),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.player_none_available),
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(8.dp),
            )
        }
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
            rows.forEachIndexed { index, (label, value) ->
                InfoRow(label, value, if (index == 0) firstFocus else null)
            }
        }
    }
}

/** One `label — value` line of the format panel. */
@Composable
private fun InfoRow(label: String, value: String, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color.White.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/**
 * The header readout: `FHD 1080p · 1920x1080 · 23.976 fps · HEVC`.
 *
 * Deliberately the same badge shape the live player uses, so "what am I watching" reads the same
 * everywhere in the app. Pieces the stream does not report are skipped rather than left blank.
 */
@Composable
private fun StreamFormatReadout(info: StreamInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val badges = buildList {
            if (info.tierAndScanLabel.isNotEmpty()) add(info.tierAndScanLabel)
            if (info.compactResolutionLabel.isNotEmpty()) add(info.compactResolutionLabel)
            if (info.fpsLabel.isNotEmpty()) add(info.fpsLabel)
            if (info.videoCodec.isNotEmpty()) add(info.videoCodec)
            if (info.dynamicRange.isNotEmpty()) add(info.dynamicRange)
        }
        badges.forEachIndexed { index, text ->
            if (index > 0) Spacer(Modifier.width(6.dp))
            StatBadge(text, highlight = index == 0)
        }
    }
}

/** A small pill of stream telemetry — the tier badge is tinted, the rest are neutral. */
@Composable
private fun StatBadge(text: String, highlight: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .clip(shape)
            .background(
                if (highlight) AppTheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f),
            )
            .then(if (highlight) Modifier.border(1.dp, AppTheme.primary.copy(alpha = 0.7f), shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) AppTheme.primary else Color.White.copy(alpha = 0.95f),
            fontWeight = FontWeight.Bold,
        )
    }
}

private enum class VodPanel { NONE, SUBTITLES, AUDIO, SPEED, ASPECT, FORMAT, NEXT_EPISODE }

@OptIn(UnstableApi::class)
@Composable
private fun TrackPanel(
    panel: VodPanel,
    controller: PlayerController,
    tracks: Tracks,
    firstFocus: FocusRequester,
    onDone: () -> Unit,
) {
    val trackType = if (panel == VodPanel.SUBTITLES) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
    val groups = tracks.groups.filter { it.type == trackType }
    val options = mutableListOf<Triple<String, Boolean, () -> Unit>>()

    if (panel == VodPanel.SUBTITLES) {
        val anySelected = groups.any { g -> (0 until g.length).any { g.isTrackSelected(it) } }
        options += Triple(stringResource(R.string.player_subtitles_off), !anySelected) { controller.disableText(); onDone() }
    }
    groups.forEach { group ->
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val format = group.getTrackFormat(i)
            options += Triple(vodTrackLabel(format.label, format.language, options.size), group.isTrackSelected(i)) {
                controller.selectTrack(group, i); onDone()
            }
        }
    }

    Column(
        Modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text(
            if (panel == VodPanel.SUBTITLES) stringResource(R.string.player_subtitles) else stringResource(R.string.player_audio),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            if (options.isEmpty()) {
                Text(stringResource(R.string.player_none_available), color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(8.dp))
            }
            options.forEachIndexed { index, (label, selected, onClick) ->
                TrackRow(label, selected, onClick, if (index == 0) firstFocus else null)
            }
        }
    }
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit, focusRequester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        Modifier
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
        if (selected) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_selected), tint = fg)
    }
}

private fun vodTrackLabel(label: String?, language: String?, index: Int): String {
    if (!label.isNullOrBlank()) return label
    if (!language.isNullOrBlank() && language != "und") {
        return runCatching { java.util.Locale(language).displayLanguage.ifBlank { language } }.getOrDefault(language)
    }
    return "Track ${index + 1}"
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * The rest of the series, so the next episode can be picked deliberately rather than only arriving
 * on its own when one finishes.
 */
@Composable
private fun NextEpisodePanel(
    episodes: List<Episode>,
    currentId: Long?,
    nextId: Long?,
    firstFocus: FocusRequester,
    onPick: (Episode) -> Unit,
) {
    val ordered = remember(episodes) {
        episodes.sortedWith(compareBy({ it.season }, { it.episodeNumber }))
    }
    Column(
        Modifier
            .widthIn(max = 460.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.player_next_episode),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(8.dp))
        if (ordered.isEmpty()) {
            Text(
                stringResource(R.string.player_next_episode_none),
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(8.dp),
            )
        }
        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
            ordered.forEachIndexed { index, ep ->
                TrackRow(
                    label = "S${ep.season}E${ep.episodeNumber} · ${ep.title}",
                    selected = ep.id == currentId || ep.id == nextId,
                    onClick = { onPick(ep) },
                    focusRequester = if (index == 0) firstFocus else null,
                )
            }
        }
    }
}

/** The item being played. Held as state so "next episode" can swap it without a navigation. */
private data class VodItem(val mediaKey: String, val url: String, val title: String)

/** How far the rewind/forward buttons jump, and how long the up-next countdown runs. */
private const val SKIP_MILLIS = 10_000L
private const val UP_NEXT_SECONDS = 10

/** Episode row keys are `ep:<id>`, film row keys `movie:<id>` — see SeriesDetailScreen. */
private const val EPISODE_KEY_PREFIX = "ep:"
private const val MOVIE_KEY_PREFIX = "movie:"

private const val META_SEPARATOR = " · "

/** The playback rates on offer, paired with the label the speed button shows. */
private val SPEED_CHOICES = listOf(
    0.5f to "0.5×",
    0.75f to "0.75×",
    1f to "1×",
    1.25f to "1.25×",
    1.5f to "1.5×",
    2f to "2×",
)

/** The label for the current playback rate, or `1×` if it is somehow not one of the choices. */
private fun speedLabel(speed: Float): String =
    SPEED_CHOICES.firstOrNull { it.first == speed }?.second ?: "1×"

/** `AAC 5.1` once the decoder has reported a track, else the plain word for audio. */
private fun audioButtonLabel(info: StreamInfo, fallback: String): String =
    listOf(info.audioCodec, info.audioChannels)
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .ifEmpty { fallback }

/** `2h 22m` / `48 min` — a runtime the way people say it, for the header meta line. */
private fun formatRuntime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes.coerceAtLeast(1)} min"
    }
}

/**
 * The selected subtitle track's label, or empty for "nothing on".
 *
 * Empty rather than the word "Off", so the button can localise it from `player_subtitles_off`.
 */
@OptIn(UnstableApi::class)
private fun selectedTextLabel(tracks: Tracks): String {
    val group = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected } ?: return ""
    for (i in 0 until group.length) {
        if (group.isTrackSelected(i)) {
            val format = group.getTrackFormat(i)
            return format.label?.takeIf { it.isNotBlank() }
                ?: format.language?.takeIf { it.isNotBlank() }?.uppercase(java.util.Locale.US)
                ?: "On"
        }
    }
    return ""
}

/** The row id inside an `ep:<id>` media key, or null when this item is not an episode. */
private fun String.episodeRowId(): Long? =
    if (startsWith(EPISODE_KEY_PREFIX)) removePrefix(EPISODE_KEY_PREFIX).toLongOrNull() else null

/** The row id inside a `movie:<id>` media key, or null when this item is not a film. */
private fun String.movieRowId(): Long? =
    if (startsWith(MOVIE_KEY_PREFIX)) removePrefix(MOVIE_KEY_PREFIX).toLongOrNull() else null
