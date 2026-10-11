/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.ui.theme.AppPalette
import app.opentv.ui.theme.palette
import androidx.compose.ui.graphics.Color
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * The parity palette's readability invariants. A palette edit that makes the cursor harder to
 * read than the cell it sits on, or the body text harder to read than the surface, is a
 * regression in the guide's most basic affordance - so each pair that the guide relies on is
 * pinned here rather than eyeballed.
 */
class GuidePaletteContrastTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun cursorInk_isReadableOnTheFocusedCell() {
        val p = AppPalette.TVIMATE_PARITY.palette()
        assertThat(contrast(p.onCellFocus, p.cellFocus)).isAtLeast(4.5)
    }

    @Test
    fun bodyText_isReadableOnProgrammeCells() {
        val p = AppPalette.TVIMATE_PARITY.palette()
        assertThat(contrast(p.onSurface, p.chromeCell)).isAtLeast(4.5)
    }

    @Test
    fun bodyText_isReadableOnTheGuideBackground() {
        val p = AppPalette.TVIMATE_PARITY.palette()
        assertThat(contrast(p.onSurface, p.background)).isAtLeast(4.5)
    }

    @Test
    fun secondaryText_isReadableOnTheGuideBackground() {
        val p = AppPalette.TVIMATE_PARITY.palette()
        assertThat(contrast(p.onSurfaceVariant, p.background)).isAtLeast(3.0)
    }

    @Test
    fun theParityPalette_isDistinctFromSlate() {
        // The parity palette must never silently become Slate Guide: the guide's look is the
        // point of the theme, and Slate stays untouched for everyone else.
        assertThat(AppPalette.TVIMATE_PARITY).isNotEqualTo(AppPalette.SLATE_GUIDE)
    }
}
