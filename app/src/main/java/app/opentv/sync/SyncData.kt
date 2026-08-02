/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.sync

import kotlinx.serialization.Serializable

/**
 * The state two OpenTV devices exchange when they sync.
 *
 * Nothing here is keyed by a local database id — those differ from one install to the next. An
 * item is identified by something stable for the same provider: a channel/movie by its **stream
 * URL**, a profile by its **name**, a category by its **group key**. So a bundle from one device
 * makes sense on another.
 *
 * Two merge policies travel together, matching how people actually use this:
 *  - **Watch positions** are two-way, newest-wins per item (you watch on any device, resume on
 *    all), using [SyncPosition.updatedAtMillis].
 *  - **Curation** — the profile list, favourites, hidden channels and hidden categories — is
 *    hub-authoritative: you tidy it up on the hub and the followers adopt it. These are plain
 *    sets, applied as "make the follower match the hub".
 *
 * Every field defaults to empty and the reader ignores unknown keys, so a positions-only device
 * and a full v2 device interoperate without a flag day.
 */
@Serializable
data class SyncBundle(
    val version: Int = 2,
    val generatedAtMillis: Long = 0,
    /** Profile names the hub knows about. Followers add any they are missing (add-only). */
    val profiles: List<String> = emptyList(),
    /** Two-way, newest-wins resume points. */
    val positions: List<SyncPosition> = emptyList(),
    /** Hub-authoritative curation, keyed by the stable identifiers noted above. */
    val favouriteChannels: List<String> = emptyList(),
    val hiddenChannels: List<String> = emptyList(),
    val favouriteMovies: List<String> = emptyList(),
    val hiddenCategories: List<String> = emptyList(),
)

@Serializable
data class SyncPosition(
    val profile: String,
    val streamUrl: String,
    /** "movie" or "ep" — which table to resolve [streamUrl] against on the receiving device. */
    val kind: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val updatedAtMillis: Long,
)
