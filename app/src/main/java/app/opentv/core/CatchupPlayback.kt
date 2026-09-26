/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

/**
 * The archive programme currently playing through the shared live player.
 *
 * Catch-up is not a separate screen: selecting a past programme in the guide swaps the shared
 * player onto the provider's timeshift stream (`isLive = false`), exactly the way a channel change
 * swaps the live stream. This holder carries the metadata the OSD needs to label that playback and
 * lets the guide keep the viewer where they scrubbed to when they back out.
 */
data class CatchupPlayback(
    val channelId: Long,
    val channelName: String,
    val programmeTitle: String,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val url: String,
    val userAgent: String,
) {
    fun reachedLiveEdge(
        positionMillis: Long,
        nowMillis: Long,
        correctionMillis: Long = 0L,
        toleranceMillis: Long = LIVE_EDGE_TOLERANCE_MILLIS,
    ): Boolean {
        if (endUtcMillis <= nowMillis) return false
        val livePosition = nowMillis - (startUtcMillis + correctionMillis)
        return positionMillis >= livePosition - toleranceMillis
    }

    companion object {
        const val LIVE_EDGE_TOLERANCE_MILLIS = 2_000L
    }
}
