/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.opentv.core.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a booked recording is due (or after a reboot). On the due alarm it starts the
 * capture service for that recording; on boot it re-arms every still-future booking, since alarms
 * don't survive a power cycle.
 */
class RecordingAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> rescheduleAll(context)
            RecordingScheduler.ACTION_START -> {
                val id = RecordingScheduler.recordingIdFrom(intent)
                if (id > 0) RecordingService.start(context, id)
            }
        }
    }

    private fun rescheduleAll(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.get(context).recordingRepository
                val now = System.currentTimeMillis()
                repo.scheduled().forEach { rec ->
                    if (rec.scheduledStartMillis > now) {
                        RecordingScheduler.set(context, rec.id, rec.scheduledStartMillis)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
