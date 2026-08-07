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
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenTvApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // So notifications and any app-context resources use the chosen language too, not just the UI.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        val graph = ServiceLocator.get(this)
        SyncWorker.schedule(this)

        // When the normaliser has moved on since the catalogue was last processed, re-clean
        // the stored channels and re-run the guide matcher — locally, no re-download. This
        // is why a name-cleanup fix shows up on the next launch rather than the next 6-hour
        // sync, and it costs nothing when the version has not changed.
        // Cold-start reconciliation: a recording still marked "in progress" at process start is an
        // orphan from a killed capture. Done here, once per process — NOT when the Recordings screen
        // opens — so checking on a recording you just started never marks it interrupted.
        appScope.launch { runCatching { graph.recordingRepository.failInterrupted() } }

        // Re-arm scheduled recordings on every launch, for the same reason as the reminders below:
        // a force-stop or app update drops their exact alarms and only a reboot is covered by the
        // boot receiver. Re-setting the same alarm is idempotent, so this quietly keeps bookings alive.
        appScope.launch { runCatching { graph.recordingEngine.rearmScheduled() } }

        // Re-arm programme reminders on every launch. Alarms are lost on a force-stop or app
        // update (not just a reboot, which the boot receiver already covers), and re-setting an
        // exact alarm for the same reminder is idempotent — so this quietly keeps bells alive.
        appScope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                graph.reminderRepository.deleteEndedBefore(now)
                graph.reminderRepository.upcoming(now).forEach {
                    app.opentv.reminders.ReminderScheduler.set(this@OpenTvApp, it.id, it.startUtcMillis)
                }
            }
        }

        // Free, server-less "cloud" sync through the user's own NAS, if they've opted in. Writes
        // this device's bundle to the shared folder and merges in every other device's — favourites,
        // watch history and NAS recordings. Fire-and-forget and fully guarded: a blank or unreachable
        // NAS returns a result rather than throwing, so a bad launch never costs the user anything.
        if (graph.settings.nasAutoSync.value) {
            appScope.launch { runCatching { app.opentv.sync.NasSync(graph).sync() } }
        }

        appScope.launch {
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
            graph.epgRepository.syncAll(System.currentTimeMillis(), force = false)
        }
    }
}
