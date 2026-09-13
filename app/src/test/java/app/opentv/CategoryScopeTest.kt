/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.repo.SourceGates
import app.opentv.data.repo.SourceGates.ContentType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which catalogue a playlist is allowed to appear in.
 *
 * Written for the reported bug: a playlist added as Movies/Shows — with TV channels unticked — still
 * showed up in the TV category list, and a playlist added for live TV alone could appear in the
 * Movies/Shows lists. The cause was that the content-type filter was written out inline at one call
 * site (the guide's rail) and simply missing from the others (the channel manager's rail, and both
 * VOD category lists), so the lists disagreed about what belonged in them.
 *
 * [SourceGates.contributingSourceIds] is the single rule those lists now share, so these tests pin
 * the membership down in one place. Note it is deliberately about the playlist's own boxes only —
 * the app-wide Content toggles decide what is *fetched*, not what a playlist *is*, and folding them
 * in here would make a provider list empty itself when an unrelated section was switched off.
 */
class CategoryScopeTest {

    private fun source(
        id: Long,
        live: Boolean = true,
        vod: Boolean = true,
        series: Boolean = true,
    ) = Source(
        id = id,
        name = "Playlist $id",
        kind = SourceKind.XTREAM,
        url = "http://example.com:8080",
        username = "user",
        password = "pass",
        includeLive = live,
        includeVod = vod,
        includeSeries = series,
    )

    @Test
    fun `a playlist added for films only is not part of the TV catalogue`() {
        val filmsOnly = source(id = 1, live = false, vod = true, series = true)
        assertThat(SourceGates.contributes(ContentType.LIVE, filmsOnly)).isFalse()
        assertThat(SourceGates.contributes(ContentType.MOVIES, filmsOnly)).isTrue()
        assertThat(SourceGates.contributes(ContentType.SERIES, filmsOnly)).isTrue()
    }

    @Test
    fun `a playlist added for live TV only is not part of the Movies or Shows catalogues`() {
        val liveOnly = source(id = 1, live = true, vod = false, series = false)
        assertThat(SourceGates.contributes(ContentType.LIVE, liveOnly)).isTrue()
        assertThat(SourceGates.contributes(ContentType.MOVIES, liveOnly)).isFalse()
        assertThat(SourceGates.contributes(ContentType.SERIES, liveOnly)).isFalse()
    }

    @Test
    fun `a films-only playlist is excluded from the TV source ids`() {
        val filmsOnly = source(id = 1, live = false)
        val tv = source(id = 2, live = true, vod = false, series = false)
        val ids = SourceGates.contributingSourceIds(ContentType.LIVE, listOf(filmsOnly, tv))
        assertThat(ids).containsExactly(2L)
    }

    @Test
    fun `a TV-only playlist is excluded from the movies and shows source ids`() {
        val tv = source(id = 1, live = true, vod = false, series = false)
        val vod = source(id = 2, live = true)
        assertThat(SourceGates.contributingSourceIds(ContentType.MOVIES, listOf(tv, vod)))
            .containsExactly(2L)
        assertThat(SourceGates.contributingSourceIds(ContentType.SERIES, listOf(tv, vod)))
            .containsExactly(2L)
    }

    @Test
    fun `movies and shows are scoped apart from each other`() {
        // Ticking Movies without Shows must not put the playlist in the Shows list — the "vice
        // versa" half of the report, where a category row outlived the box that put it there.
        val moviesOnly = source(id = 1, live = false, vod = true, series = false)
        val showsOnly = source(id = 2, live = false, vod = false, series = true)
        val all = listOf(moviesOnly, showsOnly)
        assertThat(SourceGates.contributingSourceIds(ContentType.MOVIES, all)).containsExactly(1L)
        assertThat(SourceGates.contributingSourceIds(ContentType.SERIES, all)).containsExactly(2L)
    }

    @Test
    fun `a playlist with every box unticked is in no catalogue`() {
        val nothing = source(id = 1, live = false, vod = false, series = false)
        for (type in ContentType.entries) {
            assertThat(SourceGates.contributes(type, nothing)).isFalse()
            assertThat(SourceGates.contributingSourceIds(type, listOf(nothing))).isEmpty()
        }
    }

    @Test
    fun `membership is the playlist's boxes, not the app-wide toggles`() {
        // Same boxes, and the rule answers the same either way: contributes() takes no global
        // toggle, so a section switched off in Settings cannot empty a provider list.
        val filmsOnly = source(id = 1, live = false, vod = true, series = false)
        assertThat(SourceGates.contributes(ContentType.LIVE, filmsOnly)).isFalse()
        assertThat(SourceGates.contributingSourceIds(ContentType.LIVE, listOf(filmsOnly))).isEmpty()
    }
}
