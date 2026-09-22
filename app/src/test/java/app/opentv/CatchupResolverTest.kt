/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.CatchupResolver
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Catch-up URL resolution across the provider modes TiviMate supports: native Xtream
 * timeshift, M3U `catchup-source` templates, `shift`/`append`, `xc` derivation and
 * `flussonic` derivation — plus the start-time correction and the Stalker exclusion.
 */
class CatchupResolverTest {

    private val startMillis = 1_700_000_000_000L // 2023-11-14T22:13:20Z, fixed so stamps are stable

    private fun programme() = Programme(
        feedId = 1,
        epgChannelId = "bbc1.uk",
        startUtcMillis = startMillis,
        endUtcMillis = startMillis + 60 * 60_000L,
        title = "Evening News",
    )

    private fun m3uChannel(
        // Deliberately NOT Xtream-shaped (that shape auto-derives timeshift, which would
        // shadow the mode under test).
        streamUrl: String = "http://cdn.example.net/stream/ch1.m3u8",
        tvArchive: Boolean = false,
        days: Int = 0,
        mode: String = "",
        correction: Int = 0,
        cmd: String? = null,
    ) = Channel(
        sourceId = 1,
        streamId = "tvg:bbc1.uk",
        name = "BBC One",
        categoryId = "UK",
        logoUrl = null,
        epgChannelId = "bbc1.uk",
        number = 1,
        streamUrl = streamUrl,
        tvArchive = tvArchive,
        tvArchiveDays = days,
        catchupMode = mode,
        catchupCorrectionMin = correction,
        cmd = cmd,
    )

    private fun m3uSource(url: String = "http://example.com/list.m3u") = Source(
        name = "M3U",
        kind = SourceKind.M3U,
        url = url,
    )

    @Test
    fun `stalker commands are not catch-up templates`() {
        assertThat(CatchupResolver.isCatchupTemplate("ffmpeg http://example.com/x")).isFalse()
        assertThat(CatchupResolver.isCatchupTemplate("auto http://example.com/x")).isFalse()
        assertThat(CatchupResolver.isCatchupTemplate(null)).isFalse()
        assertThat(CatchupResolver.isCatchupTemplate("")).isFalse()
        assertThat(CatchupResolver.isCatchupTemplate("http://h/timeshift/{utc}/1.ts")).isTrue()
        assertThat(CatchupResolver.isCatchupTemplate("?utc=\${start}&lutc=\${timestamp}")).isTrue()
    }

    @Test
    fun `stalker channel is not supported and resolves to null`() {
        val ch = m3uChannel(cmd = "ffmpeg http://example.com/x")
        assertThat(CatchupResolver.isSupported(m3uSource(), ch)).isFalse()
        assertThat(CatchupResolver.resolve(m3uSource(), ch, programme())).isNull()
    }

    @Test
    fun `native xtream builds the timeshift url`() {
        val src = Source(name = "X", kind = SourceKind.XTREAM, url = "http://panel.example.com:8080", username = "u", password = "p")
        val ch = m3uChannel().copy(streamId = "12345", sourceId = 2)
        val url = CatchupResolver.resolve(src, ch, programme())
        assertThat(url).startsWith("http://panel.example.com:8080/timeshift/u/p/60/")
        assertThat(url).endsWith("/12345.ts")
    }

    @Test
    fun `template placeholders are substituted`() {
        val ch = m3uChannel(
            tvArchive = true,
            days = 3,
            cmd = "http://arch.example.com/c/{utc}/{duration}/{catchup-id}.m3u8",
        )
        val url = CatchupResolver.resolve(m3uSource(), ch, programme())
        val startSec = startMillis / 1000L
        assertThat(url).isEqualTo("http://arch.example.com/c/$startSec/3600/bbc1.uk.m3u8")
    }

    @Test
    fun `shift mode needs no template`() {
        val ch = m3uChannel(
            streamUrl = "http://cdn.example.net/stream/ch1.m3u8",
            mode = "shift",
        )
        val url = CatchupResolver.resolve(m3uSource(), ch, programme())
        val startSec = startMillis / 1000L
        assertThat(url).startsWith("http://cdn.example.net/stream/ch1.m3u8?utc=$startSec&lutc=")
    }

    @Test
    fun `xc mode derives the timeshift url from a panel-shaped stream url`() {
        val ch = m3uChannel(
            streamUrl = "http://panel.example.com/live/alice/secret123/777.ts",
            mode = "xc",
        )
        val url = CatchupResolver.resolve(m3uSource(), ch, programme())
        assertThat(url).startsWith("http://panel.example.com/timeshift/alice/secret123/60/")
        assertThat(url).endsWith("/777.ts")
    }

    @Test
    fun `flussonic mode derives the archive url in seconds`() {
        val ch = m3uChannel(
            streamUrl = "http://flus.example.com/ch1/index.m3u8",
            mode = "flussonic",
        )
        val url = CatchupResolver.resolve(m3uSource(), ch, programme())
        val startSec = startMillis / 1000L
        assertThat(url).isEqualTo("http://flus.example.com/ch1/archive-$startSec-3600.m3u8")
    }

    @Test
    fun `global plus channel correction shifts the stamp`() {
        val src = Source(name = "X", kind = SourceKind.XTREAM, url = "http://panel.example.com", username = "u", password = "p")
        val ch = m3uChannel(correction = 2).copy(streamId = "99", sourceId = 3)
        val plain = CatchupResolver.resolve(src, ch, programme(), globalCorrectionMin = 0)!!
        val shifted = CatchupResolver.resolve(src, ch, programme(), globalCorrectionMin = -5)!!
        assertThat(plain).isNotEqualTo(shifted)
        // +2 min against the 22:13 start; -5 + 2 = -3 min net.
        assertThat(plain).contains("2023-11-14:22-15")
        assertThat(shifted).contains("2023-11-14:22-10")
    }
}
