/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.content.Context
import app.opentv.data.db.OpenTvDatabase
import app.opentv.data.remote.StalkerApi
import app.opentv.data.remote.PlexApi
import app.opentv.data.remote.StremioClient
import app.opentv.data.remote.XtreamApi
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.repo.EpgRepository
import app.opentv.data.repo.RecordingRepository
import app.opentv.data.repo.ReminderRepository
import app.opentv.data.repo.SourceRepository
import app.opentv.player.PlayerController
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/**
 * Hand-rolled dependency container.
 *
 * Hilt would be the conventional choice. It is deliberately not used: it adds an annotation
 * processor, a build-time graph, and a class of error message that is genuinely hard to read,
 * in exchange for wiring roughly a dozen objects. For a project whose main hope is drive-by
 * contributions from people fixing their own bugs, "you can read the whole graph in one file"
 * is worth more than the ceremony.
 */
object ServiceLocator {

    @Volatile private var instance: Graph? = null

    fun get(context: Context): Graph =
        instance ?: synchronized(this) {
            instance ?: Graph(context.applicationContext).also { instance = it }
        }

    /**
     * Clears the singleton so the next [get] builds a fresh [Graph].
     *
     * Called from [MainActivity.onDestroy] when the Activity is finishing (not a config
     * change). This ensures a process restart on Fire OS — which sometimes re-uses the VM
     * without calling Application.onCreate again — starts with clean singletons.
     */
    fun clear() {
        synchronized(this) {
            instance = null
        }
    }

    class Graph(context: Context) {
        private val appContext = context.applicationContext

        val settings: AppSettings by lazy { AppSettings.get(appContext) }

        val database: OpenTvDatabase by lazy { OpenTvDatabase.build(context) }

        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                // 50 MB HTTP cache: feeds and playlists whose hosts send ETag/Last-Modified
                // validators revalidate with a 304 instead of re-downloading in full every
                // refresh. Responses the server did not allow caching are never stored, so
                // this only ever saves bytes. (The streaming client deliberately has no
                // cache — live streams are infinite bodies.)
                .cache(
                    okhttp3.Cache(
                        java.io.File(appContext.cacheDir, "http_cache"),
                        50L * 1024 * 1024,
                    ),
                )
                .connectTimeout(15, TimeUnit.SECONDS)
                // Generous: catalogue endpoints on a busy panel can take a long time to
                // produce 40,000 rows, and timing out mid-list is worse than waiting.
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        /**
         * A separate client for the player's data source. A live stream is an effectively
         * infinite HTTP response body, so the catalogue client's five-minute [callTimeout] would
         * guillotine every live channel at that mark — here there is no overall call timeout, only
         * a read timeout that catches a genuinely dead connection. Cross-protocol redirects are
         * followed explicitly because IPTV stream URLs routinely bounce http→https onto a CDN, and
         * a client that stops at the redirect surfaces as "lost connection to server".
         */
        val streamingHttpClient: OkHttpClient by lazy {
            httpClient.newBuilder()
                // Inherits the catalog cache unless disabled: a live stream is an effectively
                // infinite body and must never be spooled toward the disk cache.
                .cache(null)
                .callTimeout(0, TimeUnit.MILLISECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }

        val xtreamApi: XtreamApi by lazy { XtreamApi(httpClient) }

        /** Stalker / Ministra portal client (MAC handshake + create_link). */
        val stalkerApi: StalkerApi by lazy { StalkerApi(httpClient) }

        /**
         * Plex Media Server client. Used only for the sign-in handshake against plex.tv and for
         * reading libraries, artwork and playback from the address the user typed.
         */
        val plexApi: PlexApi by lazy { PlexApi(httpClient) }

        /** Neutral Stremio add-on protocol client. Talks only to user-added manifest URLs. */
        val stremioClient: StremioClient by lazy { StremioClient(httpClient) }

        /**
         * The torrent engine, bundled in this APK and run as a child process.
         *
         * Lazy because most sessions never touch it: constructing it costs nothing, but starting it
         * spawns a process, opens peer connections and claims 64 MB of RAM, which is far too much to
         * pay for on a box that only wanted to watch the guide.
         */
        val torrentEngine: app.opentv.torrent.TorrentEngine by lazy {
            app.opentv.torrent.TorrentEngine(appContext, engineHttpClient)
        }

        /**
         * A client for the engine on loopback, with timeouts of its own.
         *
         * The catalogue client's five-minute call timeout is right for a panel producing 40,000 rows
         * and wrong for an engine on the same device: the status poll runs every second, so a wedged
         * engine must fail in seconds rather than stall the poll for minutes. `rem` is the slowest
         * call the engine serves — it waits for the torrent to close, up to five seconds — which is
         * what the twenty-second read timeout covers. No cache: everything here is local state that
         * changes every second.
         */
        private val engineHttpClient: OkHttpClient by lazy {
            httpClient.newBuilder()
                .cache(null)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Talks to [torrentEngine]. The address is resolved per call, because the engine picks its
         * port when it starts and the client outlives any single run of it.
         */
        val torrServerClient: app.opentv.data.remote.TorrServerClient by lazy {
            app.opentv.data.remote.TorrServerClient(
                baseUrl = { torrentEngine.baseUrl },
                http = engineHttpClient,
            )
        }

        /**
         * The page a phone opens to add a torrent, served on the LAN for as long as the app runs —
         * a magnet sent from a phone should not need anyone standing at the TV.
         *
         * The address is stable across visits (one saved key, one preferred port), so a Shortcuts
         * action or a bookmark set up once keeps working. The socket itself is started and stopped
         * with the app's composition.
         */
        private val phoneAddServerDelegate: Lazy<app.opentv.torrent.PhoneAddServer> = lazy {
            app.opentv.torrent.PhoneAddServer(key = settings.phoneAddKey) { link ->
                // The Torrents view model drains this queue and starts the torrent, wherever the
                // viewer is; the phone waits here for its answer.
                app.opentv.torrent.PhoneAddRequests.request(link)
            }
        }

        /** The phone-add page; starting it is the composition's job. */
        val phoneAddServer: app.opentv.torrent.PhoneAddServer by phoneAddServerDelegate

        /**
         * Stops the phone-add socket if this session ever opened it.
         *
         * Called on the way out with the other teardowns above. Guarded so a session that never
         * composed the shell does not build the object just to destroy it — it is only a state
         * flow, but the habit is the point.
         */
        fun stopPhoneAddServer() {
            if (phoneAddServerDelegate.isInitialized()) {
                runCatching { phoneAddServer.stop() }
            }
        }

        /**
         * Shared keyless-weather client for the player header. Its station-id cache must
         * outlive a single player visit — a per-composition instance repaid the
         * zip→coords→points→stations→observation round trips on every entry.
         */
        val weatherClient: app.opentv.data.remote.WeatherClient by lazy {
            app.opentv.data.remote.WeatherClient(httpClient)
        }

        val sourceRepository: SourceRepository by lazy {
            SourceRepository(database.sources(), xtreamApi, stalkerApi, plexApi)
        }

        val catalogRepository: CatalogRepository by lazy {
            CatalogRepository(
                sourceDao = database.sources(),
                channelDao = database.channels(),
                categoryDao = database.categories(),
                movieDao = database.movies(),
                seriesDao = database.series(),
                episodeDao = database.episodes(),
                positionDao = database.positions(),
                api = xtreamApi,
                stalkerApi = stalkerApi,
                plexApi = plexApi,
                http = httpClient,
                settings = settings,
            )
        }

        val epgRepository: EpgRepository by lazy {
            EpgRepository(
                programmeDao = database.programmes(),
                feedDao = database.epgFeeds(),
                aliasDao = database.epgAliases(),
                channelDao = database.channels(),
                sourceDao = database.sources(),
                api = xtreamApi,
                http = httpClient,
                settings = settings,
                db = database,
            )
        }

        val playbackPositions get() = database.positions()

        val profiles get() = database.profiles()

        val recordingRepository: RecordingRepository by lazy {
            RecordingRepository(database.recordings(), database.seriesRules())
        }

        val reminderRepository: ReminderRepository by lazy {
            ReminderRepository(database.reminders())
        }

        val recordingEngine: app.opentv.recording.RecordingEngine by lazy {
            app.opentv.recording.RecordingEngine(
                appContext, recordingRepository, sourceRepository, settings,
                database.channels(), epgRepository,
            )
        }

        /**
         * Backing lazy for [livePlayer]. Kept as an explicit [lazy] field so teardown can ask
         * `isInitialized()` — the `by lazy` keyword form has no reflection-free way to do that
         * (unlike `lateinit var`). This matters at process exit: we want to release the player
         * *if it exists* without constructing it purely for the sake of destroying it.
         */
        private val livePlayerDelegate: Lazy<PlayerController> = lazy {
            PlayerController(
                context = appContext,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                httpClient = streamingHttpClient,
                subtitlesEnabled = true,
                // Honour Settings > Recording > "Pause & rewind live TV". This was hard-coded false,
                // which left that switch completely dead: it was written to prefs and read back into
                // the settings UI, but nothing consumed it, so the live player never kept a
                // back-buffer and rewind on live TV had nothing to seek into.
                //
                // Read here rather than at each use because the flag configures the player's
                // LoadControl at construction (see PlayerController.dvr). The shared live player is
                // built lazily on first playback and lives for the run of the process, so toggling
                // the switch takes effect from the next launch — which is also why it is a snapshot
                // and not a collector: swapping the player to apply it mid-stream would drop the
                // channel being watched.
                dvr = settings.livePauseEnabled.value,
            )
        }

        /**
         * Shared Live TV player instance across full screen and the guide preview pane.
         * Keeps the stream playing uninterrupted with zero buffering when backing out to the guide.
         */
        val livePlayer: PlayerController by livePlayerDelegate

        /**
         * Stops the shared Live TV player only if it was ever created.
         *
         * Lifecycle teardown and multiview must use this instead of touching [livePlayer]:
         * it is lazy, so a session that only browsed menus would CONSTRUCT a full ExoPlayer
         * — renderers, track selector, allocator, handler threads — purely to stop it again.
         */
        fun stopLivePlayer() {
            if (livePlayerDelegate.isInitialized()) {
                runCatching { livePlayer.stop() }
            }
        }

        /**
         * Releases the shared Live TV player if it was ever created.
         *
         * Must be called before [ServiceLocator.clear] when the Activity is finishing, so the
         * native decoder is freed before the process VM shuts down. Without this, Fire OS
         * devices — especially the Cube with its Dolby pipeline — can keep the audio decoder
         * alive past process death.
         *
         * Idempotent: [PlayerController.release] is safe to call multiple times. If the lazy
         * was never touched (no playback this session), nothing is constructed or released.
         */
        fun releaseLivePlayer() {
            if (livePlayerDelegate.isInitialized()) {
                runCatching { livePlayer.release() }
            }
        }

        /**
         * Stops the bundled torrent engine, if this session ever started it.
         *
         * Called on the way out with the player teardown above. Unlike the players this needs no
         * `isInitialized` guard: [app.opentv.torrent.TorrentEngine.stop] is a no-op when no process
         * was started, and the object itself holds nothing but a state flow — constructing it in a
         * session that never opened the Torrents tab costs nothing and starts nothing.
         */
        fun stopTorrentEngine() {
            runCatching { torrentEngine.stop() }
        }
    }
}