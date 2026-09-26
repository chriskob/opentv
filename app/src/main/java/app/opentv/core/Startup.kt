/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.os.SystemClock
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
        mark("firstFrame")
        _firstFrameDrawn.value = true
    }

    private val processStartUptime: Long = runCatching {
        android.os.Process.getStartUptimeMillis()
    }.getOrElse { SystemClock.elapsedRealtime() }
    private val marks = LinkedHashMap<String, Long>()

    /**
     * Records a one-shot boot milestone. Later calls with the same name are ignored, so a
     * milestone that genuinely only happens once (playback starting, sources arriving) is logged
     * once rather than on every re-tune.
     */
    @Synchronized
    fun mark(name: String) {
        if (marks.containsKey(name)) return
        val at = SystemClock.elapsedRealtime() - processStartUptime
        marks[name] = at
        android.util.Log.i(BOOT_TAG, "$name=+${at}ms")
    }

    /**
     * Logs an event every time it happens, keeping the first under [name]. Route entries need
     * this: the first entry is the boot number worth reading, but a later one is the only proof
     * that a navigation actually landed somewhere rather than merely being requested.
     */
    @Synchronized
    fun markEach(name: String) {
        val at = SystemClock.elapsedRealtime() - processStartUptime
        val n = marks[name]
        marks[name] = n ?: at
        android.util.Log.i(BOOT_TAG, if (n == null) "$name=+${at}ms" else "$name=+${at}ms (repeat)")
    }

    @Synchronized
    fun trace(): String =
        marks.entries.joinToString(" ") { "${it.key}=${it.value}ms" }

    const val BOOT_TAG = "OpenTV-Boot"
}
