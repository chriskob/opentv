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
}
