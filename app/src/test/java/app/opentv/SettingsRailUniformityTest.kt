/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import java.io.File
import org.junit.Test
import com.google.common.truth.Truth.assertThat

/**
 * The collapsed settings rail is a column of equal squares.
 *
 * Collapsed, the drawer is a 92dp strip of icon tiles. The tiles used to sit against its left
 * edge, and a selected row was the only one wide enough to fill the strip - a full-width pill
 * that started left of the tiles and ended past them, so the rail read as lopsided (maintainer's
 * photo, 2026-10-18). Now every tile is the same 40dp square centred in the strip, and a selected
 * row carries its mark on the tile itself: the fill plus the accent bar inside the tile's own left
 * edge. These pins keep the even geometry from drifting back.
 */
class SettingsRailUniformityTest {

    private val settingsKit: String by lazy {
        File("src/main/java/app/opentv/ui/settings/components/SettingsKit.kt").readText()
    }

    @Test
    fun collapsedTile_isCentredInTheRail() {
        // The collapsed branch centres the tile in the row; a left-hugging tile with a trailing
        // weight spacer is what made the rail lopsided.
        assertThat(
            settingsKit.contains("Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { tile() }"),
        ).isTrue()
    }

    @Test
    fun collapsedSelection_livesOnTheTile_notTheRow() {
        // The row keeps no selection fill while collapsed (`selected && expanded`) ...
        assertThat(settingsKit.contains("selected = selected && expanded")).isTrue()
        // ... and the collapsed tile paints the selection fill and the accent bar itself.
        assertThat(settingsKit.contains("val tileSelected = selected && !expanded")).isTrue()
        assertThat(
            settingsKit.contains("if (tileSelected) Modifier.background(AppTheme.palette.selectedFill)"),
        ).isTrue()
        // The row-wide accent bar is expanded-only; collapsed, the bar sits inside the tile.
        assertThat(settingsKit.contains("if (selected && expanded) {")).isTrue()
    }
}
