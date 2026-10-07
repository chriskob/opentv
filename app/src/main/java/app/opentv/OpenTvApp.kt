/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import android.app.Application
import android.content.Context
import android.util.Log
import app.opentv.core.LocaleUtils
import app.opentv.core.ServiceLocator
import app.opentv.core.Startup
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.work.SyncWorker
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long startup maintenance waits for the first frame before running anyway. */
private const val FIRST_FRAME_WAIT_MILLIS = 10_000L
private const val STARTUP_MAINTENANCE_DELAY_MILLIS = 120_000L

class OpenTvApp : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Read through the service locator rather than held directly: the interceptor runs on an image
    // thread where a synchronous SharedPreferences read is fine but constructing a second AppSettings
    // would not be.
    private val plexServerHost: String? get() = ServiceLocator.get(this).settings.plexServerHost
    private val plexAuthToken: String? get() = ServiceLocator.get(this).settings.plexAuthToken

    /**
     * The app-wide Coil loader, tuned for a poster-and-logo heavy UI on a low-end TV box. The
     * default loader keeps a small memory cache and no disk cache, so scrolling back through a
     * shelf — or reopening Movies — re-downloads and re-decodes every image. Here:
     *  - a generous memory cache and a 64 MB disk cache mean art you've already seen paints from
     *    cache, instantly, instead of hitting the network. Disk entries are keyed by URL and honor
     *    HTTP cache headers, so stable provider art stays cached while changed art revalidates;
     *  - no crossfade — an immediate swap reads as snappier on a d-pad grid than a fade, and skips
     *    a frame of blending per image;
     *  - RGB_565 for opaque art (posters/backdrops) halves the memory per bitmap, so more fits in
     *    cache; Coil keeps ARGB_8888 for anything with transparency, so channel logos are untouched.
     *  - a dedicated HTTP client that identifies as OpenTV with generous timeouts: provider image
     *    hosts routinely refuse header-less requests (or trickle), and Coil's default client sends
     *    no User-Agent with 10s timeouts — a HEAD probe could pass while the real fetch 403s or
     *    times out, leaving a permanently bare card.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    // Bound fetch parallelism: every shelf/grid enqueues its visible cards plus
                    // the prefetch window, so a fast d-pad scroll could otherwise put 60+ image
                    // fetches in flight at once — each splitting the box's bandwidth and each
                    // holding decode buffers, which is what made posters trickle in slowly while
                    // the heap thrashed into GC-pressure ANRs on a 2GB box. 12 total / 4 per
                    // host keeps shelves filling fast without contention. This client serves
                    // images only, so API/EPG calls are unaffected.
                    .dispatcher(
                        okhttp3.Dispatcher().apply {
                            maxRequests = 12
                            maxRequestsPerHost = 4
                        },
                    )
                    .addNetworkInterceptor { chain ->
                        val request = chain.request()
                        val builder = request.newBuilder().header("User-Agent", "OpenTV")
                        // Plex artwork is authenticated with a header, not a query parameter.
                        //
                        // Every Plex poster failed to load while the token rode in the URL. That
                        // puts a live credential into Coil's disk-cache keys, into any log line that
                        // prints a poster URL, and - through the reverse proxy in front of the
                        // server - in front of whatever rules that proxy applies to query strings.
                        // As a header it is scoped to [plexServerHost] and to nowhere else, so it is
                        // never sent to a third-party image host either.
                        val host = plexServerHost
                        val token = plexAuthToken
                        if (request.url.host.equals(host ?: "", ignoreCase = true) && host != null) {
                            if (token != null) {
                                if (request.header("X-Plex-Token") == null) {
                                    builder.header("X-Plex-Token", token)
                                }
                                // Logged once per process, because "every Plex poster is broken"
                                // with no way to see whether the header was attached is exactly
                                // the guessing loop this is meant to end.
                                Log.i("OpenTV-Plex", "auth header attached for ${request.url.host}")
                            } else {
                                Log.w(
                                    "OpenTV-Plex",
                                    "Plex image requested for ${request.url.host} but no token is " +
                                        "cached yet - the shelf will have no artwork until the next sync",
                                )
                            }
                        }
                        chain.proceed(builder.build())
                    }
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // 8%, not 20%: on the boxes this app targets the ART heap cap is 384MB, and a
                    // 20% bitmap cache (~77MB) sat right where the guide's data and the video
                    // decoder needed room — it was a direct contributor to the OutOfMemoryError
                    // and GC-pressure ANR reports. 8% (~30MB) still covers a screenful of posters
                    // several times over; the disk cache handles the long tail.
                    .maxSizePercent(0.08)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    // 256MB of art on a box whose /data is already holding a multi-GB guide
                    // database. 64MB keeps the "already seen" hit rate without hogging the
                    // partition (and the trim work that comes with it).
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(false)
            .allowHardware(true)
            .allowRgb565(true)
            .build()

    // So notifications and any app-context resources use the chosen language too, not just the UI.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        val graph = ServiceLocator.get(this)
        val settings = graph.settings
        SyncWorker.schedule(this, settings.playlistRefreshHours.value)

        // When the normaliser has moved on since the catalogue was last processed, re-clean
        // the stored channels and re-run the guide matcher — locally, no re-download. This
        // is why a name-cleanup fix shows up on the next launch rather than the next 6-hour
        // sync, and it costs nothing when the version has not changed.
        // Cold-start reconciliation: a recording still marked "in progress" at process start is an
        // orphan from a killed capture. Done here, once per process — NOT when the Recordings screen
        // opens — so checking on a recording you just started never marks it interrupted.
        //
        // This one runs before the first frame, deliberately. It is a single UPDATE, and a process
        // that started *because* a booked recording is firing marks that recording in progress
        // within milliseconds of this line — so deferring it would let it run afterwards and label
        // an honest recording as interrupted.
        appScope.launch(Dispatchers.IO) { runCatching { graph.recordingRepository.failInterrupted() } }

        // Everything below only re-arms alarms that are already set, so it waits for the first frame
        // — and it should: the reminder loop and the recording re-arm each make one binder call per
        // booking, and running them during launch put them in direct competition with the guide's
        // first database query for the disk, at the one moment the process can least afford it.
        //
        // The wait is bounded, because a headless start — a scheduled recording firing while the app
        // is closed — never draws a frame, and these still have to run there.
        appScope.launch {
            withTimeoutOrNull(FIRST_FRAME_WAIT_MILLIS) { Startup.firstFrameDrawn.first { it } }

            // Resolve the graph AFTER the wait, not the captured one: when the Activity
            // finished while this coroutine parked, ServiceLocator.clear() already released
            // that Graph's player and DB. Running on a resurrected-by-reference old graph
            // would rebuild the whole object set it was supposed to free.
            val liveGraph = ServiceLocator.get(this@OpenTvApp)

            // A force-stop or app update drops exact alarms, and only a reboot is covered by the
            // boot receiver. Re-setting the same alarm is idempotent, so this keeps bookings alive.
            launch(Dispatchers.IO) { runCatching { liveGraph.recordingEngine.rearmScheduled() } }

            // Program reminders, for the same reason: setting an exact alarm for the same reminder
            // twice is harmless.
            launch(Dispatchers.IO) {
                runCatching {
                    val now = System.currentTimeMillis()
                    liveGraph.reminderRepository.deleteEndedBefore(now)
                    liveGraph.reminderRepository.upcoming(now).forEach {
                        app.opentv.reminders.ReminderScheduler.set(this@OpenTvApp, it.id, it.startUtcMillis)
                    }
                }
            }
        }

        appScope.launch {
            // Defer startup background maintenance so the live UI and DB render immediately
            // on cold start with zero I/O contention or CPU throttling.
            kotlinx.coroutines.delay(STARTUP_MAINTENANCE_DELAY_MILLIS)
            // Resolve the graph AFTER the delay, as with the first-frame block above: if the
            // Activity finished and clear() ran while this parked, the captured old Graph
            // (DB + HTTP clients) would be pinned alive for the whole sync it is about to
            // start, on a graph that is no longer the app's.
            val liveGraph = ServiceLocator.get(this@OpenTvApp)
            val prefs = getSharedPreferences("opentv", MODE_PRIVATE)
            val seen = prefs.getInt("normalizer_version", 0)
            if (seen < CatalogRepository.NORMALIZER_VERSION) {
                liveGraph.catalogRepository.renormalizeAll()
                prefs.edit().putInt("normalizer_version", CatalogRepository.NORMALIZER_VERSION).apply()
            }
            // Sweep rows belonging to playlists that no longer exist. A delete that overlapped an
            // import still writing leaves the tail of that import behind, owned by nobody — and since
            // the guide lists channels without joining the sources table, those rows never leave the
            // screen on their own. Cheap (five indexed deletes) and it runs on every process start.
            runCatching { liveGraph.catalogRepository.purgeOrphans() }
            // Runs on every launch. It is cheap when nothing is stale (feeds within their
            // refresh window are skipped), but it is what makes the free regional guide turn
            // itself on and download the first time — without waiting for the user to find
            // the refresh button. ensureFeeds + auto-enable-by-region + matcher all live here.
            // The refresh interval now comes from the user's setting instead of a hardcoded 6h.
            //
            // IfIdle, not syncAll: this is nobody's request, so when the user is already importing
            // a playlist (or the periodic worker is mid-guide) it stands down instead of queueing
            // behind that import and then starting its own catalogue-wide scan the moment the user
            // thinks they are finished. Nothing is lost — the next launch, or the worker, picks it
            // up — and the box never ends up running two of these at once.
            val epgIntervalMillis = java.util.concurrent.TimeUnit.HOURS.toMillis(
                settings.epgRefreshHours.value.toLong().coerceAtLeast(1)
            )
            liveGraph.epgRepository.syncAllIfIdle(
                System.currentTimeMillis(),
                force = false,
                refreshIntervalMillis = epgIntervalMillis,
            )
        }
    }
}
