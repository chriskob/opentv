/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.torrent

/**
 * The media key a torrent play carries through the app.
 *
 * The VOD player's route takes a `key` and a `url`; the key is what identifies the item for resume
 * positions, watch history and — here — knowing that a play came from the torrent engine. It is
 * `torrent:<info hash>:<file id>`, alongside the existing `movie:`, `rec:` and `plex:` keys, so
 * every screen that inspects a key keeps working without knowing torrents exist.
 *
 * The hash is the engine's own lowercase hex info hash, not something OpenTV derives: a magnet's
 * `xt` can be base32 or a bare hash, and only the engine's answer is canonical.
 */
object TorrentMediaKey {

    const val PREFIX = "torrent:"

    data class Ref(val hash: String, val fileId: Int)

    fun of(hash: String, fileId: Int): String = "$PREFIX$hash:$fileId"

    fun isTorrentKey(mediaKey: String): Boolean = mediaKey.startsWith(PREFIX)

    /** The hash and file id in [mediaKey], or null when it isn't a torrent key. */
    fun parse(mediaKey: String): Ref? {
        if (!isTorrentKey(mediaKey)) return null
        val body = mediaKey.removePrefix(PREFIX)
        val separator = body.lastIndexOf(':')
        if (separator <= 0) return null
        val hash = body.substring(0, separator)
        val fileId = body.substring(separator + 1).toIntOrNull() ?: return null
        if (hash.isBlank()) return null
        return Ref(hash, fileId)
    }
}
