/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.app.Activity

/**
 * Asks the window for a display mode that matches the stream, using [RefreshRateMatcher] to choose it.
 *
 * The Activity owns the window — only it can change layout parameters — but the player is what knows
 * the stream's frame rate, so this sits between the two the same way [PipState] does for
 * picture-in-picture: the player reports the rate, the window is what actually changes.
 *
 * `preferredDisplayModeId` is the Android TV route, and it is a *request*: a TV framework honours it by
 * switching the HDMI output (which is why the screen blanks for a beat when a 50 Hz channel starts),
 * and a box that ignores it simply stays put. Zero means "no preference", which is how the display is
 * handed back when the player closes — so a box is never left stuck on a mode chosen by a stream that
 * stopped playing ten minutes ago.
 */
object DisplayRefresh {

    /** The mode id currently asked for; 0 means no preference, i.e. the system's own choice. */
    @Volatile
    private var requestedModeId: Int = 0

    /**
     * Switch the display to suit [contentFps], if the setting is on and a mode fits.
     *
     * Called per stream, so it must be cheap and must not thrash: it returns immediately when the
     * display is already on the right mode, because re-asserting the same mode still re-syncs the
     * HDMI link and shows up as a flicker between every pair of same-rate channels.
     */
    @Suppress("DEPRECATION")
    fun match(activity: Activity, contentFps: Float) {
        // A picture-in-picture window is not worth a full display re-sync for.
        if (PipState.inPip.value) return
        val display = activity.windowManager?.defaultDisplay ?: return
        val modes = display.supportedModes?.map {
            RefreshRateMatcher.Mode(it.modeId, it.refreshRate)
        } ?: return

        val target = RefreshRateMatcher.pick(contentFps, modes) ?: return
        if (display.mode?.modeId == target.id) {
            requestedModeId = target.id
            return
        }
        apply(activity, target.id)
    }

    /** Hand the display back to the system. Safe to call when nothing was ever requested. */
    fun restore(activity: Activity) {
        if (requestedModeId == 0) return
        apply(activity, 0)
    }

    private fun apply(activity: Activity, modeId: Int) {
        requestedModeId = modeId
        activity.window.attributes = activity.window.attributes.apply {
            preferredDisplayModeId = modeId
        }
    }
}
