/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.opentv.MainActivity
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a booked reminder is due (or after a reboot). On the due alarm it raises a
 * notification for the programme — tapping jumps to the channel — and, for an auto-tune reminder,
 * directly launches the activity so a TV box switches over on its own without relying on
 * full-screen intents (which Android TV often silently ignores).
 * On boot it re-arms every still-future reminder, since alarms do not survive a power cycle.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> rescheduleAll(context)
            ReminderScheduler.ACTION_REMIND -> {
                val id = ReminderScheduler.reminderIdFrom(intent)
                if (id > 0) fire(context, id)
            }
        }
    }

    private fun fire(context: Context, id: Long) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.get(context).reminderRepository
                val reminder = repo.byId(id) ?: return@launch
                val appContext = context.applicationContext

                // P0: For auto-tune reminders, directly start the activity on Android TV.
                // setFullScreenIntent is silently ignored on many TV devices; launching the
                // activity directly ensures the channel actually switches.
                if (reminder.autoTune) {
                    val launch = Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(MainActivity.EXTRA_PLAY_CHANNEL, reminder.channelId)
                    }
                    appContext.startActivity(launch)
                }

                // Always also post a notification so the user sees what happened.
                notify(appContext, reminder)
                repo.markFired(id)
            } finally {
                pending.finish()
            }
        }
    }

    private fun rescheduleAll(context: Context) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = ServiceLocator.get(context).reminderRepository
                val now = System.currentTimeMillis()
                repo.upcoming(now).forEach { r -> ReminderScheduler.set(context, r.id, r.startUtcMillis) }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, reminder: Reminder) {
        createChannel(context)

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_PLAY_CHANNEL, reminder.channelId)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(reminder.title)
            .setContentText(context.getString(R.string.reminder_starting_now, reminder.channelName))
            .setSmallIcon(R.drawable.ic_opentv_logo)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .addAction(0, context.getString(R.string.reminder_watch_now), contentPi)

        // For non-auto-tune reminders, still attach a full-screen intent as a best-effort
        // enhancement on devices that support it. On TV where it's ignored, the user taps
        // the notification manually — same as TiviMate's behaviour.
        if (reminder.autoTune) {
            builder.setFullScreenIntent(contentPi, true)
        }

        NotificationManagerCompat.from(context)
            .notify(NOTIFICATION_BASE + reminder.id.toInt(), builder.build() as Notification)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.reminder_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply { description = context.getString(R.string.reminder_channel_desc) },
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "reminders"
        private const val NOTIFICATION_BASE = 5000
    }
}