/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.data.model

/**
 * One library section on a Plex server, as reported by `/library/sections`.
 *
 * Plex splits a server into sections and there is no single "movies" collection: a server can have
 * several movie libraries ("Movies", "4K Movies", "Family"), and OpenTV treats each as its own
 * source of recently-added items rather than trying to merge them.
 */
data class PlexSection(
    val key: String,
    val title: String,
    /** Plex's own type token: `movie` or `show`. Anything else is not something we can list. */
    val type: String,
) {
    val isMovies: Boolean get() = type.equals("movie", ignoreCase = true)
    val isShows: Boolean get() = type.equals("show", ignoreCase = true)
}

/**
 * One item in a section's `/recentlyAdded` feed.
 *
 * [ratingKey] is the stable identity Plex uses for everything - it is what detail lookups and play
 * requests are addressed by - so it is what OpenTV stores as the stream id.
 */
data class PlexRecentItem(
    val ratingKey: String,
    val title: String,
    val type: String,
    val year: Int? = null,
    /** Plex's own summary, when the library has one. */
    val summary: String? = null,
    val durationMillis: Long? = null,
    /** Library key (the `guid` of the library section the item belongs to). */
    val librarySectionId: String? = null,
    val addedAtEpochSeconds: Long? = null,
    /**
     * Path to the poster, relative to the server (e.g. `/library/metadata/1234/thumb/5678`).
     * Relative on purpose: it needs the server address and the token to become a real URL, and
     * both can change without the library changing. See [PlexUrls.image].
     */
    val thumbPath: String? = null,
    val artPath: String? = null,
    /** For shows, so a show can be matched to its episodes later. */
    val parentRatingKey: String? = null,
    val viewOffsetMillis: Long? = null,
    val viewCount: Int? = null,
) {
    val isMovie: Boolean get() = type.equals("movie", ignoreCase = true)
    val isShow: Boolean get() = type.equals("show", ignoreCase = true)
}

/** One library on a Plex server, as offered to the viewer on the Plex shelf. */
data class PlexLibraryOption(
    val sourceId: Long,
    val key: String,
    val title: String,
    val isMovies: Boolean,
    val isEnabled: Boolean,
)

/** The playable file behind a Plex item, from `/library/metadata/{ratingKey}`. */
data class PlexMediaPart(
    /** Server-relative path to the file, e.g. `/library/parts/1234/5678/file.mkv`. */
    val key: String,
    val container: String? = null,
    val fileSizeBytes: Long? = null,
    val durationMillis: Long? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
)
