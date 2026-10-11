/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

/**
 * What happens when the viewer presses OK on a guide cell.
 *
 * [PLAY_LIVE] for a programme that has not finished airing, [LAUNCH_CATCHUP] for a finished one on
 * a channel that can serve an archive stream. A finished programme on a channel without archive
 * support is [INERT]: it is still drawn (the timeline keeps reading as a timeline) but pressing OK
 * does nothing and reports nothing.
 */
internal enum class GuideCellAction {
    PLAY_LIVE,
    LAUNCH_CATCHUP,
    INERT,
}

/**
 * The guide cell's action, decided in one place so the grid, the badges and the tests agree.
 *
 * [archiveCapable] is the per-channel answer from `CatchupResolver` (the provider's archive flag,
 * a catch-up template, or an Xtream portal), never a guess from the channel's name.
 */
internal fun resolveGuideCellAction(
    isPast: Boolean,
    archiveCapable: Boolean,
): GuideCellAction = when {
    !isPast -> GuideCellAction.PLAY_LIVE
    archiveCapable -> GuideCellAction.LAUNCH_CATCHUP
    else -> GuideCellAction.INERT
}

/**
 * Whether a cell may take the cursor.
 *
 * Only the [GuideCellAction.INERT] case is gated, and by the viewer's choice: with "show previous
 * programmes without catch-up" off, a finished programme with no archive behind it is a dead end —
 * dim it and let the cursor step over it rather than land somewhere that cannot be played. With the
 * setting on it behaves like any other cell: focusable, inspectable, still inert on OK.
 */
internal fun guideCellTakesFocus(
    action: GuideCellAction,
    showPreviousWithoutCatchup: Boolean,
): Boolean = action != GuideCellAction.INERT || showPreviousWithoutCatchup
