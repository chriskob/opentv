/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.torrent.TorrentEngine
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * The engine configuration OpenTV ships, checked against the engine's own schema.
 *
 * This is a privacy test as much as a parsing one. The engine is a separate program with its own
 * defaults, and its defaults upload, write a disk cache when asked to, advertise itself on the local
 * network, and ask the router for a port. Every one of those is off here, and a test is the only
 * thing that stops a future edit from quietly turning one back on.
 *
 * [EXPECTED_KEYS] is the exact key set of the settings file TorrServer MatriX.147 writes on its
 * first run, captured from a live build. The engine's loader is not a merge: a key missing from our
 * file is a zero value, so a key dropped from this file has to fail the build rather than the
 * feature.
 */
class TorrentEngineSettingsTest {

    private val settings = Json.parseToJsonElement(TorrentEngine.defaultSettingsJson())
        .jsonObject["BitTorr"]!!.jsonObject

    private fun bool(key: String) = settings[key]!!.jsonPrimitive.boolean

    @Test
    fun `never uploads, and never asks the network to be reachable`() {
        // `NoUpload` in the engine's BitTorrent library: the peer protocol still handshakes, but no
        // piece of any file is sent to anyone. This is the whole of "never upload".
        assertThat(bool("DisableUpload")).isTrue()

        // Without this the engine opens an inbound port on the user's router by UPnP.
        assertThat(bool("DisableUPNP")).isTrue()

        // Local peer discovery and mDNS advertisement: both are right for a server you run on
        // purpose and announce, and wrong for an engine bundled inside a player.
        assertThat(bool("EnableLPD")).isFalse()
        assertThat(bool("EnableBonjour")).isFalse()
        assertThat(bool("EnableDLNA")).isFalse()
    }

    @Test
    fun `keeps the cache in memory, bounded, and cleans up on drop`() {
        // 64 MiB, the engine's own default, shared by every torrent it holds: bounded RAM instead of
        // a growing disk cache, which is the honest answer to "won't the box fill up".
        assertThat(settings["CacheSize"]!!.jsonPrimitive.int).isEqualTo(64 * 1024 * 1024)
        assertThat(bool("UseDisk")).isFalse()
        assertThat(settings["TorrentsSavePath"]!!.jsonPrimitive.content).isEmpty()

        // The engine deletes a torrent's directory on `rem`; if a future change ever enables the disk
        // cache, that is what stops the files accumulating.
        assertThat(bool("RemoveCacheOnDrop")).isTrue()

        // A torrent with no reader is dropped by the engine itself, which is what ends the swarm when
        // the user simply backs out of playback.
        assertThat(settings["TorrentDisconnectTimeout"]!!.jsonPrimitive.int).isEqualTo(30)
    }

    @Test
    fun `keeps the peer discovery a magnet needs`() {
        // DHT is how a magnet with no working tracker finds anyone at all, and PEX is how it widens a
        // thin swarm. Turning either off would be a broken feature, not a stricter one.
        assertThat(bool("DisableDHT")).isFalse()
        assertThat(bool("DisablePEX")).isFalse()
        assertThat(settings["DefaultTrackers"]!!.jsonPrimitive.content)
            .contains("udp://tracker.opentrackr.org:1337/announce")
    }

    @Test
    fun `keeps the settings file the live configuration`() {
        // With this false the engine reads its settings from its database instead, and the file
        // OpenTV ships quietly stops being what is in force.
        assertThat(bool("StoreSettingsInJson")).isTrue()
    }

    @Test
    fun `carries every key the engine writes, so nothing falls back to a zero`() {
        assertThat(settings.keys).containsExactlyElementsIn(EXPECTED_KEYS)
    }

    @Test
    fun `is valid JSON that the engine's own reader would accept`() {
        // A raw string in Kotlin is the one place an escaping mistake survives review; parsing it here
        // means it fails on the build machine rather than as a dead engine on a TV.
        val text = TorrentEngine.defaultSettingsJson()
        assertThat(text).startsWith("{")
        assertThat(text).endsWith("}\n")
        assertThat(Json.parseToJsonElement(text)).isNotNull()
    }

    private companion object {
        /**
         * Every key of `BitTorr` in the settings file TorrServer MatriX.147 writes on first run.
         * Verified against a live build's own file, not from documentation.
         */
        val EXPECTED_KEYS = setOf(
            "CacheSize",
            "ConnectionsLimit",
            "DefaultTrackers",
            "DisableDHT",
            "DisablePEX",
            "DisableTCP",
            "DisableUPNP",
            "DisableUTP",
            "DisableUpload",
            "DownloadRateLimit",
            "EnableBonjour",
            "EnableDebug",
            "EnableDLNA",
            "EnableIPv6",
            "EnableLPD",
            "EnableRutorSearch",
            "EnableTorznabSearch",
            "ForceEncrypt",
            "FriendlyName",
            "LPDIPv6",
            "MergeAllM3U",
            "PeersListenPort",
            "PreloadCache",
            "ReaderReadAHead",
            "RemoveCacheOnDrop",
            "ResponsiveMode",
            "RetrackersMode",
            "ShowFSActiveTorr",
            "SslCert",
            "SslKey",
            "SslPort",
            "StoreSettingsInJson",
            "StoreViewedInJson",
            "TMDBSettings",
            "TorrentDisconnectTimeout",
            "TorrentsSavePath",
            "TorznabUrls",
            "TrackTimecode",
            "TrackersListURL",
            "UploadRateLimit",
            "UseDisk",
        )
    }
}
