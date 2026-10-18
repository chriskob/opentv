/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import android.util.Log
import app.opentv.data.model.TorrentFile
import app.opentv.data.model.TorrentStatus
import java.net.URLDecoder
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the TorrServer engine OpenTV bundles in its own APK.
 *
 * ## Scope
 * TorrServer is a BitTorrent engine with an HTTP front end: you hand it a magnet link, it finds
 * peers and serves the file's bytes over `http://127.0.0.1:<port>/stream/...`. OpenTV spawns it as a
 * separate process (see [app.opentv.torrent.TorrentEngine]) and this class is the only thing that
 * talks to it. Nothing here is a torrent *client*: no peer protocol, no piece map, no DHT. The app
 * asks for a list, adds a link, reads a status, and hands a localhost URL to the player — the engine
 * owns everything else.
 *
 * ## Why a URL provider instead of a fixed base URL
 * The engine's port is picked at runtime (a free port, so a second copy of the app or the user's own
 * TorrServer can't collide with it), so the address is only known once the process is up. Passing a
 * `() -> String?` keeps one client instance alive across engine restarts, and every call simply
 * fails softly while the engine is down.
 *
 * ## Blocking on purpose
 * As with [StremioClient] and [TmdbClient], methods block, and callers already run them inside
 * `withContext(Dispatchers.IO)`.
 */
class TorrServerClient(
    private val baseUrl: () -> String?,
    private val http: OkHttpClient,
) {

    /** What went wrong when adding a link, in the engine's own words where it offered any. */
    sealed interface AddResult {
        data class Added(val status: TorrentStatus) : AddResult
        data class Refused(val reason: String) : AddResult

        /** The engine isn't running (never started, crashed, or this device has no engine binary). */
        data object EngineDown : AddResult
    }

    /**
     * The engine's version string, or null if nothing answers. This is the readiness probe: the
     * endpoint needs no authentication, touches no torrent, and returns immediately, so it is safe
     * to call every 250 ms while the process boots.
     */
    fun echo(): String? = runCatching {
        val url = url("/echo") ?: return null
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()?.trim()?.takeIf { it.isNotEmpty() }
        }
    }.onFailure { Log.w(TAG, "echo failed", it) }.getOrNull()

    /**
     * Whether the engine can actually hand out a byte of [url] right now.
     *
     * A stream URL is a promise the engine makes before the swarm has delivered anything: until
     * the first piece lands, a player that opens it gets an empty answer (and, on its Range
     * request, HTTP 416) and gives up — which looks exactly like a dead link while the torrent is
     * merely still warming up. Asking for one byte resolves that in a single request, closes it
     * again, and holds no reader open, so the screen can wait for the swarm instead of handing the
     * player a URL it cannot fill.
     */
    /**
     * Whether the engine can already hand out the start of [url].
     *
     * Probes the first megabyte rather than a single byte. One byte proves the first piece has
     * arrived, but the player then opens onto a black screen while the decoder waits for the rest
     * of the opening GOP; the engine blocks a range request until the bytes exist, so a response
     * for the megabyte means there is something real to play. Shorter files answer with what they
     * have, hence the half-probe floor instead of an exact length.
     */
    fun canServe(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${PROBE_BYTES - 1}")
            .build()
        http.newCall(request).execute().use { response ->
            val length = response.body?.contentLength() ?: -1L
            // -1 is a chunked answer: the engine streams the range as the pieces arrive.
            response.isSuccessful && (length < 0L || length >= PROBE_BYTES / 2)
        }
    }.onFailure { Log.d(TAG, "stream probe failed", it) }.getOrDefault(false)

    /**
     * Every torrent the engine holds in memory. Deliberately the `list` action and not `get`:
     * `get` on a hash the engine has dropped loads it again from its database and starts
     * downloading it — a status refresh must never resurrect a torrent.
     */
    fun list(): List<TorrentStatus> {
        val body = post("/torrents", buildJsonObject { put("action", JsonPrimitive("list")) })
            ?: return emptyList()
        return parseStatuses(body)
    }

    /** One torrent's status, or null when the engine no longer has it (it returns 404). */
    /**
     * One torrent's status, or null when the engine no longer holds it.
     *
     * Deliberately `list`, not `get`: `get` on a hash the engine has dropped reloads it from its
     * database and starts the download again, so a one-second status poll would resurrect a
     * torrent the viewer had stopped - and begin fetching it from zero.
     */
    fun status(hash: String): TorrentStatus? {
        if (hash.isBlank()) return null
        return list().firstOrNull { it.hash.equals(hash, ignoreCase = true) }
    }

    /**
     * Add a magnet (or `.torrent` URL, or bare info hash) and start looking for peers.
     *
     * [saveToDb] stays false for pasted links: they are a one-off watch, and a link the engine has
     * forgotten leaves nothing behind — no database row, no stale list entry. Metadata arrives
     * asynchronously, so the returned status is normally `stat = STAT_ADDED` with no files yet; poll
     * [status] for the file list.
     */
    fun add(link: String, title: String? = null, saveToDb: Boolean = false): AddResult {
        val body = buildJsonObject {
            put("action", JsonPrimitive("add"))
            put("link", JsonPrimitive(link))
            if (title != null) put("title", JsonPrimitive(title))
            if (saveToDb) put("save_to_db", JsonPrimitive(true))
        }
        val result = call("/torrents", body) ?: return AddResult.EngineDown
        if (!result.ok) return AddResult.Refused(errorMessage(result.body) ?: "HTTP ${result.code}")
        val status = parseStatus(result.body) ?: return AddResult.Refused("the engine gave no status")
        return AddResult.Added(status)
    }

    /** Forget a torrent: stops it, frees its cache, and deletes any files it wrote. */
    fun remove(hash: String): Boolean = simpleAction("rem", hash)

    /**
     * Stop a torrent and release its memory without forgetting it. The engine does this by itself
     * once a torrent has had no reader for `torrentDisconnectTimeout` seconds; calling it explicitly
     * is how playback ending stops the swarm immediately instead of half a minute later.
     */
    fun drop(hash: String): Boolean = simpleAction("drop", hash)

    /** Forget every torrent. */
    fun wipe(): Boolean {
        val body = buildJsonObject { put("action", JsonPrimitive("wipe")) }
        return (call("/torrents", body) ?: return false).ok
    }

    /**
     * A read-back of the engine's live settings, checking the promises OpenTV makes about it: it must
     * not upload, must not write to disk, and must not advertise itself on the local network.
     *
     * The engine is a separate program with its own defaults and its own future releases, so the app
     * verifies rather than assumes. An empty list means the engine agrees with the app; anything in
     * it is a name OpenTV should surface instead of quietly claiming "never uploads".
     */
    fun violatedAssurances(): List<String> {
        val body = post("/settings", buildJsonObject { put("action", JsonPrimitive("get")) })
            ?: return emptyList()
        val expected = listOf(
            "DisableUpload" to true,
            "UseDisk" to false,
            "EnableLPD" to false,
            "EnableBonjour" to false,
            "DisableUPNP" to true,
        )
        return expected.filter { (key, wanted) -> parseSettingBool(body, key)?.let { it != wanted } ?: false }
            .map { it.first }
    }

    private fun simpleAction(action: String, hash: String): Boolean {
        if (hash.isBlank()) return false
        val body = buildJsonObject {
            put("action", JsonPrimitive(action))
            put("hash", JsonPrimitive(hash))
        }
        return (call("/torrents", body) ?: return false).ok
    }

    private fun post(path: String, body: JsonObject): String? = call(path, body)?.takeIf { it.ok }?.body

    private data class Reply(val code: Int, val body: String, val ok: Boolean)

    /**
     * One POST of a JSON action, with the engine's error text preserved on failure. The body of a
     * failed call is what makes a bad magnet readable ("error parse link") instead of a bare code.
     */
    private fun call(path: String, body: JsonObject): Reply? {
        val url = url(path) ?: return null
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                Reply(code = response.code, body = text, ok = response.isSuccessful)
            }
        }.onFailure { Log.w(TAG, "request to $path failed", it) }.getOrNull()
    }

    private fun url(path: String): HttpUrl? {
        val base = baseUrl()?.trimEnd('/') ?: return null
        return "$base$path".toHttpUrlOrNull()
    }

    companion object {
        private const val TAG = "TorrServerClient"

        /** Head the readiness gate waits for before it opens the player: one megabyte. */
        private const val PROBE_BYTES = 1_048_576L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * The parser for every body this class reads. Lenient with unknown keys because the engine
         * adds fields between releases, and strict about nothing else: a field OpenTV doesn't know
         * about is not an error.
         */
        private val PARSER = Json { ignoreUnknownKeys = true; isLenient = true }

        /** The engine's error message for a failed call, if it sent one. */
        private fun errorMessage(body: String): String? = runCatching {
            PARSER.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.takeIf { it.isNotBlank() }

        // ---------------------------------------------------------------- pure parsing/reasoning
        // Everything below is a pure function over bytes and strings so it can be unit-tested
        // against shapes captured from the engine instead of a live server. They are public only for
        // that: TorrServerClientTest is the other caller.

        /** Parse `POST /torrents {action:list}`. A malformed body reads as "no torrents". */
        fun parseStatuses(body: String): List<TorrentStatus> = runCatching {
            PARSER.parseToJsonElement(body).jsonArray
                .mapNotNull { element -> parseStatus(element.toString()) }
        }.getOrDefault(emptyList())

        /**
         * Parse a single `TorrentStatus`, or null when the body isn't one. A hash is required: it is
         * the one field every real status carries (`add` and `get` both know the info hash before any
         * metadata arrives), so its absence means this wasn't a status at all.
         */
        fun parseStatus(body: String): TorrentStatus? = runCatching {
            PARSER.decodeFromString(TorrentStatus.serializer(), body)
        }.getOrNull()?.takeIf { it.hash.isNotBlank() }

        /** Read one boolean out of `POST /settings {action:get}`, or null if it isn't there. */
        fun parseSettingBool(body: String, key: String): Boolean? = runCatching {
            PARSER.parseToJsonElement(body).jsonObject[key]?.jsonPrimitive?.contentOrNull
                ?.toBooleanStrictOrNull()
        }.getOrNull()

        private val HEX40 = Regex("^[0-9a-fA-F]{40}$")
        private val BASE32_32 = Regex("^[A-Za-z2-7]{32}$")

        /**
         * The info hash a magnet asks for, normalised for display and comparison: lowercase hex for a
         * hex hash, upper-case for the older base32 form, null when there is no usable hash.
         *
         * This is only ever used to reason about a link the user pasted. The hash OpenTV keys its
         * list on is the one the *engine* reports after adding, which is always hex.
         */
        fun magnetInfoHash(link: String): String? {
            val xt = magnetParam(link, "xt") ?: return null
            val value = xt.substringAfter("urn:btih:", "").trim()
            return when {
                HEX40.matches(value) -> value.lowercase(Locale.ROOT)
                BASE32_32.matches(value) -> value.uppercase(Locale.ROOT)
                else -> null
            }
        }

        private fun magnetParam(link: String, name: String): String? {
            val trimmed = link.trim()
            if (!trimmed.startsWith("magnet:", ignoreCase = true)) return null
            // Hand-rolled rather than Android's Uri, so the magnet rules stay a pure function that can
            // be unit-tested on the JVM (Uri is a stub outside Robolectric) — and because a magnet is
            // only ever `?a=b&c=d` with percent-escapes, which is the whole of what this handles.
            val query = trimmed.substringAfter('?', "")
            if (query.isEmpty()) return null
            return query.split('&')
                .map { part -> part.substringBefore('=') to part.substringAfter('=', "") }
                .firstOrNull { (key, _) -> key.equals(name, ignoreCase = true) }
                ?.second
                ?.let { value -> runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value) }
                ?.takeIf { it.isNotBlank() }
        }

        /**
         * Whether the engine has any chance of accepting this text, so the screen can refuse obvious
         * junk without a round trip. It mirrors what the engine's own link parser accepts: a magnet
         * with a usable info hash, an http(s) URL (a `.torrent` file or a magnet behind a redirect),
         * a `torrs://` share link, or a bare info hash.
         */
        fun isTorrentLink(raw: String): Boolean {
            val link = raw.trim()
            if (link.isEmpty()) return false
            return when {
                link.startsWith("torrs://", ignoreCase = true) -> link.length > "torrs://".length
                link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true) -> true
                link.startsWith("magnet:", ignoreCase = true) -> magnetInfoHash(link) != null
                else -> HEX40.matches(link) || BASE32_32.matches(link)
            }
        }

        /** Containers Media3 opens. AVI and the ASF family have no extractor, so they're excluded. */
        private val VIDEO_EXTENSIONS = setOf(
            "mkv", "mp4", "m4v", "mov", "webm", "ts", "m2ts", "mts", "mpg", "mpeg", "vob", "flv",
        )

        /** Video containers the bundled player cannot open, so the screen can say so up front. */
        private val UNPLAYABLE_EXTENSIONS = setOf("avi", "wmv", "asf", "rm", "rmvb", "divx", "iso", "img")

        fun extension(path: String): String =
            path.substringAfterLast('/', path).substringAfterLast('.', "").lowercase(Locale.ROOT)

        fun isVideoFile(path: String): Boolean = extension(path) in VIDEO_EXTENSIONS

        fun isUnplayableContainer(path: String): Boolean = extension(path) in UNPLAYABLE_EXTENSIONS

        /** A torrent's video files, biggest first — the order a picker wants. */
        fun videoFiles(files: List<TorrentFile>): List<TorrentFile> =
            files.filter { isVideoFile(it.path) }.sortedByDescending { it.length }

        /**
         * The file to play when nobody has chosen one: the biggest video in the torrent. For a
         * single-file torrent that is the file; for a season pack it is usually the largest episode.
         */
        fun biggestVideo(files: List<TorrentFile>): TorrentFile? = videoFiles(files).firstOrNull()

        /** How much of the torrent has arrived, 0-100, or -1 when the size isn't known yet. */
        fun percentLoaded(status: TorrentStatus): Int {
            if (status.torrentSize <= 0L) return -1
            return ((status.loadedSize * 100) / status.torrentSize).toInt().coerceIn(0, 100)
        }

        /**
         * The URL the player should fetch: the engine's own media endpoint for one file of a
         * torrent.
         *
         * `index` is the file id from the status, and the path segment is the file's name — the
         * engine only uses it to name a playlist, but the player uses its extension to decide what
         * it is looking at, which is why it must keep the real one. `play` starts the stream and
         * `preload` tells the engine to start fetching that file's window before the player asks.
         */
        fun streamUrl(baseUrl: String, link: String, file: TorrentFile): String? {
            val base = baseUrl.trimEnd('/')
            val name = file.path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: "stream"
            val url = "$base/".toHttpUrlOrNull() ?: return null
            return url.newBuilder()
                .addPathSegment("stream")
                // addPathSegment encodes the name, so a torrent whose files contain spaces, quotes or
                // non-Latin characters still produces a fetchable URL.
                .addPathSegment(name)
                .addQueryParameter("link", link)
                .addQueryParameter("index", file.id.toString())
                .addQueryParameter("play", "")
                .addQueryParameter("preload", "")
                .build()
                .toString()
        }

        /**
         * Whether a play URL is one of the engine's own streams. The player words its "nothing is
         * arriving" message for a provider, and a torrent is not one: it has no provider to blame,
         * it is waiting on peers.
         */
        fun isEngineStreamUrl(url: String): Boolean {
            val parsed = url.toHttpUrlOrNull() ?: return false
            val loopback = parsed.host == "127.0.0.1" || parsed.host == "localhost" ||
                parsed.host == "::1" || parsed.host == "[::1]"
            return loopback && parsed.encodedPath.startsWith("/stream/")
        }

        /** Human-readable byte count for the status line. */
        fun formatBytes(bytes: Long): String {            if (bytes < 1024) return "$bytes B"
            val units = listOf("KB", "MB", "GB", "TB")
            var value = bytes.toDouble() / 1024
            var unit = 0
            while (value >= 1024 && unit < units.lastIndex) {
                value /= 1024
                unit++
            }
            return if (value >= 100) "${value.roundToIntSafe()} ${units[unit]}"
            else String.format(Locale.US, "%.1f %s", value, units[unit])
        }

        private fun Double.roundToIntSafe(): Int = if (this >= Int.MAX_VALUE) Int.MAX_VALUE else Math.round(this).toInt()

        /** Bit rate for the status line, from the engine's own figure. */
        fun formatSpeed(bytesPerSecond: Double): String =
            if (bytesPerSecond <= 0) "" else "${formatBytes(bytesPerSecond.toLong())}/s"
    }
}
