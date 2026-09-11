/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.repo.SourceGates
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that decides whether a playlist contributes channels, movies or shows.
 *
 * Written for the reported bug: adding a playlist with TV channels unticked still imported its
 * channels and their live categories, because the rule was spelled out inline in several places and
 * the playlist's own flag was missed. A playlist's box is the user's explicit instruction about
 * that playlist — it must be honoured, and it must be able to switch a section off on its own.
 */
class SourceGatesTest {

    private fun source(
        live: Boolean = true,
        vod: Boolean = true,
        series: Boolean = true,
    ) = Source(
        name = "Test",
        kind = SourceKind.XTREAM,
        url = "http://example.com:8080",
        username = "user",
        password = "pass",
        includeLive = live,
        includeVod = vod,
        includeSeries = series,
    )

    @Test
    fun `playlist with channels unticked contributes no live channels`() {
        val s = source(live = false)
        assertThat(SourceGates.live(liveEnabled = true, source = s)).isFalse()
    }

    @Test
    fun `global Live TV off excludes channels even when the playlist wants them`() {
        val s = source(live = true)
        assertThat(SourceGates.live(liveEnabled = false, source = s)).isFalse()
    }

    @Test
    fun `channels import only when both the global toggle and the playlist agree`() {
        assertThat(SourceGates.live(liveEnabled = true, source = source(live = true))).isTrue()
    }

    @Test
    fun `a playlist can request VOD without channels`() {
        val vodOnly = source(live = false, vod = true, series = true)
        assertThat(SourceGates.live(liveEnabled = true, source = vodOnly)).isFalse()
        assertThat(SourceGates.anyVod(moviesEnabled = true, seriesEnabled = true, source = vodOnly))
            .isTrue()
    }

    @Test
    fun `movies and shows are gated independently per playlist`() {
        val moviesOnly = source(live = true, vod = true, series = false)
        assertThat(SourceGates.movies(moviesEnabled = true, source = moviesOnly)).isTrue()
        assertThat(SourceGates.series(seriesEnabled = true, source = moviesOnly)).isFalse()
        assertThat(SourceGates.anyVod(moviesEnabled = true, seriesEnabled = true, source = moviesOnly))
            .isTrue()
    }

    @Test
    fun `no VOD pass runs when the playlist wants neither half`() {
        val liveOnly = source(live = true, vod = false, series = false)
        assertThat(SourceGates.anyVod(moviesEnabled = true, seriesEnabled = true, source = liveOnly))
            .isFalse()
    }

    @Test
    fun `VOD is not fetched when nothing is enabled globally`() {
        val s = source(live = true, vod = true, series = true)
        assertThat(SourceGates.anyVod(moviesEnabled = false, seriesEnabled = false, source = s))
            .isFalse()
    }

    @Test
    fun `one playlist's choice cannot drag another's library in`() {
        val noVod = source(live = true, vod = false, series = false)
        val vod = source(live = true, vod = true, series = true)
        // Same global toggles, two playlists, two different answers.
        assertThat(SourceGates.anyVod(moviesEnabled = true, seriesEnabled = true, source = noVod))
            .isFalse()
        assertThat(SourceGates.anyVod(moviesEnabled = true, seriesEnabled = true, source = vod))
            .isTrue()
    }
}
