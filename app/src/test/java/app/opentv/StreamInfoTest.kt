/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.StreamInfo
import app.opentv.player.audioCodecLabel
import app.opentv.player.channelsLabel
import app.opentv.player.declaredQuality
import app.opentv.player.dynamicRangeLabel
import app.opentv.player.tierForHeight
import app.opentv.player.videoCodecLabel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * How the player turns raw decoder numbers into the words on screen.
 *
 * Written because this is the one place the app makes a claim *about* a stream, and the claim is
 * checked against the television: a badge that says 4K while the decoder is fed 720p is worse than
 * no badge at all, and so is a data-size estimate built from a bitrate the provider never sent. The
 * cases below pin the tier boundaries, the ways a frame rate is spoken (`23.976`, not `23`), and the
 * "say nothing" behaviour for anything the stream does not report.
 */
class StreamInfoTest {

    @Test
    fun `resolution tiers follow the decoded height`() {
        assertThat(tierForHeight(2160)).isEqualTo("4K")
        assertThat(tierForHeight(1440)).isEqualTo("QHD")
        assertThat(tierForHeight(1080)).isEqualTo("FHD")
        assertThat(tierForHeight(720)).isEqualTo("HD")
        assertThat(tierForHeight(576)).isEqualTo("SD")
        assertThat(tierForHeight(480)).isEqualTo("SD")
    }

    @Test
    fun `a height nothing has reported yet claims no tier`() {
        // Zero means "the renderer has not said", which must not render as SD — that is a claim.
        assertThat(tierForHeight(0)).isEmpty()
        assertThat(tierForHeight(-1)).isEmpty()
    }

    @Test
    fun `tier and shorthand travel together for the badge`() {
        val info = StreamInfo(width = 1920, height = 1080)
        assertThat(info.tierAndScanLabel).isEqualTo("FHD 1080p")
        assertThat(info.resolutionLabel).isEqualTo("1920 × 1080")
        assertThat(info.compactResolutionLabel).isEqualTo("1920x1080")
    }

    @Test
    fun `dimensions that have not arrived yet leave the readout blank`() {
        val unknown = StreamInfo.UNKNOWN
        assertThat(unknown.hasVideo).isFalse()
        assertThat(unknown.resolutionLabel).isEmpty()
        assertThat(unknown.tierAndScanLabel).isEmpty()
    }

    @Test
    fun `frame rates are spoken the way broadcasters quote them`() {
        // 23.976 must not be rounded to 23: the whole point of the readout is that it is exact.
        assertThat(StreamInfo(height = 1080, frameRate = 23.976f).fpsLabel).isEqualTo("23.976 fps")
        assertThat(StreamInfo(height = 1080, frameRate = 25f).fpsLabel).isEqualTo("25 fps")
        assertThat(StreamInfo(height = 720, frameRate = 29.97f).fpsLabel).isEqualTo("29.97 fps")
        assertThat(StreamInfo(height = 720, frameRate = 59.94f).fpsLabel).isEqualTo("59.94 fps")
        assertThat(StreamInfo(height = 720).fpsLabel).isEmpty()
    }

    @Test
    fun `channel layouts are named, not counted`() {
        assertThat(channelsLabel(8)).isEqualTo("7.1")
        assertThat(channelsLabel(6)).isEqualTo("5.1")
        assertThat(channelsLabel(2)).isEqualTo("Stereo")
        assertThat(channelsLabel(1)).isEqualTo("Mono")
        assertThat(channelsLabel(4)).isEqualTo("4ch")
        assertThat(channelsLabel(0)).isEmpty()
    }

    @Test
    fun `codec and bitrate arrive as one cell, in the right unit`() {
        val info = StreamInfo(
            width = 1920,
            height = 1080,
            videoCodec = "HEVC",
            videoBitrateKbps = 8200,
            audioCodec = "AAC",
            audioChannels = "5.1",
            audioBitrateKbps = 128,
        )
        assertThat(info.videoSummary).isEqualTo("HEVC 8.2 Mbps")
        assertThat(info.audioSummary).isEqualTo("AAC 5.1 128 kbps")
    }

    @Test
    fun `a stream that reports no bitrate shows no bitrate`() {
        // IPTV VOD routinely omits it. A blank cell is honest; "0 kbps" is not.
        val info = StreamInfo(width = 1920, height = 1080, videoCodec = "H.264")
        assertThat(info.videoBitrateLabel).isEmpty()
        assertThat(info.videoSummary).isEqualTo("H.264")
        assertThat(info.audioSummary).isEmpty()
    }

    @Test
    fun `estimated data size needs both a bitrate and a runtime`() {
        val info = StreamInfo(width = 3840, height = 2160, videoBitrateKbps = 8000, audioBitrateKbps = 128)
        // 8128 kbps for two hours.
        assertThat(info.estimatedSize(7_200_000L)).isEqualTo("6.8 GB")
        assertThat(info.estimatedSize(0L)).isNull()
        assertThat(StreamInfo(height = 1080).estimatedSize(7_200_000L)).isNull()
    }

    @Test
    fun `the quality a title claims is reported separately, and never guessed`() {
        assertThat(declaredQuality("The Godfather 1972 4K")).isEqualTo("4K")
        assertThat(declaredQuality("MOVIE HD")).isEqualTo("HD")
        assertThat(declaredQuality("Something 1080p")).isEqualTo("1080P")
        // Both present: the highest rank wins, because that is the one the provider is selling.
        assertThat(declaredQuality("Film 720p UHD")).isEqualTo("UHD")
        assertThat(declaredQuality("Plain Title (2026)")).isNull()
    }

    @Test
    fun `no track at all reads as nothing known`() {
        assertThat(videoCodecLabel(null)).isEmpty()
        assertThat(audioCodecLabel(null)).isEmpty()
        assertThat(dynamicRangeLabel(null)).isEmpty()
    }
}
