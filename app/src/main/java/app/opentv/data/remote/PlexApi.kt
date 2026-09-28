/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.data.remote

import android.util.Log
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
     * Asks plex.tv for a fresh sign-in code.
     *
     * Note what is NOT here: `strong=true`. Plex offers two kinds of PIN. A *strong* pin is a long
     opaque string meant for a server-to-server redirect and is never typed by a person; a plain
     pin is the four-character code a human enters on a television. Asking for the strong one
     * produced a 29-character code that nobody could enter anywhere, which is exactly what the
     * first attempt on a real device showed.
     *
     * The PIN lives for a limited time and this is the first half of the handshake only - the
     * account's token does not exist yet, and is collected later by [awaitAuthToken].
     */
    suspend fun createPin(headers: Map<String, String>): PlexParser.Pin = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${PlexUrls.PLEX_TV}/api/v2/pins")
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
        if (pin.code.length !in 4..8 || !pin.code.all { it.isLetterOrDigit() }) {
            val length = pin.code.length
            throw PlexException(
                "Plex returned a $length character sign-in code, which nobody can type. " +
                    "This is not the four-character code a TV is meant to use.",
            )
        }
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
        val body = execute(request, "Plex would not confirm the sign-in.").use { it.body?.string().orEmpty() }
        PlexParser.authToken(body.byteInputStream())
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
