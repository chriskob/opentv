/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import android.util.Log
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Source
import app.opentv.data.model.StreamKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * A client for the Stalker / Ministra portal protocol (the "MAG box" middleware many panels speak).
 *
 * ## How it differs from Xtream
 * There is no username/password. A box identifies itself by a **MAC address** and does a **handshake**
 * that returns a short-lived **token**; every later call carries that token as a bearer. A channel's
 * real stream URL is not stable — the catalogue gives a `cmd` string, and the playable URL is minted
 * on demand by [createLink] at tune time. So [liveChannels] stores each channel's `cmd`; the playback
 * path resolves it just-in-time (see [app.opentv.data.repo.CatalogRepository.resolvePlaybackUrl]).
 *
 * ## Portal-path and MAC quirks
 * Portals expose the API under different paths (`/portal.php`, `/server/load.php`, `/stalker_portal/...`);
 * [endpoints] tries the common ones and the first that hands back a token wins, cached per source. The
 * MAC goes in a Cookie, URL-encoded, alongside a MAG-style STB User-Agent — the shape real boxes send.
 *
 * Blocking OkHttp calls wrapped in `withContext(Dispatchers.IO)`, matching [XtreamApi].
 */
class StalkerApi(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    class StalkerException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private data class Session(val token: String, val endpoint: HttpUrl, val expiresAt: Long)

    /** One live token per source, so zapping doesn't re-handshake on every channel. */
    private val sessions = ConcurrentHashMap<Long, Session>()

    /** Add-source test: proves the portal + MAC produce a token. Throws [StalkerException] if not. */
    suspend fun handshakeTest(source: Source) = withContext(Dispatchers.IO) {
        session(source, force = true)
        Unit
    }

    /** Live categories ("genres") for the guide's rail. */
    suspend fun liveCategories(source: Source): List<Category> = withContext(Dispatchers.IO) {
        val s = session(source)
        val arr = call(s, source, type = "itv", action = "get_genres") as? JsonArray ?: return@withContext emptyList()
        arr.mapIndexedNotNull { index, element ->
            val o = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = o.str("id") ?: return@mapIndexedNotNull null
            Category(
                id = id,
                sourceId = source.id,
                name = o.str("title") ?: id,
                kind = StreamKind.LIVE,
                sortIndex = index,
            )
        }
    }

    /** Every live channel. Each carries its `cmd`; the real URL is minted by [createLink] at play time. */
    suspend fun liveChannels(source: Source): List<Channel> = withContext(Dispatchers.IO) {
        val s = session(source)
        val js = call(s, source, type = "itv", action = "get_all_channels")
        // get_all_channels returns { js: { data: [ ... ] } }; some portals return the array directly.
        val data = when (js) {
            is JsonArray -> js
            is JsonObject -> js["data"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        data.mapIndexedNotNull { index, element ->
            val o = element as? JsonObject ?: return@mapIndexedNotNull null
            val id = o.str("id") ?: return@mapIndexedNotNull null
            val name = o.str("name") ?: return@mapIndexedNotNull null
            val number = o.str("number")?.toIntOrNull()
            Channel(
                sourceId = source.id,
                streamId = id,
                name = name,
                categoryId = o.str("tv_genre_id"),
                logoUrl = o.str("logo")?.takeIf { it.isNotBlank() }?.let { absoluteLogo(s.endpoint, it) },
                epgChannelId = o.str("xmltv_id")?.takeIf { it.isNotBlank() },
                number = number,
                // Never played directly — a marker so nothing mistakes it for a real URL; [cmd] is
                // what gets resolved. Distinct per channel so de-dup/quality-grouping still works.
                streamUrl = "stalker://${source.id}/$id",
                cmd = o.str("cmd"),
                sortIndex = number ?: index,
            )
        }
    }

    /** Mint the real, short-lived stream URL for a channel's [cmd]. Null if the portal declines. */
    suspend fun createLink(source: Source, cmd: String): String? = withContext(Dispatchers.IO) {
        val s = session(source)
        val js = call(s, source, type = "itv", action = "create_link") { b ->
            b.addQueryParameter("cmd", cmd)
            b.addQueryParameter("forced_storage", "0")
            b.addQueryParameter("disable_ad", "0")
        }
        val linkCmd = (js as? JsonObject)?.str("cmd") ?: return@withContext null
        stripCmdPrefix(linkCmd)
    }

    // ---- Session / handshake -----------------------------------------------------------------

    private fun session(source: Source, force: Boolean = false): Session {
        val now = System.currentTimeMillis()
        if (!force) sessions[source.id]?.let { if (it.expiresAt > now) return it }
        val mac = source.macAddress?.trim().orEmpty()
        if (mac.isEmpty()) throw StalkerException("This portal needs a MAC address (e.g. 00:1A:79:xx:xx:xx).")
        var lastError: Throwable? = null
        for (endpoint in endpoints(source)) {
            val token = runCatching { handshake(source, endpoint) }
                .onFailure { lastError = it }
                .getOrNull()
            if (token != null) {
                // Best-effort profile activation — some portals won't serve itv data until it's called.
                runCatching { getProfile(source, endpoint, token) }
                return Session(token, endpoint, now + TOKEN_TTL_MILLIS).also { sessions[source.id] = it }
            }
        }
        throw StalkerException(
            "The portal didn't accept this MAC address, or the URL is wrong.",
            lastError,
        )
    }

    private fun handshake(source: Source, endpoint: HttpUrl): String? {
        val url = endpoint.newBuilder()
            .addQueryParameter("type", "stb")
            .addQueryParameter("action", "handshake")
            .addQueryParameter("token", "")
            .addQueryParameter("JsHttpRequest", "1-xml")
            .build()
        val body = execute(source, url, token = null) ?: return null
        return (body as? JsonObject)?.obj("js")?.str("token")?.takeIf { it.isNotBlank() }
    }

    private fun getProfile(source: Source, endpoint: HttpUrl, token: String) {
        val url = endpoint.newBuilder()
            .addQueryParameter("type", "stb")
            .addQueryParameter("action", "get_profile")
            .addQueryParameter("hd", "1")
            .addQueryParameter("stb_type", "MAG250")
            .addQueryParameter("JsHttpRequest", "1-xml")
            .build()
        execute(source, url, token)
    }

    // ---- HTTP --------------------------------------------------------------------------------

    private fun call(
        session: Session,
        source: Source,
        type: String,
        action: String,
        extra: (HttpUrl.Builder) -> Unit = {},
    ): JsonElement? {
        val builder = session.endpoint.newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("action", action)
            .addQueryParameter("JsHttpRequest", "1-xml")
        extra(builder)
        val body = execute(source, builder.build(), session.token) ?: return null
        return (body as? JsonObject)?.get("js")
    }

    private fun execute(source: Source, url: HttpUrl, token: String?): JsonElement? {
        val mac = source.macAddress?.trim().orEmpty()
        val cookie = "mac=${URLEncoder.encode(mac, "UTF-8")}; stb_lang=en; timezone=Europe/London"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", stbUserAgent(source))
            .header("X-User-Agent", "Model: MAG250; Link: WiFi")
            .header("Cookie", cookie)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) return null
                json.parseToJsonElement(text)
            }
        }.onFailure { Log.w(TAG, "Stalker request failed", it) }.getOrNull()
    }

    /** Candidate API endpoints for a portal URL, most common first. First that handshakes wins. */
    private fun endpoints(source: Source): List<HttpUrl> {
        val raw = source.url.trim().trimEnd('/')
        // A portal URL is often given as ".../c" (the web-client path); the API lives at the root.
        val root = raw.removeSuffix("/c").trimEnd('/')
        return listOf(
            "$root/portal.php",
            "$root/server/load.php",
            "$root/stalker_portal/server/load.php",
        ).mapNotNull { it.toHttpUrlOrNull() }
    }

    /** A MAG-box style STB User-Agent when the source hasn't set a specific one. */
    private fun stbUserAgent(source: Source): String {
        val ua = source.userAgent
        return if (ua.isBlank() || ua == Source.DEFAULT_USER_AGENT) DEFAULT_STB_UA else ua
    }

    /** Provider logos are often relative to the portal host. */
    private fun absoluteLogo(endpoint: HttpUrl, logo: String): String =
        if (logo.startsWith("http")) logo
        else "${endpoint.scheme}://${endpoint.host}:${endpoint.port}/${logo.trimStart('/')}"

    /** create_link returns e.g. "ffmpeg http://…" / "auto http://…" / a bare URL; keep just the URL. */
    private fun stripCmdPrefix(cmd: String): String {
        val trimmed = cmd.trim()
        val httpIdx = trimmed.indexOf("http")
        return if (httpIdx > 0) trimmed.substring(httpIdx).trim() else trimmed
    }

    private companion object {
        const val TAG = "StalkerApi"
        const val TOKEN_TTL_MILLIS = 4 * 60 * 1000L // handshake tokens are short-lived; re-mint often
        const val DEFAULT_STB_UA =
            "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) " +
                "MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    }
}

// ---- Minimal JSON helpers (self-contained; the Xtream accessors are private to that file) --------

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
