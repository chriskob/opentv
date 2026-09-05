/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import app.opentv.data.model.Programme
import kotlin.math.abs

/**
 * Temporal Anchor Focus Engine: Finds the target programme in [targetChannelPrograms] that best
 * matches the virtual [temporalAnchorMillis].
 *
 * Implements the specification:
 * 1. Find programme where start <= anchor < end.
 * 2. If anchor falls into an unprogrammed gap or beyond the schedule boundary,
 *    fall back to the programme whose boundary/midpoint is closest to the anchor.
 */
fun getVerticalTargetProgram(
    targetChannelPrograms: List<Programme>,
    temporalAnchorMillis: Long,
): Programme? {
    if (targetChannelPrograms.isEmpty()) return null

    // 1. Exact temporal match
    val match = targetChannelPrograms.firstOrNull { p ->
        p.startUtcMillis <= temporalAnchorMillis && p.endUtcMillis > temporalAnchorMillis
    }
    if (match != null) return match

    // 2. Fallback to the closest boundary if anchor sits in an unprogrammed gap
    return targetChannelPrograms.minByOrNull { p ->
        val mid = (p.startUtcMillis + p.endUtcMillis) / 2L
        abs(mid - temporalAnchorMillis)
    } ?: targetChannelPrograms.first()
}

/**
 * Calculates the midpoint timestamp of a programme.
 */
fun computeProgrammeMidpoint(programme: Programme): Long {
    return (programme.startUtcMillis + programme.endUtcMillis) / 2L
}

/**
 * Calculates the mounted frame start timestamp for the guide.
 *
 * Requirements:
 * 1. "whatever time it currently is is the time frame that it should open to."
 * 2. "show the full program time box and just make the indicator line move to represent the time."
 *
 * If the current live programme started within the last 60 minutes, align to the half-hour slot
 * of its start time so the entire programme time box is shown starting cleanly at the grid boundary.
 * Otherwise, align to the current half-hour slot.
 */
fun calculateMountedFrameStartTime(
    nowMillis: Long,
    liveProgrammeStartMillis: Long? = null,
): Long {
    val halfHourMs = 30 * 60 * 1000L
    return nowMillis - (nowMillis % halfHourMs)
}

/**
 * Calculates the horizontal scroll offset in pixels to mount the guide at [frameStartMillis].
 */
fun calculateInitialScrollOffsetPx(
    windowStartMillis: Long,
    frameStartMillis: Long,
    minuteDp: Float,
    density: Float,
): Int {
    val offsetMinutes = ((frameStartMillis - windowStartMillis) / 60000.0).coerceAtLeast(0.0)
    val targetDp = (offsetMinutes * minuteDp).coerceAtLeast(0.0)
    return (targetDp * density).toInt()
}

