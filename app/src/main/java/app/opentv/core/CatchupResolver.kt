/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CatchupResolver {

    /**
     * Resolves a seekable catch-up / timeshift stream URL for a finished programme.
     * Supports Xtream Codes timeshift, M3U templates with placeholder substitution,
     * Xtream URL auto-detection from M3U stream URLs, and append mode.
     */
    fun resolve(source: Source, channel: Channel, programme: Programme): String? {
        val startUtcMillis = programme.startUtcMillis
        val endUtcMillis = programme.endUtcMillis
        val durationMillis = (endUtcMillis - startUtcMillis).coerceAtLeast(60_000L)
        val durationMinutes = ((durationMillis + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
        val durationSeconds = (durationMillis / 1000L).coerceAtLeast(60L)
        val startUtcSec = startUtcMillis / 1000L
        val endUtcSec = endUtcMillis / 1000L
        val nowSec = System.currentTimeMillis() / 1000L
        val offsetSec = (nowSec - startUtcSec).coerceAtLeast(0L)

        // 1. Native Xtream Codes source
        if (source.kind == SourceKind.XTREAM) {
            val stamp = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(startUtcMillis))
            return "${source.url}/timeshift/${source.username}/${source.password}/$durationMinutes/$stamp/${channel.streamId}.ts"
        }

        // 2. M3U with explicit catchup-source template (stored in channel.cmd)
        val template = channel.cmd?.takeIf { it.isNotBlank() }
        if (template != null) {
            val stampXtream = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(startUtcMillis))
            val stampFlussonic = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(startUtcMillis))

            var url = template
                .replace("{utc}", startUtcSec.toString())
                .replace("\${utc}", startUtcSec.toString())
                .replace("{start}", startUtcSec.toString())
                .replace("\${start}", startUtcSec.toString())
                .replace("{timestamp}", startUtcSec.toString())
                .replace("\${timestamp}", startUtcSec.toString())
                .replace("{lutc}", startUtcSec.toString())
                .replace("\${lutc}", startUtcSec.toString())
                .replace("{end}", endUtcSec.toString())
                .replace("\${end}", endUtcSec.toString())
                .replace("{duration}", durationSeconds.toString())
                .replace("\${duration}", durationSeconds.toString())
                .replace("{offset}", offsetSec.toString())
                .replace("\${offset}", offsetSec.toString())
                .replace("\${(b)yyyyMMddHHmmss}", stampFlussonic)
                .replace("{(b)yyyyMMddHHmmss}", stampFlussonic)
                .replace("\${(b)yyyy-MM-dd:HH-mm}", stampXtream)
                .replace("{(b)yyyy-MM-dd:HH-mm}", stampXtream)
                .replace("{catchup-id}", channel.epgChannelId ?: channel.streamId)
                .replace("\${catchup-id}", channel.epgChannelId ?: channel.streamId)

            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                val base = channel.streamUrl
                url = if (url.startsWith("?")) {
                    val sep = if (base.contains("?")) "&" else "?"
                    base + sep + url.substring(1)
                } else {
                    val baseUri = base.toHttpUrlOrNull()
                    baseUri?.resolve(url)?.toString() ?: (base + url)
                }
            }
            return url
        }

        // 3. M3U stream URL matching Xtream live format: http(s)://host:port/(live/)user/pass/streamId(.ts/.m3u8)
        val xtreamPattern = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.[a-zA-Z0-9]+)?$""")
        val match = xtreamPattern.find(channel.streamUrl)
        if (match != null) {
            val (baseHost, user, pass, streamId) = match.destructured
            val stamp = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(startUtcMillis))
            return "$baseHost/timeshift/$user/$pass/$durationMinutes/$stamp/$streamId.ts"
        }

        // 4. Default append mode if channel declared catch-up
        if (channel.tvArchive) {
            val sep = if (channel.streamUrl.contains("?")) "&" else "?"
            return "${channel.streamUrl}${sep}utc=$startUtcSec&lutc=$nowSec"
        }

        return null
    }
}
