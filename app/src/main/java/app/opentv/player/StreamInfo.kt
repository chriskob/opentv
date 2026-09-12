/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/**
 * What the decoder is **actually** receiving, read from the tracks the player selected.
 *
 * The point of this type is the difference between what a provider *claims* a stream is and what it
 * *sends*. A panel advertising "4K" routinely serves 1080p, and an M3U exported from an Xtream
 * panel carries no resolution at all. Every field here is therefore read off the live `Format` /
 * `VideoSize` after the renderers have reported in — never guessed from the title — so a film's
 * info panel can be trusted. The claim, when there is one, is shown beside it by [declaredQuality]
 * rather than substituted for it.
 *
 * Everything is best-effort: a field the stream does not report stays empty or zero, and the UI is
 * expected to omit it rather than print a placeholder.
 */
@OptIn(UnstableApi::class)
data class StreamInfo(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val videoCodec: String = "",
    val videoBitrateKbps: Int = 0,
    val dynamicRange: String = "",
    val audioCodec: String = "",
    val audioChannels: String = "",
    val audioBitrateKbps: Int = 0,
) {
    /**
     * True once a video track has reported real dimensions. Before that a tier badge would be a
     * lie, so the player hides the format readout instead of flashing "SD" for a second.
     */
    val hasVideo: Boolean get() = width > 0 && height > 0

    /** `1920 × 1080` — what people call the size of the picture. */
    val resolutionLabel: String get() = if (hasVideo) "$width × $height" else ""

    /** `1920x1080` — the same thing in the compact form used by in-line stat badges. */
    val compactResolutionLabel: String get() = if (hasVideo) "${width}x$height" else ""

    /** `1080p` — the shorthand that goes with the tier. */
    val scanLabel: String get() = when {
        height >= 2000 -> "2160p"
        height >= 1400 -> "1440p"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        height >= 500 -> "576p"
        height > 0 -> "480p"
        else -> ""
    }

    /** `4K` / `QHD` / `FHD` / `HD` / `SD` — the coarse tier a viewer actually shops by. */
    val tierLabel: String get() = tierForHeight(height)

    /** `FHD 1080p` — tier and shorthand together, for a one-line badge. */
    val tierAndScanLabel: String
        get() = listOf(tierLabel, scanLabel).filter { it.isNotEmpty() }.joinToString(" ")

    val fpsLabel: String get() = if (frameRate > 0f) "${formatFps(frameRate)} fps" else ""

    val videoBitrateLabel: String get() = if (videoBitrateKbps > 0) formatBitrate(videoBitrateKbps) else ""

    val audioBitrateLabel: String get() = if (audioBitrateKbps > 0) formatBitrate(audioBitrateKbps) else ""

    /** `HEVC 8.2 Mbps` — codec and bitrate as one cell. */
    val videoSummary: String
        get() = listOf(videoCodec, videoBitrateLabel).filter { it.isNotEmpty() }.joinToString(" ")

    /** `AAC Stereo 128 kbps` — codec, layout and bitrate as one cell. */
    val audioSummary: String
        get() = listOf(audioCodec, audioChannels, audioBitrateLabel).filter { it.isNotEmpty() }.joinToString(" ")

    /**
     * How much data [durationMs] of this stream costs, or null when the provider reports no
     * bitrate — common on IPTV VOD. Null rather than a fabricated number: an estimate the viewer
     * might act on is worse than no estimate.
     */
    fun estimatedSize(durationMs: Long): String? {
        val kbps = videoBitrateKbps + audioBitrateKbps
        if (kbps <= 0 || durationMs <= 0L) return null
        return formatBytes(kbps.toLong() * 1000L / 8L * (durationMs / 1000L))
    }

    companion object {
        /** The state before anything has been reported — renders as "no readout yet". */
        val UNKNOWN = StreamInfo()
    }
}

/** Coarse resolution tier from a decoded frame height. */
fun tierForHeight(height: Int): String = when {
    height >= 2000 -> "4K"
    height >= 1400 -> "QHD"
    height >= 1000 -> "FHD"
    height >= 700 -> "HD"
    height > 0 -> "SD"
    else -> ""
}

/**
 * Reads the selected video and audio tracks into a [StreamInfo].
 *
 * [videoSize] is the fallback for dimensions: a live transport stream often exposes no `Format`
 * width/height until the decoder has parsed a frame, but does report a `VideoSize`.
 */
@OptIn(UnstableApi::class)
fun streamInfoOf(tracks: Tracks, videoSize: VideoSize): StreamInfo {
    val video = selectedFormat(tracks, C.TRACK_TYPE_VIDEO)
    val audio = selectedFormat(tracks, C.TRACK_TYPE_AUDIO)

    val width = video?.width?.takeIf { it > 0 } ?: videoSize.width
    val height = video?.height?.takeIf { it > 0 } ?: videoSize.height

    return StreamInfo(
        width = width.coerceAtLeast(0),
        height = height.coerceAtLeast(0),
        frameRate = video?.frameRate?.takeIf { it > 0f } ?: 0f,
        videoCodec = videoCodecLabel(video),
        videoBitrateKbps = (video?.bitrate ?: 0).coerceAtLeast(0) / 1000,
        dynamicRange = dynamicRangeLabel(video),
        audioCodec = audioCodecLabel(audio),
        audioChannels = channelsLabel(audio?.channelCount ?: 0),
        audioBitrateKbps = (audio?.bitrate ?: 0).coerceAtLeast(0) / 1000,
    )
}

/** The `Format` behind a selected track of [type], or null when nothing is selected. */
@OptIn(UnstableApi::class)
private fun selectedFormat(tracks: Tracks, type: Int): Format? {
    val group = tracks.groups.firstOrNull { it.type == type && it.isSelected } ?: return null
    for (i in 0 until group.length) {
        if (group.isTrackSelected(i)) return group.getTrackFormat(i)
    }
    return null
}

/** `H.264`, `HEVC`, `AV1`… from the sample MIME type. */
fun videoCodecLabel(format: Format?): String {
    val mime = format?.sampleMimeType ?: return ""
    return when {
        mime.contains("avc", ignoreCase = true) || mime.contains("h264", ignoreCase = true) -> "H.264"
        mime.contains("hevc", ignoreCase = true) || mime.contains("h265", ignoreCase = true) -> "HEVC"
        mime.contains("av01", ignoreCase = true) || mime.contains("av1", ignoreCase = true) -> "AV1"
        mime.contains("vp9", ignoreCase = true) -> "VP9"
        mime.contains("vp8", ignoreCase = true) -> "VP8"
        mime.contains("mpeg2", ignoreCase = true) -> "MPEG-2"
        mime.isNotEmpty() -> mime.substringAfterLast("/").uppercase(Locale.US)
        else -> ""
    }
}

/** `AAC`, `AC3`, `E-AC3`, `Opus`… from the sample MIME type. */
fun audioCodecLabel(format: Format?): String {
    val mime = format?.sampleMimeType ?: return ""
    return when {
        mime.contains("mp4a-latm", ignoreCase = true) -> "AAC-LATM"
        mime.contains("aac", ignoreCase = true) -> "AAC"
        mime.contains("eac3", ignoreCase = true) || mime.contains("ec-3", ignoreCase = true) -> "E-AC3"
        mime.contains("ac3", ignoreCase = true) || mime.contains("ac-3", ignoreCase = true) -> "AC3"
        mime.contains("opus", ignoreCase = true) -> "Opus"
        mime.contains("vorbis", ignoreCase = true) -> "Vorbis"
        mime.contains("flac", ignoreCase = true) -> "FLAC"
        mime.contains("dts", ignoreCase = true) -> "DTS"
        mime.contains("mpeg-l1", ignoreCase = true) || mime.contains("mpeg-l2", ignoreCase = true) -> "MP2"
        mime.contains("mpeg", ignoreCase = true) -> "MP3"
        mime.isNotEmpty() -> mime.substringAfterLast("/").uppercase(Locale.US)
        else -> ""
    }
}

/** `Stereo`, `5.1`, `7.1`… from a channel count. */
fun channelsLabel(count: Int): String = when {
    count >= 8 -> "7.1"
    count == 6 -> "5.1"
    count == 2 -> "Stereo"
    count == 1 -> "Mono"
    count > 2 -> "${count}ch"
    else -> ""
}

/**
 * `HDR10`, `HLG` or `Dolby Vision` when the stream says so, else empty.
 *
 * Dolby Vision is signalled in the codec string (`dvhe`/`dvh1`/`dav1`) rather than in the colour
 * info, so it is checked first; a DV stream also carries PQ transfer and would otherwise be
 * reported as plain HDR10.
 */
@OptIn(UnstableApi::class)
fun dynamicRangeLabel(format: Format?): String {
    if (format == null) return ""
    val codecs = format.codecs.orEmpty().lowercase(Locale.US)
    if (codecs.startsWith("dvhe") || codecs.startsWith("dvh1") || codecs.startsWith("dav1")) {
        return "Dolby Vision"
    }
    val color = format.colorInfo ?: return ""
    return when (color.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> "HDR10"
        C.COLOR_TRANSFER_HLG -> "HLG"
        else -> ""
    }
}

/**
 * The quality a title *claims*, as a cross-check on the decoded resolution.
 *
 * Quality tokens in provider titles are the ones channel names carry (`4K`, `FHD`, `HD`…), so this
 * reuses [app.opentv.data.parser.ChannelNameNormalizer.qualityRankOfToken] and returns the
 * highest-ranked token found. Null when the title makes no claim — the normal case for an episode.
 */
fun declaredQuality(title: String): String? {
    val tokens = title.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    return tokens
        .mapNotNull { token ->
            app.opentv.data.parser.ChannelNameNormalizer.qualityRankOfToken(token)?.let { it to token }
        }
        .maxByOrNull { it.first }
        ?.second
        ?.uppercase(Locale.US)
}

/** `23.976`, `25`, `59.94` — whole when the rate is whole, three decimals otherwise. */
private fun formatFps(fps: Float): String {
    val rounded = Math.round(fps * 1000f) / 1000f
    val whole = Math.round(rounded)
    if (Math.abs(rounded - whole) < 0.001f) return whole.toString()
    return String.format(Locale.US, "%.3f", rounded).trimEnd('0').trimEnd('.')
}

/** `820 kbps` below a megabit, `8.2 Mbps` above it. */
private fun formatBitrate(kbps: Int): String =
    if (kbps < 1000) "$kbps kbps" else String.format(Locale.US, "%.1f Mbps", kbps / 1000f)

/** `820 MB`, `1.4 GB` — for the estimated data size of a film. */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> String.format(Locale.US, "%.0f MB", bytes / 1_048_576.0)
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}
