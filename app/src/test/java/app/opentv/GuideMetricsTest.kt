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
        assertThat(metrics.widthFor(start, end)).isEqualTo(424.dp)
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
        assertThat(metrics.rowHeight).isEqualTo(48.dp)
        assertThat(metrics.channelColumnWidth).isEqualTo(296.dp)
        assertThat(metrics.channelNumberWidth).isEqualTo(40.dp)
        assertThat(metrics.logoSize).isEqualTo(32.dp)
        assertThat(metrics.timeHeaderHeight).isEqualTo(56.dp)
        assertThat(metrics.halfHourWidth).isEqualTo(240.dp)
        assertThat(metrics.cellCornerRadius).isEqualTo(6.dp)
        assertThat(metrics.cellGap).isEqualTo(4.dp)
        assertThat(metrics.cellPaddingHorizontal).isEqualTo(8.dp)
        assertThat(metrics.cellPaddingVertical).isEqualTo(6.dp)
        assertThat(metrics.focusRingWidth).isEqualTo(2.dp)
        assertThat(metrics.nowLineWidth).isEqualTo(2.dp)
        assertThat(metrics.chromeAlpha).isEqualTo(0.7f)
        assertThat(metrics.badgeHeight).isEqualTo(20.dp)
        assertThat(metrics.liveBadgeSize).isEqualTo(12.dp)
        assertThat(metrics.replayMarkerSize).isEqualTo(16.dp)
        assertThat(metrics.favouriteStarSize).isEqualTo(24.dp)
    }

    @Test
    fun parityTypeScale_matchesTheMeasuredSpaces() {
        val metrics = GuideMetrics.TIVIMATE_PARITY
        assertThat(metrics.channelNameSp).isEqualTo(TextUnit(14f, TextUnitType.Sp))
        assertThat(metrics.channelCallsignSp).isEqualTo(TextUnit(12f, TextUnitType.Sp))
        assertThat(metrics.programmeTitleSp).isEqualTo(14.sp)
        assertThat(metrics.programmeMetaSp).isEqualTo(12.sp)
        assertThat(metrics.timeRulerSp).isEqualTo(12.sp)
        assertThat(metrics.detailTitleSp).isEqualTo(20.sp)
        assertThat(metrics.detailMetaSp).isEqualTo(14.sp)
        assertThat(metrics.detailBodySp).isEqualTo(14.sp)
        assertThat(metrics.detailCategorySp).isEqualTo(14.sp)
    }
}
