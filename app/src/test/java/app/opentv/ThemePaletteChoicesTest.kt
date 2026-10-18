/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.AppSettings
import app.opentv.ui.theme.AppPalette
import app.opentv.ui.theme.palette
import androidx.compose.ui.graphics.Color
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * The theme picker offers five looks that are actually different.
 *
 * The picker used to list eleven palettes whose background, surfaces and accent were the same
 * room, so every chip showed the same swatch (maintainer's report, 2026-10-18). The list now
 * holds five complete palettes, each a different family, with Slate Guide still the default, and
 * these pins keep it that way: a theme that copies another's room, a sixth chip, or the guide's
 * own palette sneaking into the picker fails here instead of on the TV.
 */
class ThemePaletteChoicesTest {

    private fun luminance(c: Color): Double {
        fun channel(v: Float) = if (v <= 0.03928f) v / 12.92 else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun thePicker_offersFiveThemes() {
        assertThat(AppPalette.choices).hasSize(5)
    }

    @Test
    fun theDefault_isOneOfThem() {
        assertThat(AppPalette.choices).contains(AppSettings.DEFAULT_PALETTE)
    }

    @Test
    fun everyTheme_isItsOwnRoom() {
        // The regression: eleven names, one room. No two offered themes may share a background,
        // and none may share an accent either — those are the bands the chip swatch shows, so a
        // shared value is a chip that looks like its neighbour.
        val backgrounds = AppPalette.choices.map { it.palette().background }
        val accents = AppPalette.choices.map { it.palette().primary }
        assertThat(backgrounds.toSet()).hasSize(AppPalette.choices.size)
        assertThat(accents.toSet()).hasSize(AppPalette.choices.size)
    }

    @Test
    fun slateGuide_keepsTheDefaultLook() {
        // The default is what every existing install wears; its room is pinned against edits.
        val p = AppPalette.SLATE_GUIDE.palette()
        assertThat(p.background).isEqualTo(Color(0xFF1C232C))
        assertThat(p.surface).isEqualTo(Color(0xFF242B33))
        assertThat(p.primary).isEqualTo(Color(0xFF00BCD4))
        assertThat(p.onSurface).isEqualTo(Color(0xFFFFFFFF))
    }

    @Test
    fun theGuidePalette_isNotOfferedAsATheme() {
        assertThat(AppPalette.choices).doesNotContain(AppPalette.TVIMATE_PARITY)
        assertThat(AppPalette.TVIMATE_PARITY.selectable).isFalse()
    }

    @Test
    fun everyTheme_staysReadable() {
        // Each room carries the same readability floor as the guide's palette: body text on the
        // background, secondary text, the accent itself, and the ink on filled accent controls.
        AppPalette.choices.forEach { choice ->
            val p = choice.palette()
            assertThat(contrast(p.onSurface, p.background)).isAtLeast(4.5)
            assertThat(contrast(p.onSurfaceVariant, p.background)).isAtLeast(3.0)
            assertThat(contrast(p.primary, p.background)).isAtLeast(3.0)
            assertThat(contrast(p.onPrimary, p.primary)).isAtLeast(4.5)
        }
    }
}
