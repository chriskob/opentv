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
 * support is [INERT] - drawn dimmed and focusable, but pressing OK does nothing - unless the
 * viewer has turned "show previous programmes without catch-up" off, in which case it is [HIDE] and
 * is not composed at all.
 */
internal enum class GuideCellAction {
    PLAY_LIVE,
    LAUNCH_CATCHUP,
    INERT,
    HIDE,
}

/**
 * The guide cell's affordance, decided in one place so the grid, the badges and the tests agree.
 *
 * [archiveCapable] is the per-channel answer from `CatchupResolver` (the provider's archive flag,
 * a catch-up template, or an Xtream portal), never a guess from the channel's name.
 */
internal fun resolveGuideCellAction(
    isPast: Boolean,
    archiveCapable: Boolean,
    showPreviousWithoutCatchup: Boolean,
): GuideCellAction = when {
    !isPast -> GuideCellAction.PLAY_LIVE
    archiveCapable -> GuideCellAction.LAUNCH_CATCHUP
    showPreviousWithoutCatchup -> GuideCellAction.INERT
    else -> GuideCellAction.HIDE
}
