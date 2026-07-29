/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import android.app.Application
import app.opentv.core.ServiceLocator
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OpenTvApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val graph = ServiceLocator.get(this)
        SyncWorker.schedule(this)

        // When the normaliser has moved on since the catalogue was last processed, re-clean
        // the stored channels and re-run the guide matcher — locally, no re-download. This
        // is why a name-cleanup fix shows up on the next launch rather than the next 6-hour
        // sync, and it costs nothing when the version has not changed.
        appScope.launch {
            val prefs = getSharedPreferences("opentv", MODE_PRIVATE)
            val seen = prefs.getInt("normalizer_version", 0)
            if (seen < CatalogRepository.NORMALIZER_VERSION) {
                graph.catalogRepository.renormalizeAll()
                graph.epgRepository.runMatcher()
                prefs.edit().putInt("normalizer_version", CatalogRepository.NORMALIZER_VERSION).apply()
            }
        }
    }
}
