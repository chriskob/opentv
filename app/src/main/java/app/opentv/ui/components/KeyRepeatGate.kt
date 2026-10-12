/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.components

/**
 * Coalesces auto-repeat key presses to at most one action per [intervalMillis].
 *
 * A held TV d-pad repeats every 30-50 ms, but this box needs 100-500 ms per guide step while a
 * guide sync is writing the database. Every ungated repeat used to run the whole focus+scroll
 * pipeline again while the previous step's focus change was still unresolved — which is how the
 * "focus system is invalidated" crash got dispatched (see GuideInputGuardsTest), on top of
 * flooding the queue. One step per window keeps the cursor moving at a readable cadence and lets
 * each step's frame actually land before the next one starts.
 *
 * Pure time logic behind an injected [clock] so the coalescing is unit-testable; production uses
 * the monotonic uptime clock.
 */
class KeyRepeatGate(
    private val intervalMillis: Long = 60L,
    private val clock: () -> Long = { android.os.SystemClock.uptimeMillis() },
) {
    private var lastAccepted: Long? = null

    /** True when this press may run; false means "swallow it, one just ran". */
    fun accept(nowMillis: Long = clock()): Boolean {
        val last = lastAccepted
        if (last != null && nowMillis - last < intervalMillis) return false
        lastAccepted = nowMillis
        return true
    }
}
