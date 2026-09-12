/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Channel
import app.opentv.data.remote.XtreamPanelDiscovery
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Finding the panel behind a playlist, and trusting it only as far as it should be trusted.
 *
 * The failure this guards against is a *plausible wrong answer*: the panel's address is inferred
 * from URL shape, so a CDN that happens to use the same shape, a playlist with only a couple of
 * panel URLs, or an id that is not an id would all otherwise produce a confident call to the wrong
 * server with credentials lifted out of a URL that merely looked similar. Every case below is either
 * "this is the panel" or "refuse", never "probably".
 */
class XtreamPanelDiscoveryTest {

    private fun channel(
        url: String,
        tvArchive: Boolean = false,
        days: Int = 0,
        cmd: String? = null,
    ) = Channel(
        sourceId = 1,
        streamId = "s1",
        name = "Some Channel",
        categoryId = "US GENERAL",
        logoUrl = null,
        epgChannelId = null,
        number = 1,
        streamUrl = url,
        tvArchive = tvArchive,
        tvArchiveDays = days,
        cmd = cmd,
    )

    private fun panelUrls(host: String, count: Int) =
        (1..count).map { "http://$host/live/alice/secret123/$it.ts" }

    @Test
    fun `the panel in a playlist is found with its credentials`() {
        val urls = panelUrls("panel.example.com:8080", 12) +
            listOf("http://cdn.example.net/hls/x/master.m3u8")

        val found = XtreamPanelDiscovery.candidates(urls)

        assertThat(found).hasSize(1)
        assertThat(found.first().baseUrl).isEqualTo("http://panel.example.com:8080")
        assertThat(found.first().username).isEqualTo("alice")
        assertThat(found.first().password).isEqualTo("secret123")
    }

    @Test
    fun `a playlist url without the live marker, and on port 80, is still recognised`() {
        val found = XtreamPanelDiscovery.candidates(panelUrls("panel.example.com", 10))

        assertThat(found).hasSize(1)
        // Port 80 is the scheme's default, so it must not end up in the base URL.
        assertThat(found.first().baseUrl).isEqualTo("http://panel.example.com")
    }

    @Test
    fun `urls that are not the panel's own shape are refused`() {
        val urls = listOf(
            "http://cdn.example.net/hls/ch1/index.m3u8",
            "https://www.dropbox.com/s/abc/file.ts",
            "http://host/live/user/pass/movie.mp4",
            "http://host/live/user/pass",
            "http://host/",
        )

        assertThat(XtreamPanelDiscovery.candidates(urls)).isEmpty()
    }

    @Test
    fun `a couple of panel-shaped urls among many is not enough to claim a panel`() {
        val urls = panelUrls("panel.example.com", 2) +
            (1..60).map { "http://cdn.example.net/hls/ch$it/index.m3u8" }

        assertThat(XtreamPanelDiscovery.candidates(urls)).isEmpty()
    }

    @Test
    fun `stream ids are read from the end of the url`() {
        assertThat(XtreamPanelDiscovery.streamIdOf("http://h/live/u/p/173176.ts")).isEqualTo("173176")
        assertThat(XtreamPanelDiscovery.streamIdOf("http://h/live/u/p/173176")).isEqualTo("173176")
        assertThat(XtreamPanelDiscovery.streamIdOf("http://cdn/hls/master.m3u8")).isNull()
    }

    @Test
    fun `archive days are stamped onto the matching channels and nothing else`() {
        val channels = listOf(
            channel("http://panel.example.com/live/alice/secret123/111.ts"),
            channel("http://panel.example.com/live/alice/secret123/222.ts"),
            // Already declared by the playlist itself — its own answer stands.
            channel("http://panel.example.com/live/alice/secret123/333.ts", tvArchive = true, days = 3),
            // Not a panel URL at all, so nothing to match it against.
            channel("http://cdn.example.net/hls/live/index.m3u8"),
        )
        val days = mapOf("111" to 7, "222" to 1, "333" to 14)

        val (updated, added) = XtreamPanelDiscovery.applyArchive(channels, days)

        assertThat(added).isEqualTo(2)
        assertThat(updated[0].tvArchive).isTrue()
        assertThat(updated[0].tvArchiveDays).isEqualTo(7)
        assertThat(updated[1].tvArchiveDays).isEqualTo(1)
        assertThat(updated[2].tvArchiveDays).isEqualTo(3)
        assertThat(updated[3].tvArchive).isFalse()
    }

    @Test
    fun `a playlist that declared catch-up is left alone when the panel disagrees`() {
        val declared = channel(
            "http://panel.example.com/live/alice/secret123/111.ts",
            tvArchive = true,
            days = 2,
            cmd = "http://panel.example.com/timeshift/alice/secret123/{utc}/111.ts",
        )

        val (updated, added) = XtreamPanelDiscovery.applyArchive(listOf(declared), mapOf("111" to 30))

        assertThat(added).isEqualTo(0)
        assertThat(updated.first().tvArchiveDays).isEqualTo(2)
        assertThat(updated.first().cmd).isNotNull()
    }

    @Test
    fun `an empty panel answer changes nothing`() {
        val channels = listOf(channel("http://panel.example.com/live/alice/secret123/111.ts"))

        val (updated, added) = XtreamPanelDiscovery.applyArchive(channels, emptyMap())

        assertThat(added).isEqualTo(0)
        assertThat(updated).isEqualTo(channels)
    }
}
