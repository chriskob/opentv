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
 * The guide's geometry must come from one place: [app.opentv.ui.theme.GuideMetrics], supplied
 * through `LocalGuideMetrics`.
 *
 * GuideGrid.kt used to carry its own module-level constants, and a leftover constant silently wins
 * over the metrics - the screen would then measure differently from the table the parity work
 * confirmed. This test pins the swap: the constants are gone and the metrics local is in use, so a
 * future edit cannot reintroduce a second source of truth.
 *
 * The rendered geometry itself is verified on-device against the parity reference (see the
 * implementation plan's Task 8), because the full grid needs Coil and the focus graph.
 */
class GuideRenderParityTest {

    private val guideGrid: String by lazy {
        File("src/main/java/app/opentv/ui/channels/GuideGrid.kt").readText()
    }

    @Test
    fun theOldGeometryConstants_areGoneFromGuideGrid() {
        for (constant in listOf("CHANNEL_COLUMN", "ROW_HEIGHT", "HALF_HOUR_WIDTH", "MINUTE_DP")) {
            assertThat(guideGrid.contains("val $constant")).isFalse()
        }
    }

    @Test
    fun theGuide_readsItsGeometryFromTheMetricsLocal() {
        assertThat(guideGrid.contains("LocalGuideMetrics.current")).isTrue()
    }

    @Test
    fun theLocalWidthHelper_isGoneInFavourOfTheMetricsOne() {
        // GuideGrid had its own private widthFor; the guide now uses the metric-provided one.
        assertThat(Regex("""private\s+fun\s+widthFor""").containsMatchIn(guideGrid)).isFalse()
        assertThat(guideGrid.contains("import app.opentv.ui.theme.widthFor")).isTrue()
    }
}
