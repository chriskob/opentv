/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One torrent as TorrServer reports it (`state.TorrentStatus` in the engine's source).
 *
 * Every field is defaulted, and unknown keys are ignored by the parser, because this is the wire
 * shape of a *separate program* that versions on its own schedule: a field the engine adds must not
 * break playback, and a field it renames must degrade to "not shown" rather than fail the request.
 * The `omitempty` tags on the Go side mean an absent key is a real zero, not a missing answer — a
 * file of the first torrent carries no `id` at all, for instance, so 0 is the right default there.
 */
@Serializable
data class TorrentStatus(
    val hash: String = "",
    val title: String = "",
    val name: String = "",
    val stat: Int = STAT_UNKNOWN,
    @SerialName("stat_string") val statString: String = "",
    @SerialName("torrent_size") val torrentSize: Long = 0,
    @SerialName("loaded_size") val loadedSize: Long = 0,
    @SerialName("preloaded_bytes") val preloadedBytes: Long = 0,
    @SerialName("preload_size") val preloadSize: Long = 0,
    @SerialName("download_speed") val downloadSpeed: Double = 0.0,
    @SerialName("upload_speed") val uploadSpeed: Double = 0.0,
    @SerialName("total_peers") val totalPeers: Int = 0,
    @SerialName("active_peers") val activePeers: Int = 0,
    @SerialName("connected_seeders") val connectedSeeders: Int = 0,
    @SerialName("bytes_written") val bytesWritten: Long = 0,
    @SerialName("bytes_read_useful_data") val bytesReadUsefulData: Long = 0,
    @SerialName("file_stats") val fileStats: List<TorrentFile> = emptyList(),
) {
    companion object {
        const val STAT_UNKNOWN = -1

        /** Queued, metadata not yet fetched. */
        const val STAT_ADDED = 0

        /** Fetching the torrent's metadata (peers + info dictionary). */
        const val STAT_GETTING_INFO = 1

        /**
         * Downloading the window the player is reading. This is the state a torrent sits in for
         * most of a film: TorrServer caches a sliding window rather than the whole file.
         */
        const val STAT_PRELOAD = 2

        /** Running, with nothing requesting data right now. */
        const val STAT_WORKING = 3

        /** Stopped, still in memory. The engine closes a torrent on its own after
         *  `torrentDisconnectTimeout` seconds with no reader. */
        const val STAT_CLOSED = 4

        /** Known to the engine's database but not loaded. Emitted by `list` only. */
        const val STAT_IN_DB = 5
    }
}

/**
 * One file inside a torrent. [id] is the index the engine expects in `?index=` and in the
 * `/stream` URL — not a database id.
 */
@Serializable
data class TorrentFile(
    val id: Int = 0,
    val path: String = "",
    val length: Long = 0,
)
