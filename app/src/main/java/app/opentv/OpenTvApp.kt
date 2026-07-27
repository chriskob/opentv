/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import android.app.Application
import app.opentv.core.ServiceLocator
import app.opentv.data.work.SyncWorker

class OpenTvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.get(this)
        SyncWorker.schedule(this)
    }
}
