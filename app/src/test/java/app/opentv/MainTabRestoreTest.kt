/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.torrent.TorrentMediaKey
import app.opentv.ui.Tab
import app.opentv.ui.playerExitTabName
import app.opentv.ui.restoredTab
import app.opentv.ui.savedTabName
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * BACK out of Search, Settings or a player must never land on the Torrents tab.
 *
 * That tab is a one-off view — paste a magnet, watch, leave — and its screen aims the cursor at
 * the magnet field whenever it appears. People who were on it, opened Search or Settings and
 * pressed BACK were restored straight into the magnet box ("back always goes to the torrents
 * area"); the shell now writes down the last real content tab instead. These pins keep the saved
 * name and the restore in step.
 */
class MainTabRestoreTest {

    @Test
    fun torrents_savesAsTheLastContentTab() {
        assertThat(savedTabName(Tab.TORRENTS, Tab.LIVE)).isEqualTo(Tab.LIVE.name)
        assertThat(savedTabName(Tab.TORRENTS, Tab.MOVIES)).isEqualTo(Tab.MOVIES.name)
        assertThat(savedTabName(Tab.TORRENTS, Tab.RECORDINGS)).isEqualTo(Tab.RECORDINGS.name)
    }

    @Test
    fun contentTabs_saveAsThemselves() {
        assertThat(savedTabName(Tab.LIVE, Tab.MOVIES)).isEqualTo(Tab.LIVE.name)
        assertThat(savedTabName(Tab.SHOWS, Tab.LIVE)).isEqualTo(Tab.SHOWS.name)
        assertThat(savedTabName(Tab.RECORDINGS, Tab.LIVE)).isEqualTo(Tab.RECORDINGS.name)
    }

    @Test
    fun restore_returnsTheSavedTab() {
        assertThat(restoredTab(Tab.MOVIES.name, Tab.LIVE, Tab.LIVE)).isEqualTo(Tab.MOVIES)
        assertThat(restoredTab(Tab.LIVE.name, Tab.MOVIES, Tab.MOVIES)).isEqualTo(Tab.LIVE)
    }

    @Test
    fun restore_neverLandsOnTorrents() {
        // Saves written before the fix carry the Torrents name (or value); they must fall back
        // to the content tab, not reopen the magnet box.
        assertThat(restoredTab(Tab.TORRENTS.name, Tab.RECORDINGS, Tab.LIVE))
            .isEqualTo(Tab.RECORDINGS)
    }

    @Test
    fun restore_unknownNamesFallBackToHome() {
        assertThat(restoredTab("SOMETHING_ELSE", Tab.RECORDINGS, Tab.MOVIES)).isEqualTo(Tab.MOVIES)
        assertThat(restoredTab("", Tab.RECORDINGS, Tab.MOVIES)).isEqualTo(Tab.MOVIES)
    }

    @Test
    fun torrentPlayerExit_asksForTheTorrentsTab() {
        // BACK from a video playing out of the Torrents tab returns to that tab's own screen —
        // files and progress — by asking for it on the way out, since the saver never writes it.
        // "torrents" is the name the shell's requestedHomeTab when() maps to Tab.TORRENTS.
        assertThat(playerExitTabName(TorrentMediaKey.of("2ab5d1", 7))).isEqualTo("torrents")
        assertThat(playerExitTabName(TorrentMediaKey.of("ABC", 0))).isEqualTo("torrents")
    }

    @Test
    fun otherPlayerExits_askForNoTab() {
        // Movies, recordings and Plex come back through the saver or their own route; only a
        // torrent asks, so nothing else may be rerouted by this helper.
        assertThat(playerExitTabName("movie:12")).isNull()
        assertThat(playerExitTabName("rec:3")).isNull()
        assertThat(playerExitTabName("plex:9")).isNull()
        assertThat(playerExitTabName("")).isNull()
    }
}
