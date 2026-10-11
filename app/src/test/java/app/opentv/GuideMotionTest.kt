/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.ui.theme.GuideMotion
import androidx.compose.animation.core.CubicBezierEasing
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

/**
 * The guide's motion constants, pinned against the reference's own shipped curves and timings.
 *
 * An easing curve is easy to "close enough" into something wrong: `CubicBezierEasing(0.4f, 0f, 0.2f, 1f)`
 * and a plain `FastOutSlowIn` differ audibly on a TV at 166 ms. These assert the endpoints and the
 * midpoint of each curve so a well-meant substitution cannot pass unnoticed.
 */
class GuideMotionTest {

    @Test
    fun cursorEasing_startsAtRest_andEndsAtRest() {
        assertThat(GuideMotion.CURSOR_EASING.transform(0f)).isEqualTo(0f)
        assertThat(GuideMotion.CURSOR_EASING.transform(1f)).isEqualTo(1f)
    }

    @Test
    fun cursorEasing_isTheReferencesDecelerateCurve() {
        // c 0.4,0.0 0.2,1.0 - the curve the reference uses on its focus animations.
        val e = GuideMotion.CURSOR_EASING
        assertThat(abs(e.transform(0.5f) - CubicBezierEasing(0.4f, 0f, 0.2f, 1f).transform(0.5f)))
            .isLessThan(0.001f)
        // Decelerate means most of the distance is covered early: past the midpoint by halfway.
        assertThat(e.transform(0.5f)).isGreaterThan(0.5f)
    }

    @Test
    fun timings_matchTheReferencesFocusAnimation() {
        assertThat(GuideMotion.FOCUS_MS).isEqualTo(166)
        assertThat(GuideMotion.RECOVER_MS).isEqualTo(16)
    }

    @Test
    fun focusScale_isLeanbacksHighlightZoom() {
        assertThat(GuideMotion.FOCUS_SCALE).isEqualTo(1.06f)
        // It lifts without resizing the row: the guide must not re-measure on a cursor move.
        assertThat(GuideMotion.FOCUS_SCALE).isGreaterThan(1.0f)
        assertThat(GuideMotion.FOCUS_SCALE).isLessThan(1.2f)
    }

    @Test
    fun cursorRing_pulsesFromHairlineToEmphasis() {
        assertThat(GuideMotion.CURSOR_RING_MAX.value).isGreaterThan(GuideMotion.CURSOR_RING_MIN.value)
        assertThat(GuideMotion.CURSOR_RING_MIN.value).isEqualTo(2f)
        assertThat(GuideMotion.CURSOR_RING_MAX.value).isEqualTo(18f)
    }
}
