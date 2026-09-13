/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The gate that lets one heavy job run at a time.
 *
 * The app has three jobs that each saturate a Fire Stick on their own: the live catalogue import
 * (tens of thousands of channels), the movies/series import (the same again in titles), and the
 * guide sync — whose last step joins every stored channel against the programmes that arrived.
 * They are started from three unrelated places: the periodic worker, the app's own launch-time
 * refresh, and a playlist add from the phone portal. Nothing coordinated them, so adding a
 * playlist shortly after opening the app ran a catalogue import, a VOD import and a matcher over
 * the whole channel list at the same time — on one SQLite writer and a handful of slow cores. The
 * box does not fall over, it just becomes unusable, which is what "it bogs the stick down" is.
 *
 * Waiting is the right answer for a job the user asked for: the dashboard says what it is waiting
 * for (see [isBusy]) rather than reporting progress that is not moving. For a job nobody asked for
 * — the opportunistic guide refresh on launch — there is [runIfIdle], which yields instead of
 * queueing, because the next launch or the periodic worker will pick it up anyway.
 */
object HeavyWork {
    private val mutex = Mutex()

    /**
     * True while a job is inside the gate.
     *
     * Advisory: a caller that wants to explain a wait reads this before it starts, and the lock
     * may well be free by the time it asks. It is never used to decide whether work is needed.
     */
    val isBusy: Boolean get() = mutex.isLocked

    /** Runs [block] holding the gate, waiting for whatever is inside to finish first. */
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * Runs [block] only if the gate is free this instant, returning null when a job already holds
     * it. For work that is worth doing but not worth waiting for.
     */
    suspend fun <T> runIfIdle(block: suspend () -> T): T? {
        if (!mutex.tryLock()) return null
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
