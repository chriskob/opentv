/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.torrent.TorrentMediaKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The `torrent:<hash>:<file>` media key, which is what tells the player — and the teardown path in
 * VodPlayerScreen — that a play came from the torrent engine.
 */
class TorrentMediaKeyTest {

    @Test
    fun `round-trips a hash and a file id`() {
        val key = TorrentMediaKey.of("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c", 3)

        assertThat(key).isEqualTo("torrent:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c:3")
        val ref = TorrentMediaKey.parse(key)!!
        assertThat(ref.hash).isEqualTo("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")
        assertThat(ref.fileId).isEqualTo(3)
    }

    @Test
    fun `recognises only torrent keys`() {
        // The other key shapes in this app must stay untouched: the player asks this question about
        // every play, whatever its source.
        assertThat(TorrentMediaKey.isTorrentKey("torrent:abc:0")).isTrue()
        assertThat(TorrentMediaKey.parse("movie:tmdb:603:1")).isNull()
        assertThat(TorrentMediaKey.parse("plex:home:1234")).isNull()
        assertThat(TorrentMediaKey.parse("optvrec://2026-10-17/channel")).isNull()
        assertThat(TorrentMediaKey.parse("channel:42")).isNull()
    }

    @Test
    fun `refuses a key it cannot read`() {
        assertThat(TorrentMediaKey.parse("torrent:")).isNull()
        assertThat(TorrentMediaKey.parse("torrent:abc")).isNull()
        assertThat(TorrentMediaKey.parse("torrent::1")).isNull()
        assertThat(TorrentMediaKey.parse("torrent:abc:notanumber")).isNull()
    }

    @Test
    fun `keeps the file id when the hash itself has no colon`() {
        // A hash never contains a colon, but a media key is a string other code can hand us, so the
        // parse splits on the last one and a hash-shaped prefix is not silently accepted.
        val ref = TorrentMediaKey.parse("torrent:has:colons:7")!!
        assertThat(ref.hash).isEqualTo("has:colons")
        assertThat(ref.fileId).isEqualTo(7)
    }
}
