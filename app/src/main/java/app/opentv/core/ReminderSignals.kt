/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ReminderSignal(
    val reminderId: Long,
    val channelId: Long,
    val channelName: String,
    val programmeTitle: String,
    val autoTune: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

/**
 * In-memory signal bus for programme reminders and auto-tune events.
 *
 * When an alarm fires in [app.opentv.reminders.ReminderAlarmReceiver], it emits a signal here so that running screens
 * (guide, player, settings) can immediately auto-tune or show an in-app reminder prompt.
 */
object ReminderSignals {
    private val _signals = MutableSharedFlow<ReminderSignal>(extraBufferCapacity = 16)
    val signals: SharedFlow<ReminderSignal> = _signals.asSharedFlow()

    fun emit(signal: ReminderSignal) {
        _signals.tryEmit(signal)
    }
}
