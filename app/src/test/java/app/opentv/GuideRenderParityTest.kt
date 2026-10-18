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

    @Test
    fun theGuideRow_takesItsChannelColumnGeometryFromTheMetrics() {
        // The guide row carried the number's width as a literal 32dp and the column padding as
        // 8dp, so those two measurements lived outside the table the parity work confirmed and
        // could drift without any metric edit showing it.
        assertThat(guideGrid.contains("metrics.channelNumberWidth")).isTrue()
        assertThat(guideGrid.contains("metrics.channelNumberMarginStart")).isTrue()
        assertThat(guideGrid.contains("metrics.channelNameMarginStart")).isTrue()
        assertThat(guideGrid.contains("metrics.logoWidth")).isTrue()
        assertThat(guideGrid.contains("metrics.logoHeight")).isTrue()
        assertThat(guideGrid.contains("metrics.channelNumberSp")).isTrue()
        assertThat(Regex("""Modifier\.width\(32\.dp\)""").containsMatchIn(guideGrid)).isFalse()
    }

    @Test
    fun theProgrammeCell_takesItsPaddingAndLineCountFromTheMetrics() {
        // The cell's inner padding was a literal 7dp/2dp and its title always wrapped to two
        // lines; both are measurements the reference states, so both belong in the table.
        assertThat(guideGrid.contains("metrics.cellPaddingHorizontal")).isTrue()
        assertThat(guideGrid.contains("metrics.cellPaddingVertical")).isTrue()
        assertThat(guideGrid.contains("metrics.programmeTitleMaxLines")).isTrue()
        assertThat(guideGrid.contains("padding(horizontal = 7.dp, vertical = 2.dp)")).isFalse()
    }

    @Test
    fun theHeaderClock_isSizedApartFromTheRuler() {
        // The reference sizes the header's clock (16sp) differently from the 30-minute ruler labels
        // (12sp). One metric used to drive both, so the ruler's measured size silently governed
        // the clock as well.
        assertThat(guideGrid.contains("metrics.headerClockSp")).isTrue()
    }

    @Test
    fun theProgrammeTitle_isSetInTheReferenceFace() {
        assertThat(guideGrid.contains("GuideProgrammeFontFamily")).isTrue()
    }

    @Test
    fun theGuideType_resolvesTheCondensedFamily() {
        val guideType = File("src/main/java/app/opentv/ui/theme/GuideType.kt").readText()
        assertThat(guideType.contains("sans-serif-condensed")).isTrue()
    }

    @Test
    fun theChannelName_isOneMarqueedLineRatherThanTwoWrappedOnes() {
        // The reference sets the name singleLine with ellipsize=marquee and marqueeRepeatLimit=1.
        // Two wrapped lines was the old shape, and the layout is unambiguous about it. The channel
        // *list* screen has its own two-line name in its own layout, so this looks only at the
        // guide row's channel column.
        val channelColumn = guideGrid
            .substringAfter("// ---- Channel column")
            .substringBefore("// ---- Scrolling programme timeline")
        assertThat(channelColumn.contains("metrics.channelNameMaxLines")).isTrue()
        assertThat(channelColumn.contains("metrics.channelNameMarginEnd")).isTrue()
        assertThat(channelColumn.contains("basicMarquee")).isTrue()
        assertThat(Regex("""maxLines\s*=\s*2\b""").containsMatchIn(channelColumn)).isFalse()
    }

    @Test
    fun theChannelColumn_takesItsStatusIconsFromTheReferenceSlots() {
        // The three trailing icons were 15dp/14dp/14dp literals inside the row; the reference gives
        // them 20x20dip (6dip end margin), 16x16dip at alpha 0.6 (8dip) and 18x18dip (8dip).
        for (metric in listOf(
            "metrics.statusIconPrimarySize",
            "metrics.statusIconPrimaryMarginEnd",
            "metrics.statusIconSecondarySize",
            "metrics.statusIconSecondaryMarginEnd",
            "metrics.statusIconSecondaryAlpha",
            "metrics.statusIconTertiarySize",
            "metrics.statusIconTertiaryMarginEnd",
        )) {
            assertThat(guideGrid.contains(metric)).isTrue()
        }
        assertThat(Regex("""size\(1[45]\.dp\)""").containsMatchIn(guideGrid)).isFalse()
    }

    @Test
    fun theHeader_takesItsBandAndItsDividerFromTheReferenceScreen() {
        assertThat(guideGrid.contains("metrics.headerClockHeight")).isTrue()
        assertThat(guideGrid.contains("metrics.headerClockMarginStart")).isTrue()
        assertThat(guideGrid.contains("metrics.headerClockMarginEnd")).isTrue()
        assertThat(guideGrid.contains("metrics.headerDividerHeight")).isTrue()
        // The header used to be a 38dp band sized by nothing in particular; the reference's clock
        // band is 29dip (24dip here since the maintainer's tightening, 2026-10-18), which is what
        // makes it line up with the ruler.
        assertThat(Regex("""height\(38\.dp\)""").containsMatchIn(guideGrid)).isFalse()
        // The EPG stamp is drawn from its own metric rather than borrowing the ruler's size.
        assertThat(guideGrid.contains("metrics.epgInfoSp")).isTrue()
    }

    @Test
    fun theGuideGrid_takesItsSpacingFromTheReferenceScreen() {
        assertThat(guideGrid.contains("metrics.gridMarginTop")).isTrue()
        assertThat(guideGrid.contains("metrics.gridPaddingBottom")).isTrue()
    }

    @Test
    fun theMetricsTable_hasNoFieldTheRenderNeverReads() {
        // The first parity pass left four measured-looking values nothing read, and two more were
        // never wired at all. A field that no render site reads is a value that will drift, and a
        // table that lies about what drives the screen. Bringing one back is a deliberate act that
        // has to delete this line.
        val metricsSource = File("src/main/java/app/opentv/ui/theme/GuideMetrics.kt").readText()
        for (unread in listOf(
            "timeHeaderHeight",
            "badgeHeight",
            "liveBadgeSize",
            "focusRingWidth",
            "nowLineWidth",
            "channelCallsignSp",
        )) {
            assertThat(metricsSource.contains(unread)).isFalse()
        }
    }
}
