/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * The guide's geometry and type scale, in one immutable object.
 *
 * The guide used to carry these as module-level constants inside `GuideGrid.kt`, which meant the
 * shape of the screen and the values the measurements produced lived in different places. Every
 * value here comes from the measurement table in
 * `docs/superpowers/plans/2026-10-11-guide-metrics-table.md`; changing a value is a deliberate act
 * pinned by `GuideMetricsTest`, not silent drift.
 *
 * Only the guide reads these. `LocalGuideMetrics` exists so the parity theme can supply its own
 * set later without the guide changing site.
 */
@androidx.compose.runtime.Immutable
data class GuideMetrics(
    val rowHeight: Dp,
    val channelColumnWidth: Dp,
    val channelNumberWidth: Dp,
    val logoSize: Dp,
    val timeHeaderHeight: Dp,
    val halfHourWidth: Dp,
    val cellCornerRadius: Dp,
    val cellGap: Dp,
    val cellPaddingHorizontal: Dp,
    val cellPaddingVertical: Dp,
    val focusRingWidth: Dp,
    val nowLineWidth: Dp,
    /** How translucent the guide's own chrome is; fills only, never the focus ring or text. */
    val chromeAlpha: Float,
    val badgeHeight: Dp,
    val badgeCornerRadius: Dp,
    val badgeTextSp: TextUnit,
    val liveBadgeSize: Dp,
    val replayMarkerSize: Dp,
    val reminderBadgeSize: Dp,
    val favouriteStarSize: Dp,
    val channelNameSp: TextUnit,
    val channelCallsignSp: TextUnit,
    val programmeTitleSp: TextUnit,
    val programmeMetaSp: TextUnit,
    val timeRulerSp: TextUnit,
    val detailTitleSp: TextUnit,
    val detailMetaSp: TextUnit,
    val detailBodySp: TextUnit,
    val detailCategorySp: TextUnit,
) {
    /** Minutes of programme time per dp of grid width — the grid's axis unit. */
    val minuteDp: Float get() = halfHourWidth.value / 30f

    companion object {
        /** The parity defaults; confirmed in the measurement table. */
        val TIVIMATE_PARITY = GuideMetrics(
            rowHeight = 48.dp,
            channelColumnWidth = 270.dp,
            channelNumberWidth = 40.dp,
            logoSize = 32.dp,
            timeHeaderHeight = 38.dp,
            halfHourWidth = 168.dp,
            cellCornerRadius = 6.dp,
            cellGap = 4.dp,
            cellPaddingHorizontal = 8.dp,
            cellPaddingVertical = 6.dp,
            focusRingWidth = 2.dp,
            nowLineWidth = 2.dp,
            chromeAlpha = 0.7f,
            badgeHeight = 20.dp,
            badgeCornerRadius = 4.dp,
            badgeTextSp = 12.sp,
            liveBadgeSize = 12.dp,
            replayMarkerSize = 16.dp,
            reminderBadgeSize = 16.dp,
            favouriteStarSize = 24.dp,
            channelNameSp = 14.sp,
            channelCallsignSp = 12.sp,
            programmeTitleSp = 14.sp,
            programmeMetaSp = 12.sp,
            timeRulerSp = 12.sp,
            detailTitleSp = 20.sp,
            detailMetaSp = 14.sp,
            detailBodySp = 14.sp,
            detailCategorySp = 14.sp,
        )
    }
}

/**
 * Width of a programme cell spanning the interval [fromUtcMillis, toUtcMillis).
 *
 * The grid's cell is half an hour wide, so a programme's width is its duration expressed in
 * 30-minute units. Widths are whole dp so neighbouring cells share exact boundaries instead of
 * drifting apart by float dust, and a programme that starts mid-cell (10:07) closes exactly on
 * the boundary it ends at. An interval that ends at or before it starts has no cell.
 */
fun GuideMetrics.widthFor(fromUtcMillis: Long, toUtcMillis: Long): Dp {
    if (toUtcMillis <= fromUtcMillis) return Dp(0f)
    val minutes = (toUtcMillis - fromUtcMillis).toDouble() / 60_000.0
    val dp = (halfHourWidth.value * minutes / 30.0).roundToInt()
    return if (dp <= 0) Dp(0f) else dp.dp
}

/** The guide's metrics; defaults to the parity set so the guide never reads an empty object. */
val LocalGuideMetrics: ProvidableCompositionLocal<GuideMetrics> =
    staticCompositionLocalOf { GuideMetrics.TIVIMATE_PARITY }
