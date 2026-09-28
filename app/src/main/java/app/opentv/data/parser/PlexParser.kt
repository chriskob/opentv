/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.data.parser

import android.util.Xml
import app.opentv.data.model.PlexMediaPart
import app.opentv.data.model.PlexRecentItem
import app.opentv.data.model.PlexSection
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Reads Plex's XML responses.
 *
 * Plex speaks XML, not JSON, and answers in a handful of shapes: a `MediaContainer` of `Directory`
 * elements for sections, of `Video`/`Directory` elements for recently-added, a `Video` with nested
 * `Media`/`Part` for what to actually play, and a bare `<pin>` or `<user>` for the login
 * handshake. They are parsed separately because they genuinely are separate documents, but they
 * share one rule: **an attribute that is missing, empty, or the wrong shape yields a null field,
 * never an exception.**
 *
 * That rule is not defensive decoration. A library section the account cannot see, an item with
 * no artwork, a server that omits `duration` on one part - all of these arrive as ordinary
 * responses, and a parser that threw on them would take out the whole shelf rather than the one
 * row. Everything here coerces.
 */
object PlexParser {

    /**
     * `/library/sections` - the account's libraries.
     *
     * Only `movie` and `show` types are kept: `artist`, `photo` and `playlists` have no meaning for
     * OpenTV, and surfacing them would offer a section that cannot be listed.
     */
    fun sections(input: InputStream): List<PlexSection> = parse(input) { parser, sink ->
        when (parser.name) {
            "Directory" -> {
                val type = parser.attr("type").orEmpty()
                val key = parser.attr("key")
                val title = parser.attr("title") ?: parser.attr("titleSort")
                if (key != null && title != null && (type.equals("movie", true) || type.equals("show", true))) {
                    sink += PlexSection(key = key, title = title, type = type.lowercase())
                }
            }
        }
    }

    /**
     * `/library/sections/{key}/recentlyAdded` - newest first, already ordered by Plex.
     *
     * `addedAt` is Plex's own timestamp for when the item joined the library, which is what
     * "recently added" means. It is preferred over `viewedAt`, which is when *you* watched it -
     * conflating the two is how a long-finished film ends up at the top of a new-additions shelf.
     */
    fun recentlyAdded(input: InputStream): List<PlexRecentItem> = parse(input) { parser, sink ->
        when (parser.name) {
            "Video", "Directory" -> {
                val ratingKey = parser.attr("ratingKey")
                if (ratingKey != null) {
                    sink += PlexRecentItem(
                        ratingKey = ratingKey,
                        title = parser.attr("title") ?: parser.attr("titleSort") ?: "Untitled",
                        type = parser.attr("type") ?: if (parser.name == "Video") "movie" else "show",
                        year = parser.attr("year")?.toIntOrNull(),
                        summary = parser.attr("summary"),
                        durationMillis = parser.attr("duration")?.toLongOrNull()?.times(1000L),
                        librarySectionId = parser.attr("librarySectionID"),
                        addedAtEpochSeconds = parser.attr("addedAt")?.toLongOrNull(),
                        thumbPath = parser.attr("thumb"),
                        artPath = parser.attr("art"),
                        parentRatingKey = parser.attr("parentRatingKey"),
                        viewOffsetMillis = parser.attr("viewOffset")?.toLongOrNull()?.times(1000L),
                        viewCount = parser.attr("viewCount")?.toIntOrNull(),
                    )
                }
            }
        }
    }

    /**
     * `/library/metadata/{ratingKey}` - the parts behind one item.
     *
     * A `Part` is one file: an MKV, or one of several files making up a DVD set. `Media` groups
     * parts that are alternatives of the same rendition. Everything is collected rather than
     * stopping at the first, so a multi-file item still resolves. Null when the item has no
     * playable part at all, which is what an empty library entry looks like.
     */
    fun metadata(input: InputStream): List<PlexMediaPart> = parse(input) { parser, sink ->
        if (parser.name != "Part") return@parse
        val key = parser.attr("key") ?: return@parse
        sink += PlexMediaPart(
            key = key,
            container = parser.attr("container"),
            fileSizeBytes = parser.attr("size")?.toLongOrNull(),
            durationMillis = parser.attr("duration")?.toLongOrNull()?.times(1000L),
            videoCodec = parser.attr("videoCodec"),
            audioCodec = parser.attr("audioCodec"),
        )
    }

    /** `/api/v2/pins` - the four-character code shown to the viewer, and the id to poll. */
    data class Pin(val id: Long, val code: String)

    fun pin(input: InputStream): Pin? {
        val attrs = firstElement(input, "pin") ?: return null
        val id = attrs["id"]?.toLongOrNull()
        val code = attrs["code"]
        return if (id != null && code != null) Pin(id, code) else null
    }

    /**
     * The account's token, wherever Plex chooses to put it in the sign-in response.
     *
     * This deliberately does NOT look only at a `<user>` element, and that is not defensiveness -
     * it is the fix. The sign-in poll starts life as a `<pin>` document carrying no token, and on
     * the poll where the viewer's approval landed the response *grew*: 654 bytes to 672, measured
     * on a real device. A parser that only inspected `<user ... authToken>` reported "no token"
     * through that growth and on every poll after it, so the television sat on "Waiting for Plex"
     * while the browser had plainly said the sign-in succeeded.
     *
     * So: walk the whole document and take the first `authToken` attribute found, on any element.
     * A poll that is genuinely still pending has no such attribute anywhere, so "keep waiting"
     * stays honest.
     */
    fun authToken(input: InputStream): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                for (i in 0 until parser.attributeCount) {
                    if (parser.getAttributeName(i) == AUTH_TOKEN_ATTR) {
                        parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
            }
            event = try {
                parser.next()
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

    private const val AUTH_TOKEN_ATTR = "authToken"

    /** The account's own name and username, once a token is known good. */
    data class Account(val username: String?, val title: String?)

    fun account(input: InputStream): Account? {
        val attrs = firstElement(input, "user") ?: return null
        return Account(username = attrs["username"], title = attrs["title"])
    }

    // ---- plumbing -------------------------------------------------------------------------------

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)?.takeIf {
        it.isNotBlank()
    }

    /**
     * The first `<[name] .../>` element's attributes, or null if the document has none.
     *
     * Used for the login handshake, whose documents are a single element wrapped in a
     * `MediaContainer` (or bare). Blank values are dropped so a caller can treat "" and absent
     * identically - Plex does emit `title=""` for legitimately untitled things.
     */
    private fun firstElement(input: InputStream, name: String): Map<String, String>? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == name) {
                val out = mutableMapOf<String, String>()
                for (i in 0 until parser.attributeCount) {
                    val key = parser.getAttributeName(i)
                    parser.getAttributeValue(i)?.takeIf { it.isNotBlank() }?.let { out[key] = it }
                }
                return out
            }
            event = try {
                parser.next()
            } catch (e: Exception) {
                // Truncated document: whatever came before the break is what we have, and if the
                // element we wanted had not appeared yet then there is genuinely nothing to report.
                return null
            }
        }
        return null
    }

    /**
     * Walks the document, handing [onTag] every start tag, and collecting whatever it emits.
     *
     * [onTag] receives a mutable list rather than returning values, because a pull parser cannot
     * suspend mid-document - and these documents are small enough (a section's recently-added page
     * is a few hundred kilobytes at worst) that streaming is not worth the complexity. A malformed
     * tail costs the tail, not the caller's connection.
     */
    private inline fun <T> parse(input: InputStream, onTag: (XmlPullParser, MutableList<T>) -> Unit): List<T> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val sink = mutableListOf<T>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                onTag(parser, sink)
            }
            event = try {
                parser.next()
            } catch (e: Exception) {
                // A truncated or malformed document still yielded everything before the break.
                break
            }
        }
        return sink
    }
}
