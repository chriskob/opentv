/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Owns the single [ExoPlayer] instance and everything about switching what it is playing.
 *
 * ## Why one player, reused
 *
 * The obvious implementation of "change channel" is release the player and build a new one.
 * On a low-end TV box that takes long enough that a user pressing channel-up four times in a
 * row can queue four constructions and four teardowns, and the codec ends up in a state where
 * nothing plays until the app is killed. That is the "changing channels too quickly causes
 * streams to fail" class of bug, and it is entirely self-inflicted.
 *
 * OpenTV keeps exactly one player for the lifetime of the screen and only ever swaps its
 * media item. Requests are debounced, and an in-flight switch is cancelled the moment a newer
 * one arrives, so holding channel-up costs one actual tune — the one the user stopped on.
 *
 * ## Lifecycle-aware error recovery
 *
 * The error listener auto-restarts on a transient failure (provider drops connection when
 * another device starts streaming). It checks [stopped] before retrying — so a stream that
 * fails while the user already backed out to the guide never "comes back" as phantom audio.
 */
@OptIn(UnstableApi::class)
class PlayerController(
    context: Context,
    private val scope: CoroutineScope,
    httpClient: OkHttpClient,
    subtitlesEnabled: Boolean = true,
    /**
     * Whether this player asks the system for audio focus. The live/VOD players keep it on.
     * Multiview's panes turn it off: the screen already mutes the unfocused pane itself, and
     * a second player grabbing AUDIOFOCUS_GAIN ducked (or paused) the first through the
     * framework — the two panes fought the system over who was audible.
     */
    private val handleAudioFocus: Boolean = true,
    /**
     * Tunes the player for the guide's muted preview pane: a shallow start buffer so a highlighted
     * channel shows a frame quickly, and a longer switch debounce so scrolling the channel list
     * does not tune to every channel passed over. The full-screen players leave this false and keep
     * the deep buffer that rides out a twitchy IPTV source.
     */
    private val preview: Boolean = false,
    /**
     * Turns the live player into a shallow DVR: a retained back-buffer so the viewer can pause and
     * rewind live TV within the last few minutes. Off by default because holding minutes of video
     * in memory is a real cost on a cheap box; the recording player and preview never set it.
     */
    private val dvr: Boolean = false,
    /**
     * When set, `smb://` media (a recording on a NAS) is read through this source so it plays and
     * seeks in-app. Null for the live/VOD players, which never see an smb URI.
     */
    smbDataSourceFactory: androidx.media3.datasource.DataSource.Factory? = null,
    /**
     * When set, `optvrec://<id>` media (a recording still being written) is read through this
     * tail-following source so it can be watched while it records. Null everywhere except the
     * recording player.
     */
    growingDataSourceFactory: androidx.media3.datasource.DataSource.Factory? = null,
    /**
     * Playing a recording that is still being written (watch-while-recording). Playback is held a
     * cushion *behind* the file's growing edge (see the LIVE_REC_* constants) so the bursty arrival
     * of an IPTV source — worst when the recording is read back over SMB from a NAS, where each read
     * is a network round-trip — rides over the gaps instead of stalling on every one.
     */
    private val liveRecording: Boolean = false,
) {

    /** Channel-surf debounce for this controller — longer for the preview so browsing is calm. */
    private val switchDebounceMillis = if (preview) PREVIEW_SWITCH_DEBOUNCE_MILLIS else SWITCH_DEBOUNCE_MILLIS

    sealed interface State {
        data object Idle : State
        data class Buffering(val title: String) : State
        data class Playing(val title: String) : State
        /**
         * A stream failed and is being retried automatically, [attempt] of [maxAttempts].
         *
         * This exists because the old behaviour dropped straight back to a bare [Buffering]
         * between attempts. A provider gateway timeout takes the better part of a minute to
         * fail, so with a short restart delay the viewer was left watching an unexplained spinner
         * for minutes, with no way to tell a struggling provider from a hung app. Naming the
         * reason and counting the attempts turns "it is broken" into "it is trying, and here is
         * how far it has got".
         */
        data class Retrying(
            val title: String,
            val message: String,
            val attempt: Int,
            val maxAttempts: Int,
        ) : State

        /**
         * Playback stopped and automatic retries are exhausted. [message] is written for humans.
         * [canRetry] says whether a retry button is worth offering.
         */
        data class Error(val title: String, val message: String, val canRetry: Boolean) : State

        /**
         * Buffering, but for longer than any real stream takes.
         *
         * This exists because the commonest provider failure raises NO error at all: the panel
         * accepts the connection and then stops sending, so ExoPlayer sits in BUFFERING
         * indefinitely and never calls back. The viewer gets a naked spinner that could mean
         * anything from a slow channel to a dead one, and no way to tell. [canRetry] false is a
         * warning; true means the budget is spent and this is now an error.
         */
        data class Stalled(
            val title: String,
            val message: String,
            val canRetry: Boolean,
        ) : State
    }

    data class Request(
        val url: String,
        val title: String,
        val userAgent: String,
        /** Live streams are never resumed; VOD is. */
        val startPositionMillis: Long = 0L,
        val isLive: Boolean = true,
    )

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** The current stream's tracks (audio/text/video), for the in-player pickers. */
    private val _tracks = MutableStateFlow(Tracks.EMPTY)
    val tracks: StateFlow<Tracks> = _tracks.asStateFlow()

    private var switchJob: Job? = null
    private var current: Request? = null
    val currentRequest: Request? get() = current
    private var consecutiveFailures = 0

    /**
     * The retry banner currently on screen, so it survives the re-prepare a retry causes.
     * Without this, [play] overwrites it with a plain buffering state and the explanation
     * vanishes for the whole time the next attempt spends failing.
     */
    private var pendingRetry: State.Retrying? = null

    /**
     * Watches how long a tune has spent buffering, so a provider that connects and then goes
     * quiet does not look like an app that has hung. See [State.Stalled].
     */
    private var stallJob: Job? = null

    /** The stream [stallJob] is watching, so a re-tune of the same one cannot reset the clock. */
    private var stallUrl: String? = null

    private fun watchForStall(request: Request) {
        stallJob?.cancel()
        stallUrl = request.url
        stallJob = scope.launch {
            delay(STALL_WARN_MILLIS)
            val warned = _state.value
            if (warned is State.Buffering) {
                _state.value = State.Stalled(
                    title = request.title,
                    message = "Still waiting for this channel. The provider has accepted the " +
                        "request but is not sending video yet — a slow or overloaded provider, " +
                        "not a problem with this device.",
                    canRetry = false,
                )
            }
            delay((STALL_GIVE_UP_MILLIS - STALL_WARN_MILLIS).coerceAtLeast(0L))
            val still = _state.value
            if (still is State.Buffering || still is State.Stalled) {
                _state.value = State.Error(
                    title = request.title,
                    message = "This channel never started playing. The provider stopped sending " +
                        "video, so there is nothing left to wait for — try again, or pick " +
                        "another channel.",
                    canRetry = current != null,
                )
            }
        }
    }

    /**
     * Set true by [stop] and [release]. Once stopped, the error-listener auto-restart is
     * suppressed — this is what prevents "phantom audio" after the user backs out of the
     * player or exits the app. Without this guard, a stream that drops mid-playback would
     * re-spawn playback ~1.5s later even though the user already navigated away.
     */
    @Volatile
    private var stopped = false

    /**
     * Held so the User-Agent can be swapped per source before each tune.
     *
     * Providers commonly 403 any client whose UA they do not recognise, and the workaround —
     * "try a different User-Agent" — is useless unless the setting actually reaches the
     * player's HTTP layer. Setting it on the factory applies it to data sources created for
     * subsequent loads, which is exactly the granularity we need.
     */
    private val httpFactory = OkHttpDataSource.Factory(httpClient).apply {
        setDefaultRequestProperties(mapOf("User-Agent" to DEFAULT_USER_AGENT))
    }

    private val dataSourceFactory: androidx.media3.datasource.DataSource.Factory =
        DefaultDataSource.Factory(context, httpFactory).let { default ->
            val custom = buildMap<String, androidx.media3.datasource.DataSource.Factory> {
                if (smbDataSourceFactory != null) put("smb", smbDataSourceFactory)
                if (growingDataSourceFactory != null) put("optvrec", growingDataSourceFactory)
            }
            if (custom.isEmpty()) default else RoutingDataSourceFactory(default, custom)
        }

    /**
     * Retry policy tuned for IPTV rather than for CDNs.
     *
     * A provider under load returns 403, 429 or 500 for a few seconds and then serves the
     * stream perfectly well. ExoPlayer's default is to give up almost immediately on those,
     * which surfaces to the user as a channel that "doesn't work" but plays fine on the
     * second try. We retry them with backoff instead. 401 and 404 are not retried — those
     * genuinely will not fix themselves.
     */
    private val loadErrorPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val statusCode =
                (info.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode

            return when (statusCode) {
                401, 404, 410 -> C_TIME_UNSET
                403, 405, 429, 500, 502, 503, 504 -> backoffFor(info.errorCount)
                null -> backoffFor(info.errorCount) // network blips
                else -> super.getRetryDelayMsFor(info)
            }
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = MAX_LOAD_RETRIES

        private fun backoffFor(errorCount: Int): Long =
            if (errorCount > MAX_LOAD_RETRIES) C_TIME_UNSET
            else minOf(INITIAL_BACKOFF_MILLIS * (1L shl (errorCount - 1)), MAX_BACKOFF_MILLIS)
    }

    /**
     * Held so captions can be turned on and off at runtime. Prefer the device language when
     * captions are on; [setSubtitlesEnabled] flips the text renderer off entirely.
     */
    private val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setPreferredTextLanguage(java.util.Locale.getDefault().language)
            .setSelectUndeterminedTextLanguage(true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
            .build()
    }

    /**
     * Software-decode fallback.
     *
     * Vendor hardware decoders routinely refuse to initialise for some codecs on cheap TV boxes
     * (HEVC 10-bit, MPEG-2, AV1, and Dolby audio on devices that only do passthrough). By default
     * ExoPlayer never tries a second decoder, so a channel VLC plays happily fails here outright —
     * which is the usual reason a stream "works in other apps".
     *
     * `EXTENSION_RENDERER_MODE_ON` is the hook for the Media3 FFmpeg extension: it is used when
     * present and ignored (with a log line) when it is not, so bundling the extension later needs
     * no code change here. ON rather than PREFER on purpose — these boxes are weak, so the hardware
     * decoder keeps first refusal and software is only the rescue path.
     */
    private val renderersFactory = DefaultRenderersFactory(context)
        .setEnableDecoderFallback(true)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setSeekBackIncrementMs(SEEK_INCREMENT_MILLIS)
        .setSeekForwardIncrementMs(SEEK_INCREMENT_MILLIS)
        .setRenderersFactory(renderersFactory)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(loadErrorPolicy),
        )
        .setTrackSelector(trackSelector)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setAllocator(DefaultAllocator(true, 64 * 1024))
                .setTargetBufferBytes(if (preview) PREVIEW_TARGET_BUFFER_BYTES else TARGET_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(
                    if (preview) PREVIEW_MIN_BUFFER_MILLIS else MIN_BUFFER_MILLIS,
                    when {
                        preview -> PREVIEW_MAX_BUFFER_MILLIS
                        dvr -> DVR_MAX_BUFFER_MILLIS
                        liveRecording -> LIVE_REC_MAX_BUFFER_MILLIS
                        else -> MAX_BUFFER_MILLIS
                    },
                    when {
                        preview -> PREVIEW_BUFFER_FOR_PLAYBACK_MILLIS
                        liveRecording -> LIVE_REC_BUFFER_FOR_PLAYBACK_MILLIS
                        else -> BUFFER_FOR_PLAYBACK_MILLIS
                    },
                    when {
                        preview -> PREVIEW_BUFFER_AFTER_REBUFFER_MILLIS
                        liveRecording -> LIVE_REC_BUFFER_AFTER_REBUFFER_MILLIS
                        else -> BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MILLIS
                    },
                )
                // Retain the last few minutes so pause/rewind of live TV has something to seek
                // into — bounded to exactly that window. retainAllSegments=true also kept every
                // older segment: minutes of live TS at full bitrate pinned in the heap, which is
                // how the pause feature OOM/GC-thrashed the low-RAM boxes it targets.
                .apply { if (dvr) setBackBuffer(DVR_BACK_BUFFER_MILLIS, false) }
                .build(),
        )
        .build()
        .apply {
            addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    _tracks.value = tracks
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val title = current?.title.orEmpty()
                    // Playback recovered: the retry banner has done its job, and the stall
                    // watchdog no longer has anything to watch.
                    if (playbackState == Player.STATE_READY) {
                        pendingRetry = null
                        stallUrl = null
                        stallJob?.cancel()
                    }
                    _state.value = when (playbackState) {
                        Player.STATE_BUFFERING -> State.Buffering(title)
                        Player.STATE_READY -> {
                            consecutiveFailures = 0
                            State.Playing(title)
                        }
                        Player.STATE_IDLE -> _state.value
                        Player.STATE_ENDED -> State.Idle
                        else -> _state.value
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Do NOT auto-retry if the controller has been explicitly stopped —
                    // this is the critical guard against background audio after the user
                    // has already navigated away or exited the app.
                    if (stopped) return

                    consecutiveFailures++
                    val request = current
                    val message = PlaybackErrors.describe(error)

                    if (request != null && consecutiveFailures < MAX_AUTO_RETRIES) {
                        // Say what went wrong and how many attempts there are, rather than
                        // falling back to an anonymous spinner.
                        val banner = State.Retrying(
                            title = request.title,
                            message = message,
                            attempt = consecutiveFailures,
                            maxAttempts = MAX_AUTO_RETRIES,
                        )
                        pendingRetry = banner
                        _state.value = banner
                        scope.launch {
                            delay(AUTO_RESTART_DELAY_MILLIS)
                            // Re-check stopped flag after the delay — the user may have
                            // navigated away during the wait.
                            if (!stopped && current == request) {
                                // preserveRetry holds the banner up across the re-prepare.
                                // Without it play() replaces it with a plain Buffering, and the
                                // viewer is back to the bare spinner this is fixing.
                                play(request, debounce = false, preserveRetry = true)
                            }
                        }
                    } else {
                        // Attempts are spent. Stop and hand the decision to the viewer with a
                        // button: a provider having a bad hour should not look like the app
                        // abandoned them, and it should not look like the app is still busy
                        // trying when it has actually stopped.
                        pendingRetry = null
                        _state.value = State.Error(
                            title = request?.title.orEmpty(),
                            message = message,
                            canRetry = request != null,
                        )
                    }
                }
            })
        }

    init {
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            handleAudioFocus,
        )
    }

    /**
     * Switches playback.
     *
     * @param debounce when true (the default for channel surfing) the switch waits briefly so
     * that rapid presses collapse into a single tune. Pass false for a deliberate selection.
     * @param preserveRetry keeps an on-screen retry banner up across the switch, so an automatic
     * retry does not flash back to an unexplained spinner. A deliberate tune or a channel change
     * leaves this false and clears the banner.
     */
    fun play(request: Request, debounce: Boolean = true, preserveRetry: Boolean = false) {
        if (current?.url == request.url && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)) {
            current = request
            player.playWhenReady = true
            return
        }
        // Cancelling here is what makes fast channel-changing safe: the previous switch never
        // reaches the player, so we never stack prepares.
        switchJob?.cancel()
        current = request
        stopped = false

        if (!preserveRetry) pendingRetry = null
        // Immediately stop old stream and show buffering for the new channel.
        //
        // A stall message is deliberately NOT replaced here. The guide re-tunes the same channel
        // routinely (volume, resume, focus changes), and each of those came through play() and
        // wiped the explanation back to a naked spinner — which is the exact symptom the stall
        // state exists to remove. It survives until the stream plays or the viewer acts.
        _state.value = when {
            pendingRetry != null -> pendingRetry!!
            stallUrl == request.url && _state.value is State.Stalled -> _state.value!!
            else -> State.Buffering(request.title)
        }
        watchForStall(request)
        player.stop()
        player.clearMediaItems()

        switchJob = scope.launch {
            if (debounce) delay(switchDebounceMillis)

            // Only a genuinely new tune clears the count. A retry deliberately keeps it, or the
            // counter would restart at 1 on every attempt and the app would retry a broken
            // provider forever instead of eventually stopping and offering the button.
            if (!preserveRetry) consecutiveFailures = 0
            httpFactory.setDefaultRequestProperties(mapOf("User-Agent" to request.userAgent))

            val mediaItem = MediaItem.Builder()
                .setUri(request.url)
                .build()

            with(player) {
                setMediaItem(mediaItem)
                if (!request.isLive && request.startPositionMillis > 0) {
                    seekTo(request.startPositionMillis)
                }
                playWhenReady = true
                prepare()
            }
        }
    }

    fun retry() {
        // A deliberate press starts the whole budget again, and forgets any stall message —
        // otherwise the sticky banner would outlive the reason for it.
        pendingRetry = null
        stallUrl = null
        current?.let { play(it, debounce = false) }
    }

    /**
     * The quick captions toggle. On is *not* merely "allow the text renderer" — that leaves it
     * to the selector to guess a language, which for a lot of IPTV streams guesses nothing and
     * the user sees no captions even though they turned them on. So On explicitly selects the
     * first available subtitle track; Off disables text entirely.
     */
    fun setSubtitlesEnabled(enabled: Boolean) {
        if (!enabled) {
            disableText()
            return
        }
        val firstText = _tracks.value.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        if (firstText != null) {
            selectTrack(firstText, firstSupportedIndex(firstText))
        } else {
            // Nothing to select yet — allow the renderer so a track that arrives later can be
            // picked up, and re-assert once tracks change.
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    /** Force a specific audio or text track on (used by the in-player pickers). */
    fun selectTrack(group: Tracks.Group, trackIndex: Int) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .setTrackTypeDisabled(group.type, false)
            .build()
    }

    /** Turn subtitles off entirely. */
    fun disableText() {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun seekBackward() {
        if (player.isCurrentMediaItemSeekable) player.seekBack()
    }

    fun seekForward() {
        if (player.isCurrentMediaItemSeekable) player.seekForward()
    }

    val isSeekable: Boolean get() = player.isCurrentMediaItemSeekable

    private fun firstSupportedIndex(group: Tracks.Group): Int {
        for (i in 0 until group.length) if (group.isTrackSupported(i)) return i
        return 0
    }

    /** Stops playback, clears media items, and sets the lifecycle guard so error auto-retry
     * does not resurrect playback. Call this when the user backs out of the player. */
    fun stop() {
        stopped = true
        switchJob?.cancel()
        current = null
        player.stop()
        player.clearMediaItems()
        _state.value = State.Idle
    }

    /**
     * Full teardown: releases the ExoPlayer, freeing the native codec and audio pipeline.
     * After this call the player is dead; a new [PlayerController] must be created. Called
     * only when the Activity is finishing (isFinishing == true), not during normal navigation.
     */
    fun release() {
        stopped = true
        switchJob?.cancel()
        player.release()
        // Cancel any error-retry still waiting out its backoff. Without this, the delayed
        // job — and the controller it holds — stays reachable on the Main dispatcher past
        // release() and ServiceLocator.clear(), until the delay elapses on its own.
        scope.coroutineContext[Job]?.cancel()
    }

    private companion object {
        const val DEFAULT_USER_AGENT = "OpenTV/0.1 (Android)"

        /** ExoPlayer's "do not retry" sentinel. */
        const val C_TIME_UNSET = androidx.media3.common.C.TIME_UNSET

        const val SWITCH_DEBOUNCE_MILLIS = 350L
        const val SEEK_INCREMENT_MILLIS = 15_000L
        const val INITIAL_BACKOFF_MILLIS = 500L
        const val MAX_BACKOFF_MILLIS = 8_000L
        const val MAX_LOAD_RETRIES = 5
        /**
         * Automatic attempts before the player stops and offers a retry button.
         *
         * Was 3, and was invisible besides. A provider gateway timeout can take the better part
         * of a minute to fail, so three silent attempts meant minutes of unexplained spinner.
         * The count is now on screen, and the budget is 5: long enough to ride out a provider
         * blip, short enough that a genuinely dead stream reaches the button instead of looping.
         */
        const val MAX_AUTO_RETRIES = 5
        const val AUTO_RESTART_DELAY_MILLIS = 1_500L

        /**
         * How long a tune may buffer before we say so, and how long before we stop waiting.
         *
         * Calibrated against this provider, which took 28s to start a stream that then played
         * perfectly. A 15s warning would have fired on healthy playback and taught the viewer to
         * ignore it, so both numbers sit clearly above a slow start. 30s is where "waiting" stops
         * being plausible; 75s is where it stops being reasonable at all.
         */
        const val STALL_WARN_MILLIS = 30_000L
        const val STALL_GIVE_UP_MILLIS = 75_000L

        /** Memory caps for low-RAM TV hardware (1GB/1.5GB RAM). */
        const val TARGET_BUFFER_BYTES = 16 * 1024 * 1024
        const val PREVIEW_TARGET_BUFFER_BYTES = 8 * 1024 * 1024

        /**
         * Fast initial start buffer for IPTV live streams (matching TiviMate tuning).
         * 500ms buffer threshold starts playback immediately on first chunk arrival,
         * while retaining a healthy 4s-15s buffer ceiling to ride out network jitter without
         * ballooning memory usage.
         */
        const val MIN_BUFFER_MILLIS = 4_000
        const val MAX_BUFFER_MILLIS = 15_000
        const val BUFFER_FOR_PLAYBACK_MILLIS = 500
        const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MILLIS = 1_000

        // DVR mode (pause & rewind live TV): keep a couple of minutes behind the live edge to seek
        // into, and let the forward buffer grow to match so a pause of up to a couple of minutes
        // resumes cleanly. Deliberately bounded — this is memory the box has to find.
        const val DVR_BACK_BUFFER_MILLIS = 120_000
        const val DVR_MAX_BUFFER_MILLIS = 120_000

        // Watch-while-recording: hold playback a cushion *behind* the file's growing edge rather than
        // right on it. IPTV sources arrive in bursts, so a shallow buffer drains to nothing between
        // bursts and the picture stalls every second or two — worst over SMB to a NAS, where every
        // read is a network round-trip. Waiting for ~8s of reservoir before playback (and rebuilding
        // it after any stall) rides straight over those gaps; because playback and the recorder both
        // advance at 1x, that cushion holds without ever catching the edge again.
        const val LIVE_REC_MAX_BUFFER_MILLIS = 60_000
        const val LIVE_REC_BUFFER_FOR_PLAYBACK_MILLIS = 8_000
        const val LIVE_REC_BUFFER_AFTER_REBUFFER_MILLIS = 8_000

        // Guide-preview tuning: start fast and don't hoard buffer (the preview is muted and
        // low-stakes), and debounce channel-surfing harder so scrolling the list doesn't tune to
        // every channel in passing. Kept within DefaultLoadControl's constraints: min >= both
        // playback thresholds, max >= min.
        const val PREVIEW_MIN_BUFFER_MILLIS = 5_000
        const val PREVIEW_MAX_BUFFER_MILLIS = 12_000
        const val PREVIEW_BUFFER_FOR_PLAYBACK_MILLIS = 1_000
        const val PREVIEW_BUFFER_AFTER_REBUFFER_MILLIS = 1_500
        // P1: Reduced from 700ms to 350ms so the preview pane tunes faster after pressing OK on a
        // channel, while still debouncing rapid scrolling.
        const val PREVIEW_SWITCH_DEBOUNCE_MILLIS = 350L

        const val LIVE_TARGET_OFFSET_MILLIS = 10_000L
    }
}