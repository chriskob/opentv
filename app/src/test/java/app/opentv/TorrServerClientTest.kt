/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.TorrentFile
import app.opentv.data.model.TorrentStatus
import app.opentv.data.remote.TorrServerClient
import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

/**
 * The torrent engine's wire shapes, as the engine actually sends them.
 *
 * The bodies here are written from TorrServer's own `server/torr/state/state.go` and from a
 * `POST /settings {action:get}` against a real MatriX.147 build, not from a summary of the API. The
 * details that matter are the ones a hand-written guess gets wrong: the Go `omitempty` tags mean an
 * absent key is a real zero (a torrent's first file carries no `id` at all), and the engine sends
 * fields OpenTV does not model (`bytes_written_data`) which must not fail the parse.
 */
class TorrServerClientTest {

    private val magnet = "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big Buck Bunny"

    private val statusJson = """
        {
          "title": "Big Buck Bunny",
          "category": "",
          "poster": "",
          "timestamp": 1759081200,
          "name": "Big Buck Bunny",
          "hash": "dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c",
          "stat": 2,
          "stat_string": "Torrent preload",
          "loaded_size": 40265318,
          "torrent_size": 276445467,
          "preload_size": 33554432,
          "preloaded_bytes": 12582912,
          "download_speed": 1873920,
          "upload_speed": 0,
          "active_peers": 14,
          "connected_seeders": 6,
          "total_peers": 41,
          "bytes_written_data": 0,
          "file_stats": [
            {"path": "Big Buck Bunny/Big Buck Bunny.mkv", "length": 276445467},
            {"id": 1, "path": "Big Buck Bunny/poster.jpg", "length": 42069}
          ]
        }
    """.trimIndent()

    // -------------------------------------------------------------- status parsing

    @Test
    fun `parses a status the engine sent, file with no id included`() {
        val status = TorrServerClient.parseStatus(statusJson)

        assertThat(status).isNotNull()
        assertThat(status!!.hash).isEqualTo("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")
        assertThat(status.stat).isEqualTo(TorrentStatus.STAT_PRELOAD)
        assertThat(status.statString).isEqualTo("Torrent preload")
        assertThat(status.torrentSize).isEqualTo(276445467L)
        assertThat(status.downloadSpeed).isEqualTo(1873920.0)
        assertThat(status.uploadSpeed).isEqualTo(0.0)
        assertThat(status.activePeers).isEqualTo(14)
        assertThat(status.fileStats).hasSize(2)
        // The first file has no "id" key: `omitempty` drops a zero on the Go side.
        assertThat(status.fileStats[0].id).isEqualTo(0)
        assertThat(status.fileStats[0].path).isEqualTo("Big Buck Bunny/Big Buck Bunny.mkv")
        assertThat(status.fileStats[1].id).isEqualTo(1)
    }

    @Test
    fun `treats the engine's error bodies as no status at all`() {
        // A failed add answers {"error":"error parse link"} with a 200-class body, and a 404 answers
        // {"error":"..."}. Neither is a torrent, and reading one as a status would put a row on
        // screen with no hash.
        assertThat(TorrServerClient.parseStatus("""{"error":"error parse link"}""")).isNull()
        assertThat(TorrServerClient.parseStatus("{}")).isNull()
        assertThat(TorrServerClient.parseStatus("")).isNull()
        assertThat(TorrServerClient.parseStatus("<html>404 page not found</html>")).isNull()
    }

    @Test
    fun `parses an empty torrent list`() {
        assertThat(TorrServerClient.parseStatuses("[]")).isEmpty()
        assertThat(TorrServerClient.parseStatuses("not json")).isEmpty()
    }

    @Test
    fun `skips list entries that are not statuses`() {
        val body = "[$statusJson, {\"error\":\"gone\"}]"

        val statuses = TorrServerClient.parseStatuses(body)

        assertThat(statuses).hasSize(1)
        assertThat(statuses[0].title).isEqualTo("Big Buck Bunny")
    }

    // -------------------------------------------------------------- settings read-back

    @Test
    fun `reads the engine's booleans back out of a settings body`() {
        // Trimmed from a real POST /settings {action:get}. This is the read-back that lets OpenTV
        // check the engine is honouring "never upload" instead of assuming it.
        val body = """
            {"CacheSize":67108864,"ReaderReadAHead":95,"UseDisk":false,"TorrentDisconnectTimeout":30,
             "EnableBonjour":true,"DisableUPNP":false,"DisableUpload":false,"StoreSettingsInJson":true}
        """.trimIndent()

        assertThat(TorrServerClient.parseSettingBool(body, "DisableUpload")).isFalse()
        assertThat(TorrServerClient.parseSettingBool(body, "UseDisk")).isFalse()
        assertThat(TorrServerClient.parseSettingBool(body, "EnableBonjour")).isTrue()
        assertThat(TorrServerClient.parseSettingBool(body, "DisableUPNP")).isFalse()
        // A key the engine stops sending reads as "unknown", which is not a violation.
        assertThat(TorrServerClient.parseSettingBool(body, "EnableLPD")).isNull()
        assertThat(TorrServerClient.parseSettingBool("nonsense", "DisableUpload")).isNull()
    }

    // -------------------------------------------------------------- magnets and links

    @Test
    fun `normalises the info hash out of a magnet`() {
        assertThat(TorrServerClient.magnetInfoHash(magnet))
            .isEqualTo("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")
        // Upper-case hex is the same hash.
        assertThat(TorrServerClient.magnetInfoHash("magnet:?xt=urn:btih:DD8255ECDC7CA55FB0BBF81323D87062DB1F6D1C"))
            .isEqualTo("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")
        // The old 32-character base32 form stays as the engine's own parser reports it.
        assertThat(TorrServerClient.magnetInfoHash("magnet:?xt=urn:btih:3XKSVQJLNV4NDPYBHBTAJTF4YS33FL5D"))
            .isEqualTo("3XKSVQJLNV4NDPYBHBTAJTF4YS33FL5D")
        assertThat(TorrServerClient.magnetInfoHash("magnet:?dn=no%20hash%20here")).isNull()
        assertThat(TorrServerClient.magnetInfoHash("magnet:?xt=urn:btih:tooshort")).isNull()
        assertThat(TorrServerClient.magnetInfoHash("https://example.invalid/x.torrent")).isNull()
    }

    @Test
    fun `accepts exactly what the engine's link parser accepts`() {
        // server/web/api/utils/link.go: magnet, http(s), torrs://, a bare hash.
        assertThat(TorrServerClient.isTorrentLink(magnet)).isTrue()
        assertThat(TorrServerClient.isTorrentLink("magnet:?xt=urn:btih:3XKSVQJLNV4NDPYBHBTAJTF4YS33FL5D&dn=x")).isTrue()
        assertThat(TorrServerClient.isTorrentLink("https://example.invalid/film.torrent")).isTrue()
        assertThat(TorrServerClient.isTorrentLink("torrs://base64share")).isTrue()
        assertThat(TorrServerClient.isTorrentLink("dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c")).isTrue()
        assertThat(TorrServerClient.isTorrentLink("  $magnet  ")).isTrue()

        assertThat(TorrServerClient.isTorrentLink("")).isFalse()
        assertThat(TorrServerClient.isTorrentLink("hello there")).isFalse()
        assertThat(TorrServerClient.isTorrentLink("magnet:?dn=name-only")).isFalse()
        assertThat(TorrServerClient.isTorrentLink("ftp://example.invalid/x.torrent")).isFalse()
        assertThat(TorrServerClient.isTorrentLink("nn8255ecdc7ca55fb0bbf81323d87062db1f6d1c")).isFalse()
    }

    // -------------------------------------------------------------- choosing a file

    @Test
    fun `picks the torrent's videos, biggest first, and ignores the rest`() {
        val files = listOf(
            TorrentFile(0, "Show/Show.s01e01.mkv", 1_100_000_000),
            TorrentFile(1, "Show/Show.s01e02.mkv", 1_300_000_000),
            TorrentFile(2, "Show/poster.jpg", 300_000),
            TorrentFile(3, "Show/subs/english.srt", 40_000),
            TorrentFile(4, "Show/notes.nfo", 900),
            TorrentFile(5, "Show/Show.s01e03.mp4", 900_000_000),
        )

        val videos = TorrServerClient.videoFiles(files)

        assertThat(videos.map { it.id }).containsExactly(1, 0, 5).inOrder()
        assertThat(TorrServerClient.biggestVideo(files)!!.id).isEqualTo(1)
        assertThat(TorrServerClient.biggestVideo(listOf(TorrentFile(0, "a.nfo", 5)))).isNull()
    }

    @Test
    fun `knows which containers the player cannot open`() {
        // Media3 has an extractor for Matroska, MP4 and the transport-stream family; it has none for
        // AVI or the ASF family, and a torrent full of .avi files would otherwise just fail to play
        // with nothing on screen to explain it.
        assertThat(TorrServerClient.isVideoFile("Show/EP1.MKV")).isTrue()
        assertThat(TorrServerClient.isVideoFile("Show/EP1.mp4")).isTrue()
        assertThat(TorrServerClient.isVideoFile("Show/EP1.ts")).isTrue()
        assertThat(TorrServerClient.isVideoFile("Show/EP1.avi")).isFalse()
        assertThat(TorrServerClient.isUnplayableContainer("Show/EP1.AVI")).isTrue()
        assertThat(TorrServerClient.isUnplayableContainer("Show/EP1.wmv")).isTrue()
        assertThat(TorrServerClient.isUnplayableContainer("Show/EP1.mkv")).isFalse()
        assertThat(TorrServerClient.extension("Show/EP1.mkv")).isEqualTo("mkv")
    }

    // -------------------------------------------------------------- progress and formatting

    @Test
    fun `reports how much has arrived, or nothing when the size is unknown`() {
        val preloading = TorrentStatus(hash = "x", stat = 2, loadedSize = 40265318, torrentSize = 276445467)
        assertThat(TorrServerClient.percentLoaded(preloading)).isEqualTo(14)

        // Before metadata arrives there is no size to divide by, and the screen shows no bar rather
        // than a full or an empty one.
        assertThat(TorrServerClient.percentLoaded(TorrentStatus(hash = "x"))).isEqualTo(-1)

        // The cache can hold more than the torrent's own size once pieces are evicted and refetched;
        // a percentage is still a percentage.
        assertThat(
            TorrServerClient.percentLoaded(TorrentStatus(hash = "x", loadedSize = 900, torrentSize = 500)),
        ).isEqualTo(100)
    }

    @Test
    fun `formats byte counts and speeds for a 10-foot screen`() {
        assertThat(TorrServerClient.formatBytes(0)).isEqualTo("0 B")
        assertThat(TorrServerClient.formatBytes(1023)).isEqualTo("1023 B")
        assertThat(TorrServerClient.formatBytes(1024)).isEqualTo("1.0 KB")
        assertThat(TorrServerClient.formatBytes(1536)).isEqualTo("1.5 KB")
        assertThat(TorrServerClient.formatBytes(276445467)).isEqualTo("264 MB")
        assertThat(TorrServerClient.formatBytes(5L * 1024 * 1024 * 1024)).isEqualTo("5.0 GB")
        assertThat(TorrServerClient.formatSpeed(0.0)).isEmpty()
        assertThat(TorrServerClient.formatSpeed(2_097_152.0)).isEqualTo("2.0 MB/s")
    }

    // -------------------------------------------------------------- the play URL

    @Test
    fun `builds the engine's stream URL the player then fetches`() {
        val file = TorrentFile(1, "Big Buck Bunny/Big Buck Bunny.mkv", 276445467)

        val url = TorrServerClient.streamUrl("http://127.0.0.1:8090", magnet, file)!!.toHttpUrl()

        // The path must keep the real extension: that is how the player decides what it is looking at.
        assertThat(url.encodedPath).isEqualTo("/stream/Big%20Buck%20Bunny.mkv")
        assertThat(url.port).isEqualTo(8090)
        assertThat(url.queryParameter("index")).isEqualTo("1")
        // The magnet goes through percent-encoded and comes back whole, ampersands included — a magnet
        // without its `dn` or `tr` is a different request.
        assertThat(url.queryParameter("link")).isEqualTo(magnet)
        // `play` and `preload` are read for presence, not value, so an empty value is the flag.
        assertThat(url.queryParameter("play")).isEqualTo("")
        assertThat(url.queryParameter("preload")).isEqualTo("")
    }

    @Test
    fun `does not double the slash when the engine's base URL has a trailing one`() {
        val file = TorrentFile(0, "film.mkv", 10)

        val url = TorrServerClient.streamUrl("http://127.0.0.1:8090/", magnet, file)!!.toHttpUrl()

        assertThat(url.encodedPath).isEqualTo("/stream/film.mkv")
    }

    @Test
    fun `refuses to build a URL for a base that is not a URL`() {
        assertThat(TorrServerClient.streamUrl("127.0.0.1:8090", magnet, TorrentFile(0, "a.mkv", 1))).isNull()
    }

    @Test
    fun `recognises a play URL as the engine's own stream`() {
        val file = TorrentFile(7, "Sintel/sintel.mp4", 282690045)
        val url = TorrServerClient.streamUrl("http://127.0.0.1:8199", magnet, file)!!

        assertThat(TorrServerClient.isEngineStreamUrl(url)).isTrue()
        assertThat(TorrServerClient.isEngineStreamUrl("http://localhost:8090/stream/film.mkv")).isTrue()
    }

    @Test
    fun `does not mistake anything else for a torrent stream`() {
        // The engine's own API, a provider's film, and a string that is not a URL at all: the
        // player's wording must only change for the loopback stream endpoint.
        assertThat(TorrServerClient.isEngineStreamUrl("http://127.0.0.1:8199/echo")).isFalse()
        assertThat(TorrServerClient.isEngineStreamUrl("http://192.168.1.131:8080/movie/u/p/1.mp4")).isFalse()
        assertThat(TorrServerClient.isEngineStreamUrl("http://example.com/stream/film.mp4")).isFalse()
        assertThat(TorrServerClient.isEngineStreamUrl("not a url")).isFalse()
    }
}
