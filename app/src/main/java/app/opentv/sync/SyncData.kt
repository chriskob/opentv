/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.sync

import kotlinx.serialization.Serializable

/**
 * The watch-history bundle two devices exchange over the LAN.
 *
 * Nothing here is keyed by a local database id — those differ from one install to the next.
 * A movie/episode is identified by its **stream URL** (stable for the same provider) and a
 * profile by its **name**, so a bundle from one device makes sense on another. Newest-wins per
 * item on merge, using [SyncPosition.updatedAtMillis].
 */
@Serializable
data class SyncBundle(
    val version: Int = 1,
    val positions: List<SyncPosition> = emptyList(),
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
