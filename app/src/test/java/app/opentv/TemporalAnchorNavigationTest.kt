/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Programme
import app.opentv.ui.channels.calculateInitialScrollOffsetPx
import app.opentv.ui.channels.calculateMountedFrameStartTime
import app.opentv.ui.channels.computeProgrammeMidpoint
import app.opentv.ui.channels.getVerticalTargetProgram
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TemporalAnchorNavigationTest {

    // 1_700_001_000_000L % 1_800_000L == 0 (aligned to 30-minute epoch boundary)
    private val baseTime = 1_700_001_000_000L

    private fun prog(title: String, startMin: Long, endMin: Long): Programme {
        return Programme(
            feedId = 1L,
            epgChannelId = "test-channel",
            startUtcMillis = baseTime + startMin * 60_000L,
            endUtcMillis = baseTime + endMin * 60_000L,
            title = title,
        )
    }

    @Test
    fun `exact timestamp match returns correct programme`() {
        val show1 = prog("Show 1", 0, 30)
        val show2 = prog("Show 2", 30, 60)
        val show3 = prog("Show 3", 60, 120)
        val programmes = listOf(show1, show2, show3)

        // Mid-show match
        val target = getVerticalTargetProgram(programmes, baseTime + 45 * 60_000L)
        assertThat(target).isEqualTo(show2)

        // Boundary start match (start <= anchor < end)
        val boundaryStart = getVerticalTargetProgram(programmes, baseTime + 30 * 60_000L)
        assertThat(boundaryStart).isEqualTo(show2)

        // Longer show match
        val longShow = getVerticalTargetProgram(programmes, baseTime + 100 * 60_000L)
        assertThat(longShow).isEqualTo(show3)
    }

    @Test
    fun `fallback to closest boundary when anchor falls into gap`() {
        val show1 = prog("Show 1", 0, 30)
        // Gap from 30 to 60 min
        val show2 = prog("Show 2", 60, 90)
        val programmes = listOf(show1, show2)

        // Anchor at 35 min (closer to show1 midpoint 15 min than show2 midpoint 75 min)
        val targetEarly = getVerticalTargetProgram(programmes, baseTime + 35 * 60_000L)
        assertThat(targetEarly).isEqualTo(show1)

        // Anchor at 55 min (closer to show2 midpoint 75 min than show1 midpoint 15 min)
        val targetLate = getVerticalTargetProgram(programmes, baseTime + 55 * 60_000L)
        assertThat(targetLate).isEqualTo(show2)
    }

    @Test
    fun `irregular duration round-trip retains temporal anchor across channels`() {
        // Channel 1: A 3-hour movie
        val movie = prog("Blockbuster Movie", 0, 180)
        val channel1Programs = listOf(movie)

        // Channel 2: 6 x 30-minute shows
        val episodes = (0 until 6).map { idx ->
            prog("Episode ${idx + 1}", idx * 30L, (idx + 1) * 30L)
        }

        // Suppose user is on Episode 4 (90 min to 120 min)
        val currentShow = episodes[3]
        val anchor = computeProgrammeMidpoint(currentShow) // 105 min

        // D-Pad UP to Channel 1
        val targetOnChannel1 = getVerticalTargetProgram(channel1Programs, anchor)
        assertThat(targetOnChannel1).isEqualTo(movie)

        // D-Pad DOWN back to Channel 2 with the same preserved anchor
        val targetBackOnChannel2 = getVerticalTargetProgram(episodes, anchor)
        assertThat(targetBackOnChannel2).isEqualTo(currentShow)
    }

    @Test
    fun `empty programme list returns null`() {
        val target = getVerticalTargetProgram(emptyList(), baseTime + 10 * 60_000L)
        assertThat(target).isNull()
    }

    @Test
    fun `computeProgrammeMidpoint calculates exact midpoint`() {
        val show = prog("Half Hour Show", 10, 40)
        val mid = computeProgrammeMidpoint(show)
        assertThat(mid).isEqualTo(baseTime + 25 * 60_000L)
    }

    @Test
    fun `calculateMountedFrameStartTime aligns to current half-hour slot`() {
        val halfHourMs = 30 * 60 * 1000L
        val baseHour = baseTime + 2 * halfHourMs // 1:00 PM
        val now = baseHour + 22 * 60 * 1000L // 1:22 PM
        val liveProgStart = baseHour // 1:00 PM

        val frameStart = calculateMountedFrameStartTime(
            nowMillis = now,
            liveProgrammeStartMillis = liveProgStart,
        )
        // Expected: 1:00 PM for 1:22 PM
        assertThat(frameStart).isEqualTo(baseHour)
    }

    @Test
    fun `calculateMountedFrameStartTime aligns to 10_30am when now is 10_30am`() {
        val halfHourMs = 30 * 60 * 1000L
        val tenAm = baseTime + 4 * halfHourMs // 10:00 AM
        val tenThirtyAm = tenAm + halfHourMs  // 10:30 AM
        val liveProgStart = tenAm             // 10:00 AM

        val frameStart = calculateMountedFrameStartTime(
            nowMillis = tenThirtyAm,
            liveProgrammeStartMillis = liveProgStart,
        )
        // Expected: 10:30 AM (current slot)
        assertThat(frameStart).isEqualTo(tenThirtyAm)
    }

    @Test
    fun `calculateMountedFrameStartTime aligns to current half hour regardless of program start`() {
        val halfHourMs = 30 * 60 * 1000L
        val eightAm = baseTime
        val elevenAm = baseTime + 6 * halfHourMs
        val now = elevenAm + 15 * 60 * 1000L // 11:15 AM
        val movieStart = eightAm             // 8:00 AM (3+ hours ago)

        val frameStart = calculateMountedFrameStartTime(
            nowMillis = now,
            liveProgrammeStartMillis = movieStart,
        )
        // Expected: 11:00 AM (current half hour)
        assertThat(frameStart).isEqualTo(elevenAm)
    }

    @Test
    fun `calculateInitialScrollOffsetPx converts frameStart offset to exact pixels`() {
        val windowStart = baseTime
        val frameStart = baseTime + 60 * 60_000L // 60 minutes into the window
        val minuteDp = 7.0f
        val density = 2.0f

        // Expected: 60 min * 7 dp/min * 2 px/dp = 840 px
        val px = calculateInitialScrollOffsetPx(
            windowStartMillis = windowStart,
            frameStartMillis = frameStart,
            minuteDp = minuteDp,
            density = density,
        )
        assertThat(px).isEqualTo(840)
    }
}
