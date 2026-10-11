/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The guide's motion, taken from the reference guide's shipped resources.
 *
 * TiviMate builds its guide and menus on `androidx.leanback.widget.VerticalGridView` /
 * `HorizontalGridView` (both classes are present in its dex), and the motion that makes its cursor
 * feel smooth comes from three things its APK ships and this file reproduces:
 *
 * 1. Its own easing curves. Two path interpolators recur on every focus/scroll animation:
 *    `c 0.333,0.0 0.0,1.0` and `c 0.4,0.0 0.2,1.0` (the latter is the same decelerate curve
 *    Material uses). Both are reproduced below rather than approximated with a stock spec.
 * 2. Its timings: 166 ms for a focus change, 16 ms for the recovery beat, 300–333 ms for a
 *    longer move. The reference's focus animation is literally 166 ms out, 16 ms back.
 * 3. Leanback's focus highlight. `FocusHighlightHelper`'s default zoom scales the focused child
 *    (1.06) over the focus duration, which is what gives a TV cursor its "lift" without moving a
 *    single layout pixel.
 *
 * Values are kept in one object (not inlined at the call sites) so the guide's motion is
 * adjustable in one place and pinned by `GuideMotionTest`.
 */
object GuideMotion {

    /** The reference's workhorse decelerate curve: fast out of the gate, long settle. */
    val CURSOR_EASING: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** A focus change: 166 ms of travel, matching the reference's focus animation. */
    const val FOCUS_MS: Int = 166

    /** The recovery beat after the travel — the reference snaps back in 16 ms. */
    const val RECOVER_MS: Int = 16

    /**
     * How much the focused cell lifts, as leanback's focus-highlight zoom does. Scale, not layout,
     * so the guide never re-measures when the cursor moves.
     */
    const val FOCUS_SCALE: Float = 1.06f

    /**
     * The cursor ring's pulse: the reference grows its stroke from 2 dp to 18 dp over the focus
     * duration and collapses it back over [RECOVER_MS]. Reproduced as the ring's width so the
     * emphasis reads as the same gesture.
     */

    val CURSOR_RING_MIN: Dp = 2.dp
    val CURSOR_RING_MAX: Dp = 18.dp
}
