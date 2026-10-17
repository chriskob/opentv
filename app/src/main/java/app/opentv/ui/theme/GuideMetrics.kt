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
 * The channel column, the cell and the guide's chrome are quoted from the reference's own decoded
 * layouts (`PLAYLIST/work/decoded_res/res/layout/`): `APKTOOL_RENAMED_0x7f0e008e.xml` for the
 * channel row, `0x7f0e0092.xml` for the cell and `0x7f0e0058.xml` for the screen. Values the
 * decoded tree states in code rather than in a layout - the cell's 12sp, the row pitch's 3dp
 * gutter - come from the device capture instead, and say so.
 *
 * Every field here is read by a render site. A value nothing reads is a value that drifts, and a
 * table that lies about what drives the screen; `GuideRenderParityTest` pins that too.
 *
 * Only the guide reads these. `LocalGuideMetrics` exists so the parity theme can supply its own
 * set later without the guide changing site.
 */
@androidx.compose.runtime.Immutable
data class GuideMetrics(
    val rowHeight: Dp,
    val channelColumnWidth: Dp,
    val channelNumberWidth: Dp,
    /** Leading margin of the channel number, measured from the column's start edge. */
    val channelNumberMarginStart: Dp,
    /** Gap between the number and the logo. */
    val logoMarginStart: Dp,
    /** Gap between the logo and the channel name. */
    val channelNameMarginStart: Dp,
    /** The name's own `layout_marginEnd=6.0dip`, before the row's status icons. */
    val channelNameMarginEnd: Dp,
    /** The reference's name is `singleLine`: a long name scrolls once rather than wrapping. */
    val channelNameMaxLines: Int,
    val logoSize: Dp,
    val logoWidth: Dp,
    val logoHeight: Dp,
    val halfHourWidth: Dp,
    val cellCornerRadius: Dp,
    /** The gutter between two rows: the reference's 41dip band plus this is its 44dp pitch. */
    val cellGap: Dp,
    val cellPaddingHorizontal: Dp,
    val cellPaddingVertical: Dp,
    /**
     * The channel column's first status slot, after the name: `20dip` behind a `6dip` end margin.
     * The capture puts the playing mark here - leftmost of the three, tinted with the accent.
     */
    val statusIconPrimarySize: Dp,
    val statusIconPrimaryMarginEnd: Dp,
    /**
     * The second: `16dip` at `alpha=0.6`, `8dip` from the end. The capture's catch-up clock lives
     * here - small and quiet, on most rows.
     */
    val statusIconSecondarySize: Dp,
    val statusIconSecondaryMarginEnd: Dp,
    val statusIconSecondaryAlpha: Float,
    /** The third: `18dip`, `8dip` from the end. The favourite star takes it. */
    val statusIconTertiarySize: Dp,
    val statusIconTertiaryMarginEnd: Dp,
    /**
     * The header clock's own band. The reference gives the clock a `12dip` top margin and a `29dip`
     * height, which together are exactly one [rowHeight] - the date/time sits on the first row's
     * line, and the ruler labels share that band.
     */
    val headerClockHeight: Dp,
    val headerClockMarginTop: Dp,
    val headerClockMarginStart: Dp,
    val headerClockMarginEnd: Dp,
    /** The `1dip` rule the reference's screen layout declares at the clock's bottom edge. */
    val headerDividerHeight: Dp,
    /** Whether that rule is drawn; the reference ships it `gone`, and the capture shows no line. */
    val headerDividerVisible: Boolean,
    /** The grid block's own `layout_marginTop=2.0dip`, between the header and the first row. */
    val gridMarginTop: Dp,
    /** Its `paddingBottom=8.0dip`, which its `clipToPadding=false` lets the rows scroll through. */
    val gridPaddingBottom: Dp,
    /** How translucent the guide's own chrome is; fills only, never the focus ring or text. */
    val chromeAlpha: Float,
    val badgeCornerRadius: Dp,
    val badgeTextSp: TextUnit,
    val replayMarkerSize: Dp,
    val reminderBadgeSize: Dp,
    val favouriteStarSize: Dp,
    val channelNameSp: TextUnit,
    val channelNumberSp: TextUnit,
    val programmeTitleSp: TextUnit,
    /** The reference's cell layout sets maxLines=1 with an end ellipsis. */
    val programmeTitleMaxLines: Int,
    val programmeMetaSp: TextUnit,
    val timeRulerSp: TextUnit,
    /**
     * The header's own date/time. Separate from [timeRulerSp] because the reference sizes the two
     * differently: its guide layout puts the clock at 16sp, while the 30-minute ruler labels were
     * measured at 12sp off the maintainer's own screen.
     */
    val headerClockSp: TextUnit,
    val detailTitleSp: TextUnit,
    val detailMetaSp: TextUnit,
    val detailBodySp: TextUnit,
    val detailCategorySp: TextUnit,
) {
    /** Minutes of programme time per dp of grid width - the grid's axis unit. */
    val minuteDp: Float get() = halfHourWidth.value / 30f

    companion object {
        /**
         * The parity defaults; confirmed in the measurement table.
         *
         * The channel-column, cell and chrome values are taken verbatim from the reference's own
         * decoded layouts rather than derived from a photograph: the row band is 41dip, the number
         * 28dip wide behind a 16dip margin, the logo a 47x31dip box 6dip behind the number,
         * the name 10dip behind that and one marqueed line ending 6dip early, the status icons
         * 20/16/18dip spaced by their own end margins, the clock a 29dip band inset 16dip, and the
         * cell 12dip padded with a single line of `sans-serif-condensed`. `cellGap` supplies the
         * gutter that turns the 41dip band into the 44dp row pitch the Onn capture measured.
         *
         * The number is the one value the declaration alone gets wrong: the layout says 24sp, but
         * the reference's own code draws the number smaller than it declares, and the capture
         * measures it at 16sp. The drawn size wins, so 16sp is what [channelNumberSp] holds.
         */
        val TIVIMATE_PARITY = GuideMetrics(
            rowHeight = 41.dp,
            channelColumnWidth = 270.dp,
            channelNumberWidth = 28.dp,
            channelNumberMarginStart = 16.dp,
            logoMarginStart = 6.dp,
            channelNameMarginStart = 10.dp,
            channelNameMarginEnd = 6.dp,
            channelNameMaxLines = 1,
            logoSize = 32.dp,
            logoWidth = 47.dp,
            logoHeight = 31.dp,
            halfHourWidth = 160.dp,
            cellCornerRadius = 6.dp,
            cellGap = 3.dp,
            cellPaddingHorizontal = 12.dp,
            cellPaddingVertical = 1.dp,
            statusIconPrimarySize = 20.dp,
            statusIconPrimaryMarginEnd = 6.dp,
            statusIconSecondarySize = 16.dp,
            statusIconSecondaryMarginEnd = 8.dp,
            statusIconSecondaryAlpha = 0.6f,
            statusIconTertiarySize = 18.dp,
            statusIconTertiaryMarginEnd = 8.dp,
            headerClockHeight = 29.dp,
            headerClockMarginTop = 12.dp,
            headerClockMarginStart = 16.dp,
            headerClockMarginEnd = 8.dp,
            headerDividerHeight = 1.dp,
            // The reference says `android:visibility="gone"` and the capture agrees: no line shows
            // between the header and the first row. Drawing it by default would be less faithful,
            // not more, so the geometry is pinned here and left off until it is wanted.
            headerDividerVisible = false,
            gridMarginTop = 2.dp,
            gridPaddingBottom = 8.dp,
            chromeAlpha = 0.7f,
            badgeCornerRadius = 4.dp,
            // The chip's original size, and the one its render site documents: an annotation,
            // not a second title. The parity pass that moved the badges onto metrics set this
            // to 12.sp - the programme title's own size - and the chips grew into loud pills
            // crowding the cell. The hard-coded size they were drawn at before all of it was
            // 8sp, which is what `GuideBadge` still describes.
            badgeTextSp = 8.sp,
            replayMarkerSize = 16.dp,
            reminderBadgeSize = 16.dp,
            favouriteStarSize = 24.dp,
            channelNameSp = 14.sp,
            // The reference's layout declares 24sp for the number, but its own render measures the
            // digits 22px tall beside the 14sp name's 21px - 16sp, not the 34px 24sp would give.
            // The pixels are the reference; the declaration alone is not.
            channelNumberSp = 16.sp,
            programmeTitleSp = 14.sp,
            programmeTitleMaxLines = 1,
            programmeMetaSp = 12.sp,
            timeRulerSp = 12.sp,
            headerClockSp = 16.sp,
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
 * drifting apart by float dust, and a programme that starts mid-cell (10:07) closes exactly on the
 * boundary it ends at. An interval that ends at or before it starts has no cell.
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
