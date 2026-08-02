/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Books a recording to start at a wall-clock time. Uses an exact alarm so a programme that starts
 * in three hours records on the dot even if the box has dozed off in the meantime — and exact
 * alarms are on Android's short allow-list for starting a foreground service from the background,
 * which is exactly what firing a scheduled capture needs.
 */
object RecordingScheduler {

    const val ACTION_START = "app.opentv.recording.ALARM_START"
    private const val EXTRA_ID = "recordingId"

    fun set(context: Context, recordingId: Long, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, recordingId)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (_: SecurityException) {
            // Exact-alarm permission was revoked — fall back to an inexact wake, still better
            // than nothing (it may fire a little late but the capture still runs).
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancel(context: Context, recordingId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, recordingId))
    }

    fun recordingIdFrom(intent: Intent): Long = intent.getLongExtra(EXTRA_ID, -1L)

    private fun intentFor(context: Context, recordingId: Long): Intent =
        Intent(context, RecordingAlarmReceiver::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_ID, recordingId)
        }

    private fun pendingIntent(context: Context, recordingId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            recordingId.toInt(),
            intentFor(context, recordingId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
