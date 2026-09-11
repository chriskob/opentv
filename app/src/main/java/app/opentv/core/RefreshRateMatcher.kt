/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

/**
 * Chooses the display mode that matches a stream's frame rate — the difference between live
 * television and a slideshow that dips once a second.
 *
 * Most IPTV in Europe is 25 or 50 fps (from DVB's 50 Hz heritage), North America is 29.97/59.94,
 * and film is 23.976/24 — while an Android TV box sits on whatever mode the TV negotiated at boot,
 * almost always 60 Hz. A 25 fps stream on a 60 Hz display cannot be shown evenly: the picture has to
 * advance in a pattern of 2, 2, 3 display refreshes per frame instead of a steady 2, and that 3 is
 * visible as a hitch every second — most obviously on a scrolling news ticker or a panning camera.
 * Asking the display for a mode the frame rate divides *exactly* removes it: 50 Hz for 25 fps, 100 Hz
 * for 50 fps, 24 Hz for a film.
 *
 * Deliberately pure. [Mode] mirrors android.view.Display.Mode without the Android type, so the rule
 * is unit-tested rather than eyeballed on the one box that happens to be plugged in. [DisplayRefresh]
 * is the half that touches the window.
 *
 * Matching is by whole multiple rather than by nearest number. A 23.976 fps film on a 120 Hz panel is
 * exact — five refreshes per frame, which is the reason such modes exist — while 24 fps on a 60 Hz
 * panel is not, and no amount of rounding makes it so.
 */
object RefreshRateMatcher {

    /** One display mode, without the Android dependency. */
    data class Mode(val id: Int, val refreshRate: Float)

    /**
     * The mode to switch to, or null to leave the display alone.
     *
     * Null is the answer whenever no mode divides evenly into the content rate. Forcing the nearest
     * number instead would trade one kind of judder for another *and* blank the screen for a beat
     * while the HDMI link re-syncs, which is a worse experience than doing nothing.
     */
    fun pick(contentFps: Float, modes: List<Mode>): Mode? {
        if (contentFps <= 0f || contentFps.isNaN() || modes.isEmpty()) return null

        var best: Mode? = null
        var bestMultiple = 0
        for (mode in modes) {
            if (mode.refreshRate <= 0f) continue
            val multiple = evenMultiple(contentFps, mode.refreshRate) ?: continue
            val better = best == null ||
                multiple < bestMultiple ||
                (multiple == bestMultiple && mode.refreshRate < best.refreshRate)
            if (better) {
                best = mode
                bestMultiple = multiple
            }
        }
        return best
    }

    /**
     * How many display refreshes each content frame gets, or null when there is no whole number.
     *
     * The 1% tolerance is for the NTSC rates: 23.976, 29.97 and 59.94 are deliberately not round, and
     * 23.976 fps really is exactly one refresh per frame on a 23.976 Hz mode. A ratio below one is
     * rejected outright, so a 50 fps stream is never handed a 30 Hz mode just because it is nearest.
     */
    private fun evenMultiple(contentFps: Float, refreshRate: Float): Int? {
        val ratio = refreshRate / contentFps
        val whole = Math.round(ratio)
        if (whole < 1) return null
        if (Math.abs(ratio - whole) > NTSC_TOLERANCE * whole) return null
        return whole
    }

    /** Relative slack for the off-by-1/1000 broadcast rates. Generous enough for them, far too tight
     * for a genuinely mismatched pair (24 fps on a 25 Hz mode is 4% out and is rejected). */
    private const val NTSC_TOLERANCE = 0.011f
}
