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
import java.util.TimeZone
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CatchupResolver {

    // Matches standard Xtream Live URLs:
    // http(s)://domain[:port]/[live/]user/pass/streamId[.ts|.m3u8]
    // with optional prefix path segments or query parameters
    private val XTREAM_URL_REGEX = Regex("""^(https?://[^/]+)(?:/.*?)*?/(?:live/)?([^/?#]+)/([^/?#]+)/([a-zA-Z0-9_.-]+?)(?:\.[a-zA-Z0-9]+)?(?:\?.*)?$""")

    /**
     * A Stalker `create_link` command is not a catch-up template. Templates are absolute/relative
     * URLs or query fragments carrying substitution markers; bare commands (`ffmpeg …`, `auto …`)
     * have none of those and must never badge or resolve as catch-up.
     */
    fun isCatchupTemplate(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("?") ||
            value.contains("{") ||
            value.contains("\${")
    }

    /**
     * Checks whether catch-up / archive playback is supported for this channel.
     * Checks database flag, catch-up template, Xtream source kind, or Xtream URL pattern.
     */
    fun isSupported(source: Source?, channel: Channel): Boolean {
        if (channel.tvArchive) return true
        if (isCatchupTemplate(channel.cmd)) return true
        if (source?.kind == SourceKind.XTREAM) return true
        if (source != null && extractCredentials(source) != null) return true
        return XTREAM_URL_REGEX.containsMatchIn(channel.streamUrl)
    }

    /**
     * Whether this source's provider does catch-up for its channels at the portal level — an
     * Xtream portal, or one reached through the portal's playlist endpoint. Many such providers
     * never set the per-channel archive flag in their M3U, yet every channel supports catch-up,
     * so the guide badge uses this instead of trusting the flag alone.
     */
    fun isSourceCapable(source: Source): Boolean =
        source.kind == SourceKind.XTREAM ||
            source.url.contains("get.php", ignoreCase = true) ||
            source.url.contains("player_api.php", ignoreCase = true)

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
     *
     * Supports Xtream Codes timeshift, M3U templates with placeholder substitution,
     * Xtream URL auto-detection from M3U stream URLs, Flussonic derivations, and
     * shift/append modes. [globalCorrectionMin] (from Settings > Catch-up) is added to the
     * channel's own `catchup-correction` before building stamps/utc values.
     */
    fun resolve(
        source: Source,
        channel: Channel,
        programme: Programme,
        globalCorrectionMin: Int = 0,
    ): String? {
        val url = resolveInternal(source, channel, programme, globalCorrectionMin)
        android.util.Log.i("OpenTV-Catchup", "resolved ${url ?: "null"} (base=${channel.streamUrl})")
        return url
    }

    private fun resolveInternal(
        source: Source,
        channel: Channel,
        programme: Programme,
        globalCorrectionMin: Int = 0,
    ): String? {
        val correctionSec = ((channel.catchupCorrectionMin + globalCorrectionMin) * 60L)
        val adjStartMillis = programme.startUtcMillis + correctionSec * 1000L
        val startUtcMillis = adjStartMillis
        val endUtcMillis = programme.endUtcMillis + correctionSec * 1000L
        val nowMillis = System.currentTimeMillis()
        val progDurationMillis = if (endUtcMillis > startUtcMillis) {
            endUtcMillis - startUtcMillis
        } else {
            60 * 60_000L
        }
        val durationMinutes = ((progDurationMillis + 30_000L) / 60_000L).toInt().coerceIn(15, 1440)
        val durationSeconds = (progDurationMillis / 1000L).coerceAtLeast(60L)
        val startUtcSec = startUtcMillis / 1000L
        val endUtcSec = (startUtcMillis + progDurationMillis) / 1000L
        val nowSec = nowMillis / 1000L
        val offsetSec = (nowSec - startUtcSec).coerceAtLeast(0L)
        val utcTz = TimeZone.getTimeZone("UTC")
        val stamp = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = utcTz }.format(Date(startUtcMillis))

        // 1. Native Xtream Codes source
        if (source.kind == SourceKind.XTREAM) {
            val cleanStreamId = channel.streamId.removePrefix("tvg:").removePrefix("url:").substringBeforeLast('.')
            val baseUrl = source.url.trimEnd('/')
            val u = source.username.orEmpty()
            val p = source.password.orEmpty()
            return "$baseUrl/timeshift/$u/$p/$durationMinutes/$stamp/$cleanStreamId.ts"
        }

        val mode = channel.catchupMode.lowercase()
        // 2. M3U with explicit catchup-source template (stored in channel.cmd)
        val template = channel.cmd?.takeIf { isCatchupTemplate(it) }
        // `shift` ignores any template (SIPTV auto `?utc=&lutc=`); every other mode prefers
        // its explicit `catchup-source` template when one was supplied.
        if (template != null && mode != "shift") {
            val stampXtream = stamp
            val stampFlussonic = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = utcTz }.format(Date(startUtcMillis))

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

        // 3. Explicit shift/append modes: SIPTV-style `?utc={start}&lutc={now}`. These need no
        // template and win over URL-shape guessing below — the provider said how to ask.
        if (mode == "shift" || mode == "append") {
            val sep = if (channel.streamUrl.contains("?")) "&" else "?"
            return "${channel.streamUrl}${sep}utc=$startUtcSec&lutc=$nowSec"
        }

        // 4. Flussonic mode: archive-{utc}-{durationSecs}.m3u8 derived from the stream URL.
        // Flussonic counts duration in seconds (Xtream counts minutes) — do not reuse stamp above.
        if (mode == "flussonic") {
            val base = channel.streamUrl.substringBeforeLast('/') + "/"
            return "$base" + "archive-$startUtcSec-$durationSeconds.m3u8"
        }

        // 5. M3U stream URL matching Xtream live format (`xc` mode or auto-detect):
        // http(s)://host:port/(live/)user/pass/streamId(.ts/.m3u8)
        val match = XTREAM_URL_REGEX.find(channel.streamUrl)
        if (match != null) {
            val (baseHost, user, pass, streamId) = match.destructured
            val base = baseHost.trimEnd('/')
            return "$base/timeshift/$user/$pass/$durationMinutes/$stamp/$streamId.ts"
        }

        // 5. Source URL has embedded Xtream credentials (e.g. get.php?username=...&password=...)
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

        // 6. Default fallback: `default` without a template degrades to append-style.
        if (channel.tvArchive || mode == "default") {
            val sep = if (channel.streamUrl.contains("?")) "&" else "?"
            return "${channel.streamUrl}${sep}utc=$startUtcSec&lutc=$nowSec"
        }

        return null
    }
}
