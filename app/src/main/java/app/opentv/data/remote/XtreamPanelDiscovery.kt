/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import app.opentv.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeToSequence
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Finds the Xtream panel behind a plain M3U playlist, and asks it which channels have archive.
 *
 * ## Why this exists
 *
 * Most playlists are an Xtream panel's *export*, and the export throws away the per-channel answer
 * about catch-up: the `catchup` / `timeshift` / `catchup-source` attributes are simply not written.
 * Verified against one real provider — 30,828 `#EXTINF` lines, zero catch-up attributes — while the
 * panel itself reported `tv_archive: 1` for 2,195 of its 55,690 channels. A playlist-only client
 * therefore cannot badge honestly: guessing badged every channel (the bug this replaces), and
 * refusing to guess badges none, which hides the channels that really do have archive.
 *
 * The playlist does leak enough to ask properly. Its stream URLs are the panel's own
 * `…/[live/]user/pass/streamId.ext` shape, so the panel's address and credentials are right there in
 * the file. We lift them, call `player_api.php?action=get_live_streams` once, and keep the panel's
 * per-channel answer.
 *
 * ## Why it is safe to be wrong
 *
 * Everything here is best-effort and never throws to the caller: no panel recognised, credentials
 * stale, endpoint behind a captive portal — the sync continues with exactly the flags the playlist
 * gave us. The panel address is only *guessed* from URL shape, so it is confirmed by making it
 * answer before any channel is touched. Note this deliberately does not change how playback is
 * resolved: [app.opentv.core.CatchupResolver] already builds a timeshift URL from the channel's own
 * URL shape, so this only fixes what the guide *claims*, never what the player can attempt.
 */
object XtreamPanelDiscovery {

    /** Where a playlist's streams come from, and with whose credentials. */
    data class Panel(val baseUrl: String, val username: String, val password: String)

    /** A panel plus the stream id parsed out of one of its URLs. */
    internal data class Hit(val panel: Panel, val streamId: String)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Splits an `…/[live/]user/pass/streamId.ext` URL into its panel and stream id, or null when
     * the URL is not that shape.
     *
     * The last three path segments must be user / password / a numeric id, and the segment before
     * them must be absent or the literal `live` marker. Anything else is refused rather than
     * guessed at, because a wrong guess means asking the wrong server with credentials lifted from
     * a URL that happened to look similar.
     */
    internal fun parse(streamUrl: String): Hit? {
        val http = streamUrl.toHttpUrlOrNull() ?: return null
        val segments = http.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 3) return null

        val id = segments.last().substringBefore('.')
        if (id.isEmpty() || !id.all { it.isDigit() }) return null

        val password = segments[segments.size - 2]
        val username = segments[segments.size - 3]
        if (username.isBlank() || password.isBlank()) return null

        val marker = segments.getOrNull(segments.size - 4)
        if (marker != null && !marker.equals("live", ignoreCase = true)) return null

        val port = if (http.port == 80 || http.port == 443) "" else ":${http.port}"
        return Hit(
            panel = Panel("${http.scheme}://${http.host}$port", username, password),
            streamId = id,
        )
    }

    /** The stream id inside a playlist URL, or null when it is not that shape. */
    fun streamIdOf(streamUrl: String): String? = parse(streamUrl)?.streamId

    /**
     * Panels worth asking, most-used first.
     *
     * A playlist can mix hosts (a provider often serves some channels from a CDN that also happens
     * to use the same URL shape), so this counts rather than taking the first hit, and the caller
     * tries the candidates in order until one actually answers.
     *
     * @param minimum how many URLs must agree before a host is considered at all. Eight is enough
     *   to rule out the odd CDN URL on a playlist of any size, and small enough to still fire on a
     *   short playlist.
     */
    fun candidates(
        sampleUrls: List<String>,
        minimum: Int = 8,
        share: Double = 0.25,
    ): List<Panel> {
        val hits = sampleUrls.mapNotNull { parse(it) }
        if (hits.isEmpty()) return emptyList()
        val counts = LinkedHashMap<Panel, Int>()
        hits.forEach { counts[it.panel] = (counts[it.panel] ?: 0) + 1 }
        val floor = maxOf(minimum, (hits.size * share).toInt())
        return counts.entries
            .filter { it.value >= floor }
            .sortedByDescending { it.value }
            .map { it.key }
    }

    /**
     * Asks [panel] which of its streams have archive, as `streamId -> archive days`.
     *
     * Only archived channels are kept, so the map holds a couple of thousand entries on a large
     * provider rather than one per stream. The response is read as a stream: the live list is
     * ~19 MB on the provider this was written against, and a TV box has no headroom to hold that
     * as text and then again as a JSON tree.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun archiveDays(
        panel: Panel,
        http: OkHttpClient,
        userAgent: String,
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val url = playerApiUrl(panel, "get_live_streams") ?: return@withContext emptyMap()
        val out = HashMap<String, Int>()
        http.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyMap()
            val body = response.body ?: return@withContext emptyMap()
            body.byteStream().use { bytes ->
                json.decodeToSequence<JsonElement>(bytes).forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    if (obj["tv_archive"].intOrNull() != 1) return@forEach
                    val id = obj["stream_id"].stringOrNull() ?: return@forEach
                    out[id] = obj["tv_archive_duration"].intOrNull()?.coerceAtLeast(1) ?: 1
                }
            }
        }
        out
    }

    /**
     * Flags the channels the panel says have archive.
     *
     * Channels whose playlist already declared catch-up are left exactly as they are: an explicit
     * `catchup-source` template or `catchup` attribute is the provider's own answer, and a panel
     * that disagrees must not be allowed to erase it.
     */
    fun applyArchive(channels: List<Channel>, archiveDays: Map<String, Int>): Pair<List<Channel>, Int> {
        if (archiveDays.isEmpty()) return channels to 0
        var added = 0
        val updated = channels.map { channel ->
            if (channel.tvArchive) return@map channel
            val days = streamIdOf(channel.streamUrl)?.let { archiveDays[it] } ?: return@map channel
            added++
            channel.copy(tvArchive = true, tvArchiveDays = days)
        }
        return updated to added
    }

    /**
     * Detects the panel behind a playlist and stamps its archive answer onto [channels].
     *
     * Returns the channels to store (unchanged when nothing could be learned) and how many were
     * newly flagged, so the caller can log a line that explains an empty guide badge.
     */
    suspend fun enrich(
        channels: List<Channel>,
        http: OkHttpClient,
        userAgent: String,
    ): Pair<List<Channel>, Int> {
        if (channels.isEmpty()) return channels to 0
        val sample = channels.take(SAMPLE_SIZE).map { it.streamUrl }
        for (panel in candidates(sample)) {
            // A candidate that does not answer with a live list is not the provider's panel
            // (or wants different credentials) — try the next, and give up quietly if none do.
            val days = archiveDays(panel, http, userAgent)
            if (days.isEmpty()) continue
            return applyArchive(channels, days)
        }
        return channels to 0
    }

    /** How many channel URLs are inspected when looking for a panel. */
    private const val SAMPLE_SIZE = 400

    private fun playerApiUrl(panel: Panel, action: String): HttpUrl? =
        panel.baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.encodedPath("/player_api.php")
            ?.addQueryParameter("username", panel.username)
            ?.addQueryParameter("password", panel.password)
            ?.addQueryParameter("action", action)
            ?.build()

    /**
     * Reads an integer from any of the shapes panels use: `1`, `"1"`, `1.0`, `" 1 "`.
     *
     * Named differently from the private accessors in [XtreamApi] on purpose — same package, and
     * two same-named extensions would make every call in this file ambiguous.
     */
    private fun JsonElement?.intOrNull(): Int? {
        val text = (this as? JsonPrimitive)?.content?.trim() ?: return null
        return text.toIntOrNull() ?: text.toDoubleOrNull()?.let { Math.round(it).toInt() }
    }

    private fun JsonElement?.stringOrNull(): String? {
        val text = (this as? JsonPrimitive)?.content?.trim() ?: return null
        return text.ifEmpty { null }
    }
}
