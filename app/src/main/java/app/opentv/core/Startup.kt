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
 * A one-way flag saying the first frame is on screen.
 *
 * Startup maintenance that touches the alarm manager or the database waits for it, so it stops
 * competing with the guide's first query for the disk at the coldest moment of the process's life.
 * It is a signal rather than a timing guess: the work begins the instant the UI is up, however fast
 * or slow that turned out to be.
 *
 * A headless start — a scheduled recording firing while the app is closed — never draws a frame, so
 * anything waiting on this must wait with a timeout rather than forever.
 */
object Startup {
    private val _firstFrameDrawn = MutableStateFlow(false)

    /** True once the first frame has been drawn. */
    val firstFrameDrawn: StateFlow<Boolean> = _firstFrameDrawn.asStateFlow()

    fun noteFirstFrameDrawn() {
        _firstFrameDrawn.value = true
    }
}
