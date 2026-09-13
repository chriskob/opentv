/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.RecentChannelRef
import app.opentv.core.RecentChannels
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The watched-channel bar's storage.
 *
 * Written because "sometimes on the history bar the channels will disappear" turned out to be the
 * bar destroying its own history. It stored `Channel.id` — an `autoGenerate` row id, which a guide
 * sync reassigns, because a channel a provider omits from one fetch is deleted and the next fetch
 * *inserts* it again — and it forgot every entry that failed to resolve at that instant. A channel
 * that was merely absent for one sync was therefore erased from the bar for good.
 *
 * These tests pin both halves of the fix: an entry names a channel by something a sync cannot
 * change, and an absent channel is kept rather than deleted.
 */
class RecentChannelsTest {

    @Test
    fun `an entry survives the row id being reassigned by a resync`() {
        // The entry used to hold row id 4711. After a resync the same provider channel is a new row,
        // 9930: the row was deleted when the provider left it out of a fetch and re-inserted after.
        // Nothing in the entry refers to a row id, so there is nothing for the resync to invalidate.
        val ref = RecentChannelRef(sourceId = 3, streamId = "8801")
        val stored = RecentChannels.encode(listOf(ref))

        assertThat(stored).isEqualTo("3:8801")
        assertThat(stored).doesNotContain("4711")
        assertThat(RecentChannels.decode(stored)).containsExactly(ref)
    }

    @Test
    fun `a channel the catalogue does not hold right now is kept, not forgotten`() {
        // Source 3 is still installed; its channel just is not in the channels table this instant —
        // dropped by one sync, or the table being rewritten by an import in flight. The old sweep
        // read that as "gone forever" and deleted the entry. It is not gone, so nothing is deleted.
        val refs = listOf(RecentChannelRef(sourceId = 3, streamId = "8801"))

        assertThat(RecentChannels.refsToForget(refs, liveSourceIds = setOf(3L))).isEmpty()
    }

    @Test
    fun `only a deleted playlist retires an entry`() {
        val stillInstalled = RecentChannelRef(sourceId = 3, streamId = "8801")
        val playlistGone = RecentChannelRef(sourceId = 9, streamId = "1204")

        // The user deleted playlist 9. Nothing can resolve its refs ever again, so they go.
        val forgotten = RecentChannels.refsToForget(listOf(stillInstalled, playlistGone), setOf(3L))

        assertThat(forgotten).containsExactly(playlistGone)
    }

    @Test
    fun `a stream id containing colons round-trips`() {
        // M3U channels carry `tvg:<guide id>` or `url:<hash>`, so a stream id has colons of its own.
        // Splitting on every colon, or on the last one, mangles these and silently loses the entry.
        val refs = listOf(
            RecentChannelRef(sourceId = 1, streamId = "tvg:bbc1.uk"),
            RecentChannelRef(sourceId = 1, streamId = "url:1f3c9a"),
            RecentChannelRef(sourceId = 2, streamId = "8801"),
        )

        assertThat(RecentChannels.decode(RecentChannels.encode(refs))).isEqualTo(refs)
    }

    @Test
    fun `malformed stored entries are dropped rather than throwing`() {
        // This runs at startup against whatever is in prefs, so it has to survive junk: a value from
        // an unrelated version, a truncated write, an empty slot from a join.
        assertThat(RecentChannels.decode(null)).isEmpty()
        assertThat(RecentChannels.decode("")).isEmpty()
        assertThat(RecentChannels.decode("   ")).isEmpty()

        val junk = listOf(
            "3:8801", // the only good one
            "", // empty slot between delimiters
            "nonsense", // no separator at all
            ":12", // no source id
            "abc:5", // source id that is not a number
            "7:", // no stream id
            "3:8801", // duplicate
        ).joinToString(",")

        assertThat(RecentChannels.decode(junk)).containsExactly(RecentChannelRef(3, "8801"))
    }
}
