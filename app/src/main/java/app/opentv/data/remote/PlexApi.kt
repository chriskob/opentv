/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.data.remote

import android.util.Log
import app.opentv.data.model.PlexEpisode
import app.opentv.data.model.PlexMediaPart
import app.opentv.data.model.PlexRecentItem
import app.opentv.data.model.PlexSection
import app.opentv.data.parser.PlexParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * Talks to a Plex server, and to plex.tv for signing in.
 *
 * The two are deliberately separate hosts and separate methods. plex.tv is only ever used for the
 * sign-in handshake, because it is the only place an account's token can be obtained; everything
 * else - libraries, artwork, playback - goes to the server's own address, which is whatever the
 * user typed. That separation matters here: OpenTV reaches the server through a reverse proxy, so
 * there is no "discover my local servers" step and no dependency on plex.tv staying reachable
 * after sign-in.
 */
class PlexApi(private val http: OkHttpClient) {

    class PlexException(message: String, cause: Throwable? = null) : Exception(message, cause)

    // ---- sign-in ----------------------------------------------------------------------------

    /**
     * Asks plex.tv for a sign-in PIN.
     *
     * `strong=true` is required, and the reason is worth recording because it has been got wrong
     * twice here. Plex offers two kinds: a PLAIN pin is a four-character code a person types into
     * a web form, and a STRONG pin is a long opaque string that the browser approval link carries
     * for you.
     *
     * `app.plex.tv/auth#?clientID=..&code=..` is the second kind's flow. Handing it a plain
     * four-character code produces "We were unable to complete this request" - after the viewer
     * has already signed in, which is the worst possible moment to fail. That is exactly what a
     * real attempt did.
     *
     * The strong pin was dropped once already, on the grounds that a 29-character string is absurd
     * to display on a television. It is - but the display was the bug, not the pin. With a QR code
     * on screen nothing is ever typed, which makes the strong pin the right choice again. The code
     * is an identifier for the browser to carry, not an instruction for a human.
     *
     * The PIN lives for a limited time and this is the first half of the handshake only - the
     * account's token does not exist yet, and is collected by [awaitAuthToken].
     */
    suspend fun createPin(headers: Map<String, String>): PlexParser.Pin = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${PlexUrls.PLEX_TV}/api/v2/pins?strong=true")
            .post("".toRequestBody(null))
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        val body = execute(request, "Plex would not start a sign-in.")
            .use { it.body?.string().orEmpty() }
        val pin = PlexParser.pin(body.byteInputStream())
            ?: throw PlexException("Plex would not start a sign-in. Check the box can reach plex.tv.")
        // A code nobody could type is a failure, not something to put on a television screen. The
        // first attempt displayed a 29-character string because a strong pin had been requested;
        // refusing it here means that class of mistake is loud instead of merely unusable.
        if (pin.code.isBlank()) {
            throw PlexException("Plex returned an empty sign-in code.")
        }
        // INFO, not DEBUG: this box is visibly not emitting debug-level lines from the app, so a
        // DEBUG diagnostic is a diagnostic that does not exist. Length only, never the value.
        Log.i(TAG, "sign-in requested: pin id=${pin.id}, code length=${pin.code.length}")
        // Last expression, not `return`: withContext's block is not an inline lambda, so a
        // non-local return out of it is a compile error rather than a style choice.
        pin
    }

    /**
     * Polls a sign-in that the viewer has approved on their phone.
     *
     * Returns null while it is still pending, which is the *normal* answer for the first several
     * polls and is deliberately not an error - a caller that treated it as one would tell the
     * viewer their sign-in failed the instant they opened the page.
     */
    suspend fun awaitAuthToken(pinId: Long, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${PlexUrls.PLEX_TV}/api/v2/pins/$pinId")
            .get()
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        // What came back, before it is interpreted. A poll that silently returns null for a
        // hundred and fifty attempts is indistinguishable from one that is working and waiting,
        // and the difference is the whole question when a viewer says "the browser said yes but
        // the television is still waiting".
        val response = http.newCall(request).execute()
        val code = response.code
        val body = response.use { it.body?.string().orEmpty() }
        val token = PlexParser.authToken(body.byteInputStream())
        Log.i(
            TAG,
            "poll pin=$pinId -> http=$code, bodyLen=${body.length}, " +
                (if (token != null) "TOKEN RECEIVED" else "no token") +
                ", looksLike=${body.take(300).replace(Regex("\\s+"), " ")}",
        )
        // A 404 is not a pending PIN - Plex says the code is gone (redeemed or expired). Returning
        // null here made the caller log "still pending" for a hard failure, which is how a dead
        // sign-in sat on screen looking like progress. Said plainly instead.
        if (code == 404) {
            throw PlexException("Plex no longer recognises that sign-in code (it was used, or expired).")
        }
        token
    }

    /** Confirms a token works and reports the account behind it. */
    suspend fun verifyAccount(serverBase: String, token: String): PlexParser.Account? = withContext(Dispatchers.IO) {
        val url = endpoint(serverBase, "library/sections", token, "type=1") ?: return@withContext null
        val body = execute(url, "Plex did not answer.").use { it.body?.string().orEmpty() }
        PlexParser.account(body.byteInputStream())
    }

    // ---- library ----------------------------------------------------------------------------

    suspend fun sections(serverBase: String, token: String): List<PlexSection> = withContext(Dispatchers.IO) {
        val url = endpoint(serverBase, "library/sections", token, "type=1")
            ?: throw PlexException("That Plex address does not look like a web address.")
        val body = execute(url, "Plex did not return any libraries.").use { it.body?.string().orEmpty() }
        PlexParser.sections(body.byteInputStream())
    }

    /**
     * The newest items in one section.
     *
     * [limit] is passed to Plex as its own `X-Plex-Container-Size` so the server does the
     * trimming - asking for a library's whole recently-added feed and discarding all but ten would
     * transfer a lot of XML to keep ten rows.
     */
    suspend fun recentlyAdded(
        serverBase: String,
        token: String,
        sectionKey: String,
        limit: Int,
    ): List<PlexRecentItem> = withContext(Dispatchers.IO) {
        val url = endpoint(
            serverBase,
            "library/sections/$sectionKey/recentlyAdded",
            token,
            "X-Plex-Container-Start=0",
            "X-Plex-Container-Size=$limit",
            "includeGuids=0",
        ) ?: throw PlexException("That Plex address does not look like a web address.")
        val body = execute(url, "Plex would not return that library.").use { it.body?.string().orEmpty() }
        PlexParser.recentlyAdded(body.byteInputStream())
    }

    /**
     * The playable files behind one item.
     *
     * Resolved at play time rather than stored, because the part path is not stable: Plex can move a
     * file between versions of a library, and a stored path that has gone stale is a failed play
     * with no way to recover. One extra round trip at play is a better trade than a dead row.
     */
    suspend fun mediaParts(
        serverBase: String,
        token: String,
        ratingKey: String,
    ): List<PlexMediaPart> = withContext(Dispatchers.IO) {
        val url = endpoint(serverBase, "library/metadata/$ratingKey", token)
            ?: throw PlexException("That Plex address does not look like a web address.")
        val body = execute(url, "Plex would not describe that item.").use { it.body?.string().orEmpty() }
        PlexParser.metadata(body.byteInputStream()).ifEmpty {
            throw PlexException("Plex has no playable file for that item.")
        }
    }

    /**
     * Fetches one piece of artwork and reports what came back, so a failure names its cause.
     *
     * "Every poster is broken" is not a diagnosis. This distinguishes the cases that look
     * identical from the UI and need completely different fixes: a 403 or 503 is the reverse proxy
     * refusing the request, a 404 is a wrong path, a 200 whose content type is not an image is a
     * challenge or error page dressed up as artwork, and a 200 image means the loader - not the
     * server - is at fault.
     *
     * Runs once per sync at most, and reports the status and content type only. The token goes in
     * the header, exactly as the image loader now sends it, so this also proves the header works
     * independently of Coil.
     */
    suspend fun probeArtwork(serverBase: String, token: String, ratingKey: String) {
        withContext(Dispatchers.IO) {
            val url = endpoint(serverBase, "library/metadata/$ratingKey/thumb", token, "width=300")
            if (url == null) {
                Log.w(TAG, "artwork probe: '$serverBase' is not a usable address")
                return@withContext
            }
            val request = Request.Builder()
                .url(url)
                .header("X-Plex-Token", token)
                .header("User-Agent", "OpenTV")
                .get()
                .build()
            val outcome = runCatching {
                http.newCall(request).execute().use { response ->
                    val type = response.header("Content-Type") ?: "(none)"
                    "http=${response.code} contentType=$type bytes=${response.body?.contentLength()}"
                }
            }.getOrElse { "threw ${it.javaClass.simpleName}: ${it.message}" }
            Log.i(TAG, "artwork probe: $outcome")
        }
    }

    // ---- library ----------------------------------------------------------------------------

    /**
     * The episodes of one show, newest first.
     *
     * A series is a container, not a file: `/library/metadata/{seriesKey}` describes the show and
     * carries no playable part, so asking it for parts returned nothing and every show on the
     * shelf reported "no playable file". The episodes are one level down, on `/children`.
     *
     * Ordered by index descending, because a "recently added" shelf should open the latest of a
     * show. Plex returns children in its own order and does not promise that is the useful one, so
     * it is sorted here rather than assumed.
     */
    suspend fun episodes(serverBase: String, token: String, seriesKey: String): List<PlexEpisode> =
        withContext(Dispatchers.IO) {
            val url = endpoint(
                serverBase,
                "library/metadata/$seriesKey/children",
                token,
                "includeGuids=0",
            ) ?: throw PlexException("That Plex address does not look like a web address.")
            val body = execute(url, "Plex would not list that show's episodes.").use { it.body?.string().orEmpty() }
            PlexParser.episodes(body.byteInputStream()).sortedByDescending { it.index }
        }

    /**
     * Tells Plex where playback of one item stands.
     *
     * Fire-and-forget from the caller's point of view: a failed ping must never disturb playback,
     * so failures throw and the caller decides how loudly to care (the player screen swallows them
     * after the first log line - a dead ping every 15 seconds is not worth waking anyone for).
     * `state` is playing, paused, buffering or stopped; the server derives progress and eventual
     * watched status from the stream of these.
     */
    suspend fun reportTimeline(
        serverBase: String,
        token: String,
        clientIdentifier: String,
        ratingKey: String,
        state: String,
        timeMs: Long,
        durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        val url = PlexUrls.timeline(serverBase, ratingKey, token, state, timeMs, durationMs)
        val request = Request.Builder()
            .url(url)
            .header("X-Plex-Client-Identifier", clientIdentifier)
            .get()
            .build()
        execute(request, "Plex would not take the playback report.").close()
    }

    /**
     * Marks one item watched or unwatched, explicitly.
     *
     * Timeline pings move progress, but near the end of a film nobody wants to depend on exactly
     * which ping landed last. This is the button: immediate, unconditional, and reflected in Plex
     * straight away.
     */
    suspend fun setWatched(serverBase: String, token: String, ratingKey: String, watched: Boolean) =
        withContext(Dispatchers.IO) {
            val url = PlexUrls.scrobble(serverBase, ratingKey, token, watched)
            execute(Request.Builder().url(url).get().build(), "Plex would not change the watched mark.").close()
        }

    /**
     * Whether Plex already counts this item as watched, or null when it cannot be told.
     *
     * Read from the item's own metadata (`viewCount`), so the watched button can show its true
     * state instead of guessing. Null on any failure - an unknown state shows "Mark watched"
     * rather than a wrong tick.
     */
    suspend fun watchedState(serverBase: String, token: String, ratingKey: String): Boolean? =
        withContext(Dispatchers.IO) {
            val url = endpoint(serverBase, "library/metadata/$ratingKey", token)
                ?: return@withContext null
            val body = execute(url, "Plex would not describe that item.").use { it.body?.string().orEmpty() }
            PlexParser.videoViewCount(body.byteInputStream())?.let { it > 0 }
        }

    // ---- plumbing ---------------------------------------------------------------------------

    private fun endpoint(
        serverBase: String,
        path: String,
        token: String,
        vararg params: String,
    ): HttpUrl? {
        val base = serverBase.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
        return base.newBuilder()
            .addPathSegments(path)
            .apply {
                params.forEach { param ->
                    val eq = param.indexOf('=')
                    if (eq > 0) {
                        addQueryParameter(param.substring(0, eq), param.substring(eq + 1))
                    }
                }
                // Always last, so a diagnostic log of the request has the token as a single
                // recognisable segment rather than interleaved with paging parameters.
                addQueryParameter(PlexUrls.TOKEN_PARAM, token)
            }
            .build()
    }

    /**
     * Runs [request] and hands back a successful response, or throws something a viewer can be
     * told. The status is included because Plex's own failures are opaque: a 401 here means the
     * token is wrong or the server is unreachable, and the two need very different advice.
     */
    private fun execute(request: Request, what: String): Response {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw PlexException(
                "Could not reach Plex. Check the address, and that this box can see it. (${e.javaClass.simpleName})",
                e,
            )
        }
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            val hint = when (code) {
                401, 403 -> "Plex refused the sign-in. Sign in again."
                404 -> "Plex has nothing at that address."
                in 500..599 -> "The Plex server had a problem. It may be restarting."
                else -> ""
            }
            throw PlexException("$what ($code). $hint".trim())
        }
        return response
    }

    private fun execute(url: HttpUrl, what: String): Response = execute(
        Request.Builder().url(url).get().build(),
        what,
    )

    companion object {
        const val TAG = "OpenTV-Plex"

        /** Never let a token reach logcat, whatever the url looks like. */
        fun safe(url: String, token: String? = null): String = PlexUrls.redact(url, token).also {
            Log.d(TAG, it)
        }
    }
}
