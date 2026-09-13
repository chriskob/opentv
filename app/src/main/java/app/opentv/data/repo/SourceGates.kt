/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.model.Source

/**
 * What a playlist is allowed to contribute, given the app-wide Content toggles.
 *
 * The playlist's own checkbox is the user's explicit statement about that playlist, and the global
 * toggle is the app-wide one — both have to agree before anything is fetched. Pulled out of
 * [CatalogRepository] so this rule lives in exactly one place and can be tested without a database.
 *
 * Getting this wrong is what let a playlist the user had excluded from Live TV import its channels
 * anyway: the rule was written out inline in several places, and one of them missed the flag.
 */
object SourceGates {

    /** Live channels: the global Live TV toggle AND this playlist's Channels box. */
    fun live(liveEnabled: Boolean, source: Source): Boolean = liveEnabled && source.includeLive

    /** Movies: the global Movies toggle AND this playlist's Movies box. */
    fun movies(moviesEnabled: Boolean, source: Source): Boolean = moviesEnabled && source.includeVod

    /** Shows: the global Shows toggle AND this playlist's Shows box. */
    fun series(seriesEnabled: Boolean, source: Source): Boolean =
        seriesEnabled && source.includeSeries

    /** True when either VOD half is wanted for this playlist — i.e. a VOD pass is worth running. */
    fun anyVod(moviesEnabled: Boolean, seriesEnabled: Boolean, source: Source): Boolean =
        movies(moviesEnabled, source) || series(seriesEnabled, source)

    /**
     * Which of the three catalogues a list is being built for.
     *
     * Distinct from the global toggles: [live]/[movies]/[series] answer "may we *fetch* this?",
     * while this answers "may this playlist's name appear in *this* list?". A playlist the user
     * added for its films only must not put its name or its categories into the TV lists — the
     * picker is per-catalogue, so its membership has to be too.
     */
    enum class ContentType { LIVE, MOVIES, SERIES }

    /**
     * Whether [source] contributes anything to the [contentType] catalogue, by the playlist's own
     * boxes alone.
     *
     * Deliberately ignores the app-wide Content toggles. Those decide what the app fetches; this
     * decides what a playlist is *made of*. Folding the global toggle in here would make switching
     * the Movies section off empty the Movies provider list, and switching it back on refill it —
     * a list whose membership depends on an unrelated switch.
     */
    fun contributes(contentType: ContentType, source: Source): Boolean = when (contentType) {
        ContentType.LIVE -> source.includeLive
        ContentType.MOVIES -> source.includeVod
        ContentType.SERIES -> source.includeSeries
    }

    /**
     * The ids of the playlists allowed to contribute to [contentType].
     *
     * The callers scope category rows by membership in this set rather than by re-deriving the
     * rule: "exclude the playlists whose Channels box is off" was written out inline in one list
     * and simply omitted from another (the channel manager), so that list kept showing a
     * films-only playlist's categories. One rule, one place.
     */
    fun contributingSourceIds(contentType: ContentType, sources: List<Source>): Set<Long> =
        sources.filter { contributes(contentType, it) }.map { it.id }.toSet()
}
