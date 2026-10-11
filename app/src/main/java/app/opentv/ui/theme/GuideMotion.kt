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
import kotlin.math.PI
import kotlin.math.cos

/**
 * The guide's motion.
 *
 * Two sources, kept separate because they do different jobs and sound different if swapped:
 *
 * **The engine.** TiviMate's guide and menus are `androidx.leanback.widget.VerticalGridView` /
 * `HorizontalGridView` (both classes are present in its dex, as is `GridLayoutManager`). From
 * leanback's own Apache-2.0 source (`FocusHighlightHelper.java`): the focus highlight zooms the
 * focused item over **150 ms** on an `AccelerateDecelerateInterpolator`, scaling the child rather
 * than re-laying it out. That is the "lift" a TV cursor has, and it is reproduced faithfully here —
 * [FOCUS_EASING] is Android's accelerate-decelerate curve, not an approximation of it.
 *
 * **The app's own flourish.** The reference ships its own cursor animation: a stroke grows from
 * 2 dp to 18 dp over 166 ms on its decelerate curve and collapses in a 16 ms beat. That pulse is
 * [CURSOR_EASING] with [PULSE_MS]/[RECOVER_MS], and it is deliberately not the same curve as the
 * focus lift.
 *
 * Values live in one object (not inlined at call sites) so the guide's motion is adjustable in one
 * place and pinned by `GuideMotionTest`.
 */
object GuideMotion {

    /** A focus change: 166 ms of travel, the reference's own focus animation. */
    const val PULSE_MS: Int = 166

    /** The recovery beat after the travel — the reference snaps back in 16 ms. */
    const val RECOVER_MS: Int = 16

    /**
     * The focus highlight's travel, straight from leanback's `BrowseItemFocusHighlight`.
     * 150 ms on an accelerate-decelerate curve: slow in, fast through the middle, slow out — which
     * is what keeps a cursor lift from feeling like a snap.
     */
    const val FOCUS_HIGHLIGHT_MS: Int = 150

    /** Android's `AccelerateDecelerateInterpolator`: `(cos((t + 1) * PI) + 1) / 2`. */
    val FOCUS_EASING: Easing = Easing { fraction ->
        ((cos((fraction + 1f) * PI) + 1f) / 2f).toFloat()
    }

    /** The reference's own decelerate curve: fast out of the gate, long settle. */
    val CURSOR_EASING: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /**
     * How much the focused cell lifts, as leanback's `FocusHighlightHelper` zoom does. Scale, not
     * layout, so moving the cursor never re-measures the grid — the reason a 20-programme row stays
     * smooth. Leanback's factor comes from its `lb_focus_zoom_factor_*` fractions (small / xsmall /
     * medium / large, chosen by the presenter); 1.06 is the medium case this guide's cell size maps
     * to.
     */
    const val FOCUS_SCALE: Float = 1.06f

    /**
     * The cursor ring's pulse: the reference grows its stroke from 2 dp to 18 dp over [PULSE_MS]
     * and collapses it back over [RECOVER_MS]. Reproduced as the ring's width so the emphasis
     * reads as the same gesture.
     */
    val CURSOR_RING_MIN: Dp = 2.dp
    val CURSOR_RING_MAX: Dp = 18.dp
}
