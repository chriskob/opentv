/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.data.remote

/**
 * Builds the URLs OpenTV asks a Plex server for.
 *
 * Kept apart from [PlexApi] deliberately, and free of any Android or network dependency, because
 * these strings are where Plex integration quietly goes wrong and they can be checked without a
 * server. Three things are encoded here that are not obvious:
 *
 *  - **The token travels in the query string.** Plex accepts it as a header too, but a token in a
 *    URL is what reaches the server, and Plex's own documentation specifies the query form for
 *    media URLs specifically.
 *  - **Playback asks for HLS.** `protocol=hls` makes the server serve an MPEG-DASH/HLS playlist
 *    instead of the raw file. This is not a preference. OpenTV reaches the server through a
 *    reverse proxy, and a single multi-gigabyte response through a proxy that buffers is a
 *    request that times out and cannot be seeked. A segmented playlist is thousands of small
 *    independent requests, which is the shape a proxy handles well - and it is what gives ExoPlayer
 *    a real, seekable timeline.
 *  - **Nothing here logs or embeds the token in an error message.** [redact] exists so that a
 *    token can appear in a debug string without leaking it, which is the whole reason it is a
 *    function rather than a convention.
 */
object PlexUrls {

    const val TOKEN_PARAM = "X-Plex-Token"

    /** The host a Plex account is reached through for sign-in. Never the media server. */
    const val PLEX_TV = "https://plex.tv"

    /**
     * A stable per-install identifier Plex uses to pair a sign-in request with the device that
     * asked for it. Without it, a PIN cannot be polled - Plex hands the token to whichever client
     * presents the same identifier that requested the PIN.
     */
    fun clientHeaders(clientIdentifier: String, product: String, version: String, device: String, platform: String): Map<String, String> = mapOf(
        "X-Plex-Client-Identifier" to clientIdentifier,
        "X-Plex-Product" to product,
        "X-Plex-Version" to version,
        "X-Plex-Platform" to platform,
        "X-Plex-Device" to device,
        "X-Plex-Device-Name" to device,
    )

    /**
     * The sign-in URL the viewer opens on their phone to approve the PIN.
     *
     * This is a fragment URL - the code and identifiers live after `#`, so they never reach the
     * Plex web server as part of the request and are not left in browser history as query
     * parameters. That is Plex's required format, not a preference, and getting it wrong produces
     * a page that loads and never links the account.
     *
     * Encoding is done by hand rather than with [java.net.URLEncoder], because `URLEncoder` is a
     * *form* encoder: it writes a space as `+`, which is correct in a query string and wrong in a
     * fragment, where `+` is a literal plus. A device name of "OpenTV on AFTR" would have gone
     * across as "OpenTV+on+AFTR". Percent-encoding is correct in both places, so that is what is
     * used here.
     */
    fun pinApprovalUrl(clientIdentifier: String, code: String, product: String, device: String): String {
        fun enc(value: String): String = buildString {
            for (byte in value.toByteArray(Charsets.UTF_8)) {
                val ch = byte.toInt().toChar()
                if (ch.isLetterOrDigit() && ch.code < 128 || ch in "-_.~") {
                    append(ch)
                } else {
                    append('%').append("%02X".format(byte.toInt() and 0xFF))
                }
            }
        }
        return "https://app.plex.tv/auth#?clientID=${enc(clientIdentifier)}&code=${enc(code)}" +
            "&context%5Bdevice%5D%5Bproduct%5D=${enc(product)}" +
            "&context%5Bdevice%5D%5BdeviceName%5D=${enc(device)}"
    }

    /**
     * The URL that plays [partKey].
     *
     * [partKey] is whatever Plex reported - server-relative, e.g. `/library/parts/12/34/file.mkv`.
     * It is appended to [serverBase] verbatim rather than rebuilt, so a server that changes its
     * path shape does not need a code change here.
     */
    fun play(
        serverBase: String,
        partKey: String,
        token: String,
        clientIdentifier: String,
        hls: Boolean = true,
    ): String {
        val base = serverBase.trimEnd('/')
        val path = if (partKey.startsWith("/")) partKey else "/$partKey"
        val params = buildList {
            add("$TOKEN_PARAM=$token")
            if (hls) {
                add("protocol=hls")
                // Tells the server this is a session it should transcode/remux for, rather than a
                // file download. Plex uses it to decide whether to start a transcode at all.
                add("session=$clientIdentifier")
                add("X-Plex-Client-Identifier=$clientIdentifier")
            }
        }.joinToString("&")
        return "$base$path?$params"
    }

    /**
     * An absolute URL for a library image.
     *
     * Plex reports artwork as a server-relative path, and it will not serve it without the token -
     * so a bare path renders as a broken poster. Resolving against the server here means the stored
     * value is complete and the image loader needs no Plex-specific knowledge.
     */
    fun image(serverBase: String, path: String?, token: String, width: Int? = null): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) {
            // Already absolute - some libraries hand back full URLs for remote artwork. Only the
            // token is added, and only if the caller did not already include one.
            val sep = if ('?' in path) "&" else "?"
            return if (path.contains(TOKEN_PARAM)) path else "$path$sep$TOKEN_PARAM=$token"
        }
        val base = serverBase.trimEnd('/')
        val suffix = if (width != null) "/$width" else ""
        val p = if (path.startsWith("/")) path else "/$path"
        return "$base$p$suffix?$TOKEN_PARAM=$token"
    }

    /**
     * Replaces the token in [url] with a placeholder, for anything about to be logged.
     *
     * Two passes, because either alone leaks. When [token] is known it is scrubbed as a literal
     * everywhere it appears, which is the only way to catch a token that has ended up somewhere
     * other than a named parameter - a path segment, say, or a query value under a name we did not
     * expect. Then the parameter *name* is scrubbed too, which covers the case where the token we
     * hold is not the one in the url: a stale url from a previous sign-in, or a differently-shaped
     * server.
     *
     * The rest of the url is preserved deliberately. A redacted log that has also lost the part
     * path is useless for working out what went wrong, which is the only reason to redact at all.
     */
    fun redact(url: String, token: String? = null): String {
        var out = url
        if (!token.isNullOrBlank()) {
            out = out.replace(token, "***")
        }
        // `from` advances past every replacement. Without it this loop never terminates: the text
        // it writes is "***", which is immediately found again by the next search, and the url
        // never changes - a live infinite loop, which is how this was first found.
        var from = 0
        while (from < out.length) {
            val idx = out.indexOf(TOKEN_PARAM, from, ignoreCase = true)
            if (idx < 0) return out
            var start = idx + TOKEN_PARAM.length
            while (start < out.length && (out[start] == '=' || out[start] == '&')) start++
            var end = start
            while (end < out.length && out[end] != '&' && out[end] != '#' && out[end] != '/') end++
            // An empty value is not a leak; skip past it rather than spinning on it.
            if (end == start) {
                from = start + 1
                continue
            }
            out = out.substring(0, start) + "***" + out.substring(end)
            from = start + 3
        }
        return out
    }
}
