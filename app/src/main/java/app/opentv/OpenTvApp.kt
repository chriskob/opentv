/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import android.app.Application
import android.content.Context
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

class OpenTvApp : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The app-wide Coil loader, tuned for a poster-and-logo heavy UI on a low-end TV box. The
     * default loader keeps a small memory cache and no disk cache, so scrolling back through a
     * shelf — or reopening Movies — re-downloads and re-decodes every image. Here:
     *  - a generous memory cache and a 256 MB disk cache mean art you've already seen paints from
     *    cache, instantly, instead of hitting the network;
     *  - no crossfade — an immediate swap reads as snappier on a d-pad grid than a fade, and skips
     *    a frame of blending per image;
     *  - RGB_565 for opaque art (posters/backdrops) halves the memory per bitmap, so more fits in
     *    cache; Coil keeps ARGB_8888 for anything with transparency, so channel logos are untouched.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
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

            // A force-stop or app update drops exact alarms, and only a reboot is covered by the
            // boot receiver. Re-setting the same alarm is idempotent, so this keeps bookings alive.
            launch(Dispatchers.IO) { runCatching { graph.recordingEngine.rearmScheduled() } }

            // Program reminders, for the same reason: setting an exact alarm for the same reminder
            // twice is harmless.
            launch(Dispatchers.IO) {
                runCatching {
                    val now = System.currentTimeMillis()
                    graph.reminderRepository.deleteEndedBefore(now)
                    graph.reminderRepository.upcoming(now).forEach {
                        app.opentv.reminders.ReminderScheduler.set(this@OpenTvApp, it.id, it.startUtcMillis)
                    }
                }
            }
        }

        appScope.launch {
            // Defer startup background maintenance so the live UI and DB render immediately
            // on cold start with zero I/O contention or CPU throttling.
            kotlinx.coroutines.delay(30000)
            val prefs = getSharedPreferences("opentv", MODE_PRIVATE)
            val seen = prefs.getInt("normalizer_version", 0)
            if (seen < CatalogRepository.NORMALIZER_VERSION) {
                graph.catalogRepository.renormalizeAll()
                prefs.edit().putInt("normalizer_version", CatalogRepository.NORMALIZER_VERSION).apply()
            }
            // Runs on every launch. It is cheap when nothing is stale (feeds within their
            // refresh window are skipped), but it is what makes the free regional guide turn
            // itself on and download the first time — without waiting for the user to find
            // the refresh button. ensureFeeds + auto-enable-by-region + matcher all live here.
            // The refresh interval now comes from the user's setting instead of a hardcoded 6h.
            val epgIntervalMillis = java.util.concurrent.TimeUnit.HOURS.toMillis(
                settings.epgRefreshHours.value.toLong().coerceAtLeast(1)
            )
            graph.epgRepository.syncAll(
                System.currentTimeMillis(),
                force = false,
                refreshIntervalMillis = epgIntervalMillis,
            )
        }
    }
}
