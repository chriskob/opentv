/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A one-shot channel-to-play request, set from outside the Compose tree (a reminder notification
 * tap) and consumed by the nav graph. Kept deliberately tiny: a single nullable id the app watches
 * and clears once it has navigated, so a stale request never re-fires on the next launch.
 */
object PlayRequests {
    private val _channelId = MutableStateFlow<Long?>(null)
    val channelId: StateFlow<Long?> = _channelId.asStateFlow()

    private val _fullScreenRequest = MutableStateFlow<Long?>(null)
    val fullScreenRequest: StateFlow<Long?> = _fullScreenRequest.asStateFlow()

    fun request(id: Long) {
        if (id != 0L) _channelId.value = id
    }

    fun requestFullScreen() {
        _fullScreenRequest.value = System.currentTimeMillis()
    }

    fun consume() {
        _channelId.value = null
    }

    fun consumeFullScreen() {
        _fullScreenRequest.value = null
    }
}
