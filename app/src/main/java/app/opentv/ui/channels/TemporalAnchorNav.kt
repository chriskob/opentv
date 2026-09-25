/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import app.opentv.data.model.Programme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Temporal Anchor Focus Engine: Finds the target programme in [targetChannelPrograms] that best
 * matches the virtual [temporalAnchorMillis].
 *
 * The anchor is a half-hour *column* (see [halfHourColumnStart]), not a programme midpoint. That
 * distinction matters: a midpoint drifts into the middle of a long block, so moving up/down would
 * re-select a later column and the cursor would slide right one row at a time. A column stays put.
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

fun getHorizontalTargetProgram(
    programmes: List<Programme>,
    currentProgramme: Programme?,
    currentColumnMillis: Long,
    isRight: Boolean,
): Programme? {
    if (programmes.isEmpty()) return null

    val halfHourMs = 30 * 60 * 1000L
    val candidate = currentColumnMillis + if (isRight) halfHourMs else -halfHourMs
    val atCandidate = programmes.firstOrNull { programme ->
        programme.startUtcMillis <= candidate && programme.endUtcMillis > candidate
    }

    if (!isRight) {
        return atCandidate ?: programmes
            .asSequence()
            .filter { it.endUtcMillis <= candidate }
            .maxByOrNull { it.endUtcMillis }
    }

    val current = currentProgramme
    if (current != null && atCandidate?.id == current.id) {
        val remaining = current.endUtcMillis - candidate
        if (remaining > 0L && remaining < halfHourMs) {
            return programmes
                .asSequence()
                .filter { it.startUtcMillis >= current.endUtcMillis }
                .minByOrNull { it.startUtcMillis }
                ?: atCandidate
        }
    }

    return atCandidate ?: programmes
        .asSequence()
        .filter { it.startUtcMillis >= candidate }
        .minByOrNull { it.startUtcMillis }
}

/**
 * Snaps a timestamp down to the half-hour column that contains it — the guide's cursor is a
 * :00/:30 column, so every anchor is expressed as one. Programme midpoints are never used; a
 * long block would otherwise pull the cursor into its centre and drift the column as the user
 * moves up and down.
 */
fun halfHourColumnStart(millis: Long): Long {
    val halfHourMs = 30 * 60 * 1000L
    return millis - millis % halfHourMs
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

fun calculateSnappedScrollOffsetPx(
    windowStartMillis: Long,
    columnStartMillis: Long,
    minuteDp: Float,
    density: Float,
    maxValue: Int = Int.MAX_VALUE,
): Int {
    val offsetMinutes = ((columnStartMillis - windowStartMillis) / 60000.0).coerceAtLeast(0.0)
    val targetDp = offsetMinutes * minuteDp
    return (targetDp * density).roundToInt().coerceIn(0, maxValue.coerceAtLeast(0))
}

