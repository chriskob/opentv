/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.ui.theme.GuideMetrics
import app.opentv.ui.theme.widthFor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The guide's geometry comes from one measured object. These tests pin the minute-to-width
 * mapping (the grid's 30-minute cell) and the parity defaults, so a metric edit has to be
 * deliberate rather than a silent visual drift.
 */
class GuideMetricsTest {

    private fun minutes(n: Long) = n * 60_000L

    @Test
    fun thirtyMinutes_spansExactlyHalfTheHourWidth() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.widthFor(0L, minutes(30))).isEqualTo(metrics.halfHourWidth)
    }
    @Test
    fun ninetyMinutes_spansThreeHalfHourCells() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.widthFor(0L, minutes(90))).isEqualTo(metrics.halfHourWidth * 3f)
    }

    @Test
    fun aWholeHour_spansTwoCells() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.widthFor(0L, minutes(60))).isEqualTo(metrics.halfHourWidth * 2f)
    }

    @Test
    fun aProgrammeEndingOnTheNowMinute_endsOnAGridBoundary() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        // 10:07 -> 11:00 is 53 minutes, so the cell must close on the hour boundary,
        // not leave a sliver of the next cell uncovered.
        val start = minutes(10 * 60 + 7)
        val end = minutes(11 * 60)
        assertThat(metrics.widthFor(start, end)).isEqualTo(283.dp)
    }

    @Test
    fun anEmptyOrBackwardsInterval_hasNoWidth() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.widthFor(minutes(60), minutes(30))).isEqualTo(0.dp)
        assertThat(metrics.widthFor(minutes(60), minutes(60))).isEqualTo(0.dp)
    }

    @Test
    fun parityDefaults_matchTheMeasuredTable() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        // The channel band is 41dip in the reference's own row layout; the 3dp gutter supplies the
        // 44dp pitch the device capture measured, so the two sources agree.
        assertThat(metrics.rowHeight).isEqualTo(41.dp)
        assertThat(metrics.channelColumnWidth).isEqualTo(270.dp)
        assertThat(metrics.channelNumberWidth).isEqualTo(28.dp)
        assertThat(metrics.channelNumberMarginStart).isEqualTo(16.dp)
        assertThat(metrics.logoSize).isEqualTo(32.dp)
        assertThat(metrics.halfHourWidth).isEqualTo(160.dp)
        assertThat(metrics.cellCornerRadius).isEqualTo(6.dp)
        assertThat(metrics.cellGap).isEqualTo(3.dp)
        assertThat(metrics.cellPaddingHorizontal).isEqualTo(12.dp)
        assertThat(metrics.cellPaddingVertical).isEqualTo(1.dp)
        assertThat(metrics.chromeAlpha).isEqualTo(0.7f)
        assertThat(metrics.badgeCornerRadius).isEqualTo(4.dp)
        assertThat(metrics.replayMarkerSize).isEqualTo(16.dp)
        assertThat(metrics.reminderBadgeSize).isEqualTo(16.dp)
        assertThat(metrics.favouriteStarSize).isEqualTo(24.dp)
    }

    @Test
    fun theChannelColumn_matchesTheReferenceRowLayout() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        // 16 margin + 28 number + 6 gap + 47 logo + 10 gap = 107dp, leaving 157dp of the 270dp
        // column for the name once its own 6dp end margin is taken - the arrangement the reference's
        // own channel-row layout declares. Nothing is held back at the column's end as well: a
        // second inset there shortened the name's run by 6dp for nothing.
        assertThat(metrics.logoWidth).isEqualTo(47.dp)
        assertThat(metrics.logoHeight).isEqualTo(31.dp)
        assertThat(metrics.logoMarginStart).isEqualTo(6.dp)
        assertThat(metrics.channelNameMarginStart).isEqualTo(10.dp)
    }

    @Test
    fun theChannelName_isOneMarqueedLine() {
        // `0x7f0e008e` `0x7f0b03db`: singleLine=true, ellipsize=marquee, marqueeRepeatLimit=1,
        // layout_marginEnd=6.0dip. The name used to wrap to two lines, which is the one thing about
        // the channel column a photograph could not settle - the layout settles it.
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.channelNameMaxLines).isEqualTo(1)
        assertThat(metrics.channelNameMarginEnd).isEqualTo(6.dp)
    }

    @Test
    fun theChannelRowStatusIcons_matchTheReferenceSlots() {
        // `0x7f0e008e`: 0x7f0b022a is 20x20dip with a 6dip end margin, 0x7f0b0219 is 16x16dip at
        // alpha 0.6 with an 8dip end margin, 0x7f0b0230 is 18x18dip with an 8dip end margin. The
        // reference spaces these with their own margins, not with inserted spacers, and the capture
        // fills the slots: 20dip is the playing mark, 16dip the catch-up clock, 18dip the star.
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.statusIconPrimarySize).isEqualTo(20.dp)
        assertThat(metrics.statusIconPrimaryMarginEnd).isEqualTo(6.dp)
        assertThat(metrics.statusIconSecondarySize).isEqualTo(16.dp)
        assertThat(metrics.statusIconSecondaryMarginEnd).isEqualTo(8.dp)
        assertThat(metrics.statusIconSecondaryAlpha).isEqualTo(0.6f)
        assertThat(metrics.statusIconTertiarySize).isEqualTo(18.dp)
        assertThat(metrics.statusIconTertiaryMarginEnd).isEqualTo(8.dp)
    }

    @Test
    fun theGuideChrome_matchesTheReferenceScreenLayout() {
        // `0x7f0e0058`: the clock 0x7f0b03a6 is 29dip tall with marginTop 12dip and marginStart
        // 16dip; the divider 0x7f0b0140 is 1dip of #20ffffff; the grid 0x7f0b0430 carries
        // marginTop 2dip and paddingBottom 8dip.
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.headerClockHeight).isEqualTo(29.dp)
        assertThat(metrics.headerClockMarginTop).isEqualTo(12.dp)
        assertThat(metrics.headerClockMarginStart).isEqualTo(16.dp)
        assertThat(metrics.headerClockMarginEnd).isEqualTo(8.dp)
        assertThat(metrics.headerDividerHeight).isEqualTo(1.dp)
        assertThat(metrics.gridMarginTop).isEqualTo(2.dp)
        assertThat(metrics.gridPaddingBottom).isEqualTo(8.dp)
    }

    @Test
    fun theHeaderBand_isExactlyOneChannelRowBand() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        // The reference's clock band is 12dip of margin plus a 29dip clock, which is the 41dip of
        // its own channel row - so the date/time sits on the first row's line rather than above it.
        // In the device capture the clock and the 2:00 PM ruler label are on the same line, which is
        // this relationship seen from the outside.
        assertThat(metrics.headerClockMarginTop + metrics.headerClockHeight)
            .isEqualTo(metrics.rowHeight)
    }

    @Test
    fun theHeaderDivider_isDeclaredButNotDrawn() {
        // The reference ships the divider `android:visibility="gone"`, and the capture shows no
        // line between the header and the first row. The element is pinned above so it can be
        // turned on deliberately; drawing it by default would be less faithful, not more.
        assertThat(GuideMetrics.TIVIMATE_PARITY.headerDividerVisible).isFalse()
    }

    @Test
    fun theProgrammeCell_isASingleLine() {
        // The reference's cell layout sets maxLines=1 with an end ellipsis; the details live in
        // the description panel, not in a second line inside the block.
        assertThat(GuideMetrics.TIVIMATE_PARITY.programmeTitleMaxLines).isEqualTo(1)
    }

    @Test
    fun parityTypeScale_matchesTheMeasuredSpaces() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.channelNameSp).isEqualTo(TextUnit(14f, TextUnitType.Sp))
        // The layout declares 24sp, but the reference's own render measures the digits 22px tall
        // beside the 14sp name's 21px - i.e. 16sp. Mirroring the drawn size, not the declared one,
        // is deliberate: the user compares pixels, and 24sp reads as oversized next to TiviMate.
        assertThat(metrics.channelNumberSp).isEqualTo(16.sp)
        assertThat(metrics.programmeTitleSp).isEqualTo(14.sp)
        assertThat(metrics.programmeMetaSp).isEqualTo(12.sp)
        assertThat(metrics.timeRulerSp).isEqualTo(12.sp)
        // The reference's guide layout sets the header clock at 16sp; the ruler labels above stay at
        // the 12sp the maintainer measured off their own screen, so the two are separate metrics.
        assertThat(metrics.headerClockSp).isEqualTo(16.sp)
        assertThat(metrics.detailTitleSp).isEqualTo(20.sp)
        assertThat(metrics.detailMetaSp).isEqualTo(14.sp)
        assertThat(metrics.detailBodySp).isEqualTo(14.sp)
        assertThat(metrics.detailCategorySp).isEqualTo(14.sp)
    }
}
