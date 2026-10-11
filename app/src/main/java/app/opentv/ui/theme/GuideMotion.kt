/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.animation.core.Easing
import kotlin.math.PI
import kotlin.math.cos

/**
 * The guide's cursor motion.
 *
 * The reference's guide and menus are `androidx.leanback.widget.VerticalGridView` /
 * `HorizontalGridView` (both classes are present in its dex, as is `GridLayoutManager`), and the
 * lift a TV cursor has is leanback's own focus highlight. From leanback's Apache-2.0 source
 * (`FocusHighlightHelper.java`): `BrowseItemFocusHighlight` scales the focused child over
 * [FOCUS_HIGHLIGHT_MS] on an `AccelerateDecelerateInterpolator`, using `view.setScaleX/setScaleY`
 * rather than re-laying the child out. That is reproduced here — [FOCUS_EASING] is Android's
 * accelerate-decelerate curve exactly, not an approximation — with the same "scale the child,
 * never re-measure" approach, which is what keeps a 20-programme row smooth.
 *
 * Not applied, deliberately: the reference also ships a cursor pulse (a stroke growing 2dp to 18dp
 * over 166ms and collapsing in 16ms — both curves and timings are in its resource table).
 * Reproducing it as the *cell border* flashed visibly on every d-pad step, because 18dp of border
 * on a 44dp-tall row is eight dp of ring on each side. It belongs on a decorative ring behind the
 * cell, and is left for when one exists rather than faked here.
 *
 * Values live in one object (not inlined at call sites) so the guide's motion is adjustable in one
 * place and pinned by `GuideMotionTest`.
 */
object GuideMotion {

    /**
     * The focus highlight's travel, straight from leanback's `BrowseItemFocusHighlight`. Slow in,
     * fast through the middle, slow out — which is what keeps a cursor lift from feeling like a
     * snap.
     */
    const val FOCUS_HIGHLIGHT_MS: Int = 150

    /** Android's `AccelerateDecelerateInterpolator`: `(cos((t + 1) * PI) + 1) / 2`. */
    val FOCUS_EASING: Easing = Easing { fraction ->
        ((cos((fraction + 1f) * PI) + 1f) / 2f).toFloat()
    }

    /**
     * How much the focused cell lifts, as leanback's `FocusHighlightHelper` zoom does. Scale, not
     * layout, so moving the cursor never re-measures the grid. Leanback's factor comes from its
     * `lb_focus_zoom_factor_*` fractions (small / xsmall / medium / large, chosen by the
     * presenter); 1.06 is the medium case this guide's cell size maps to.
     */
    const val FOCUS_SCALE: Float = 1.06f
}
