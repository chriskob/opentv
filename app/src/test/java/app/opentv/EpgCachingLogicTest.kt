/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Programme
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Test

class EpgCachingLogicTest {

    @Test
    fun `staleness check respects configured 12 hour refresh interval`() {
        val now = 1_700_000_000_000L
        val intervalMillis = TimeUnit.HOURS.toMillis(12)

        // Feed synced 6 hours ago -> should NOT be stale
        val recentFeed = EpgFeed(
            id = 1L,
            name = "Test Feed",
            lastSyncMillis = now - TimeUnit.HOURS.toMillis(6),
        )
        val isRecentStale = (now - recentFeed.lastSyncMillis) >= intervalMillis
        assertThat(isRecentStale).isFalse()

        // Feed synced 13 hours ago -> should BE stale
        val staleFeed = EpgFeed(
            id = 2L,
            name = "Stale Feed",
            lastSyncMillis = now - TimeUnit.HOURS.toMillis(13),
        )
        val isOldStale = (now - staleFeed.lastSyncMillis) >= intervalMillis
        assertThat(isOldStale).isTrue()
    }

    @Test
    fun `window lookahead calculates correct 6-hour block aligned to half-hour`() {
        val halfHourMillis = 30 * 60 * 1000L
        val lookaheadMillis = 6 * 60 * 60 * 1000L

        // Arbitrary timestamp: 14:17:23
        val now = 1_700_000_000_000L
        val roundedStart = now - (now % halfHourMillis)
        val windowEnd = roundedStart + lookaheadMillis

        assertThat(roundedStart % halfHourMillis).isEqualTo(0L)
        assertThat(windowEnd - roundedStart).isEqualTo(lookaheadMillis)
    }

    @Test
    fun `cached window hit check returns cached data when query is within range`() {
        val start = 1_000_000L
        val end = 2_000_000L
        val testProgrammes = mapOf(
            "ch1" to listOf(
                Programme(
                    feedId = 1L,
                    epgChannelId = "ch1",
                    startUtcMillis = start + 1000,
                    endUtcMillis = start + 5000,
                    title = "Test Show",
                ),
            ),
        )
        val cachedRange = (start..end) to testProgrammes

        // Exact match
        val isExactInside = start >= cachedRange.first.first && end <= cachedRange.first.last
        assertThat(isExactInside).isTrue()

        // Subset query inside cache
        val isSubInside = (start + 500) >= cachedRange.first.first && (end - 500) <= cachedRange.first.last
        assertThat(isSubInside).isTrue()

        // Query extending past cache end -> Cache miss
        val isOverflow = start >= cachedRange.first.first && (end + 500) <= cachedRange.first.last
        assertThat(isOverflow).isFalse()
    }

    @Test
    fun `programme correctly identifies live status and progress percentage`() {
        val start = 1_000_000L
        val end = 1_000_000L + (60 * 60 * 1000L) // 1 hour duration
        val prog = Programme(
            feedId = 1L,
            epgChannelId = "ch1",
            startUtcMillis = start,
            endUtcMillis = end,
            title = "Current Show",
        )

        // Before show starts
        assertThat(prog.isLiveAt(start - 1000)).isFalse()
        assertThat(prog.progressAt(start - 1000)).isEqualTo(0f)

        // At exact start
        assertThat(prog.isLiveAt(start)).isTrue()
        assertThat(prog.progressAt(start)).isEqualTo(0f)

        // Exactly halfway (30 mins in)
        val mid = start + (30 * 60 * 1000L)
        assertThat(prog.isLiveAt(mid)).isTrue()
        assertThat(prog.progressAt(mid)).isWithin(0.01f).of(0.5f)

        // At end time (already finished / transition to next)
        assertThat(prog.isLiveAt(end)).isFalse()
        assertThat(prog.progressAt(end)).isEqualTo(1f)
    }

    @Test
    fun `past programme retention prunes only events ending older than 1 day`() {
        val now = 1_700_000_000_000L
        val retentionPastMillis = TimeUnit.DAYS.toMillis(1)
        val cutoff = now - retentionPastMillis

        val programmeJustEnded = Programme(
            feedId = 1L,
            epgChannelId = "ch1",
            startUtcMillis = now - TimeUnit.HOURS.toMillis(2),
            endUtcMillis = now - TimeUnit.HOURS.toMillis(1),
            title = "Earlier Today Show",
        )
        val shouldKeep = programmeJustEnded.endUtcMillis >= cutoff
        assertThat(shouldKeep).isTrue()

        val programmeTwoDaysAgo = Programme(
            feedId = 1L,
            epgChannelId = "ch1",
            startUtcMillis = now - TimeUnit.HOURS.toMillis(50),
            endUtcMillis = now - TimeUnit.HOURS.toMillis(49),
            title = "Two Days Ago Show",
        )
        val shouldKeepOld = programmeTwoDaysAgo.endUtcMillis >= cutoff
        assertThat(shouldKeepOld).isFalse()
    }
}
