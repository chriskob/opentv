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

    // Matches standard Xtream Live URLs:
    // http(s)://domain[:port]/[live/]user/pass/streamId[.ts|.m3u8]
    // with optional prefix path segments or query parameters
    private val XTREAM_URL_REGEX = Regex("""^(https?://[^/]+)(?:/.*?)*?/(?:live/)?([^/?#]+)/([^/?#]+)/([a-zA-Z0-9_.-]+?)(?:\.[a-zA-Z0-9]+)?(?:\?.*)?$""")

    /**
     * Checks whether catch-up / archive playback is supported for this channel.
     * Checks database flag, custom template, Xtream source kind, or Xtream URL pattern.
     */
    fun isSupported(source: Source?, channel: Channel): Boolean {
        if (channel.tvArchive) return true
        if (!channel.cmd.isNullOrBlank()) return true
        if (source?.kind == SourceKind.XTREAM) return true
        if (source != null && extractCredentials(source) != null) return true
        return XTREAM_URL_REGEX.containsMatchIn(channel.streamUrl)
    }

    private fun extractCredentials(source: Source): Pair<String, String>? {
        val user = source.username
        val pass = source.password
        if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
            return user to pass
        }
        val httpUrl = source.url.toHttpUrlOrNull() ?: return null
        val u = httpUrl.queryParameter("username") ?: httpUrl.queryParameter("user")
        val p = httpUrl.queryParameter("password") ?: httpUrl.queryParameter("pass")
        if (!u.isNullOrBlank() && !p.isNullOrBlank()) {
            return u to p
        }
        return null
    }

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
        val stamp = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).format(Date(startUtcMillis))

        // 1. Native Xtream Codes source
        if (source.kind == SourceKind.XTREAM) {
            val cleanStreamId = channel.streamId.removePrefix("tvg:").removePrefix("url:")
            val baseUrl = source.url.trimEnd('/')
            val u = source.username.orEmpty()
            val p = source.password.orEmpty()
            return "$baseUrl/timeshift/$u/$p/$durationMinutes/$stamp/$cleanStreamId.ts"
        }

        // 2. M3U with explicit catchup-source template (stored in channel.cmd)
        val template = channel.cmd?.takeIf { it.isNotBlank() }
        if (template != null) {
            val stampXtream = stamp
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
        val match = XTREAM_URL_REGEX.find(channel.streamUrl)
        if (match != null) {
            val (baseHost, user, pass, streamId) = match.destructured
            val base = baseHost.trimEnd('/')
            return "$base/timeshift/$user/$pass/$durationMinutes/$stamp/$streamId.ts"
        }

        // 4. Source URL has embedded Xtream credentials (e.g. get.php?username=...&password=...)
        val creds = extractCredentials(source)
        if (creds != null) {
            val sourceUri = source.url.toHttpUrlOrNull()
            if (sourceUri != null) {
                val base = "${sourceUri.scheme}://${sourceUri.host}${if (sourceUri.port != 80 && sourceUri.port != 443) ":${sourceUri.port}" else ""}"
                val cleanStreamId = channel.streamId.removePrefix("tvg:").removePrefix("url:")
                if (cleanStreamId.isNotBlank() && cleanStreamId.matches(Regex("""^[a-zA-Z0-9_.-]+$"""))) {
                    return "$base/timeshift/${creds.first}/${creds.second}/$durationMinutes/$stamp/$cleanStreamId.ts"
                }
            }
        }

        // 5. Default append mode if channel declared catch-up
        if (channel.tvArchive) {
            val sep = if (channel.streamUrl.contains("?")) "&" else "?"
            return "${channel.streamUrl}${sep}utc=$startUtcSec&lutc=$nowSec"
        }

        return null
    }
}
