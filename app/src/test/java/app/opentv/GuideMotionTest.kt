/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.ui.theme.GuideMotion
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

/**
 * The guide's cursor motion, pinned against leanback's own focus-highlight behaviour.
 *
 * The curve matters more than it looks: leanback uses `AccelerateDecelerateInterpolator`, which is
 * symmetric and therefore *arrives and settles*; the decelerate curve a well-meant substitution
 * would use is fast-out, which reads as a flick on a TV at this duration. These pin the endpoints,
 * the exact midpoint, and the timing.
 */
class GuideMotionTest {

    @Test
    fun focusHighlightTravel_isLeanbacks150ms() {
        assertThat(GuideMotion.FOCUS_HIGHLIGHT_MS).isEqualTo(150)
    }

    @Test
    fun focusEasing_isAndroidsAccelerateDecelerateCurve() {
        val e = GuideMotion.FOCUS_EASING
        assertThat(e.transform(0f)).isEqualTo(0f)
        assertThat(e.transform(1f)).isEqualTo(1f)
        // Symmetric about the midpoint: slow in, fast through, slow out.
        assertThat(e.transform(0.5f)).isWithin(0.001f).of(0.5f)
        assertThat(e.transform(0.25f)).isLessThan(0.25f)
        assertThat(e.transform(0.75f)).isGreaterThan(0.75f)
    }

    @Test
    fun focusEasing_isNotAFastOutDecelerate() {
        // Guards the substitution: at a quarter in, a decelerate curve would be past halfway,
        // while the accelerate-decelerate curve is still behind it.
        assertThat(GuideMotion.FOCUS_EASING.transform(0.25f)).isLessThan(0.25f)
    }

    @Test
    fun focusScale_isLeanbacksHighlightZoom() {
        assertThat(GuideMotion.FOCUS_SCALE).isEqualTo(1.06f)
        // It lifts without resizing the row: the guide must not re-measure on a cursor move.
        assertThat(GuideMotion.FOCUS_SCALE).isGreaterThan(1.0f)
        assertThat(GuideMotion.FOCUS_SCALE).isLessThan(1.2f)
    }

    @Test
    fun focusEasing_isMonotonic() {
        // A cursor that eases backward mid-travel reads as a stutter.
        var previous = GuideMotion.FOCUS_EASING.transform(0f)
        for (i in 1..20) {
            val t = i / 20f
            val v = GuideMotion.FOCUS_EASING.transform(t)
            assertThat(v).isAtLeast(previous - 0.0001f)
            previous = v
        }
        assertThat(abs(previous - 1f)).isLessThan(0.001f)
    }
}
