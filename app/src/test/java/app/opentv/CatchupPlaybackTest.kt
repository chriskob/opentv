/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.CatchupPlayback
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CatchupPlaybackTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun session(start: Long, end: Long) = CatchupPlayback(
        channelId = 1L,
        channelName = "FOX",
        programmeTitle = "The Five",
        startUtcMillis = start,
        endUtcMillis = end,
        url = "http://example/timeshift.ts",
        userAgent = "OpenTV/0.1 (Android)",
    )

    @Test
    fun `still-airing programme behind the live edge has not reached it`() {
        val archive = session(start = now - 30 * minute, end = now + 30 * minute)
        val behind = 20 * minute
        assertThat(archive.reachedLiveEdge(behind, now)).isFalse()
    }

    @Test
    fun `still-airing programme within tolerance of the live edge has reached it`() {
        val archive = session(start = now - 30 * minute, end = now + 30 * minute)
        val oneSecondBehindLive = 30 * minute - 1_000L
        assertThat(archive.reachedLiveEdge(oneSecondBehindLive, now)).isTrue()
    }

    @Test
    fun `finished programme never reaches the live edge`() {
        val archive = session(start = now - 61 * minute, end = now - 1 * minute)
        assertThat(archive.reachedLiveEdge(Long.MAX_VALUE, now)).isFalse()
    }

    @Test
    fun `positive correction moves the live edge earlier`() {
        val archive = session(start = now - 30 * minute, end = now + 30 * minute)
        val correction = 5 * minute
        val livePosition = 25 * minute
        assertThat(archive.reachedLiveEdge(livePosition, now, correction)).isTrue()
    }
}
