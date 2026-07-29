/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.parser

/**
 * Turns the channel names IPTV providers actually ship into something usable.
 *
 * A real provider list contains entries like:
 *
 *     UK| BBC ONE FHD
 *     UK - BBC One HD
 *     [UK] BBC 1 ᵁᴴᴰ
 *     ### PRIME RAW 60fps ####
 *
 * while an XMLTV guide calls the same channel `BBC One`. Nothing in either format joins
 * them. This normaliser produces, for any name:
 *
 *  - a **base name** with country prefixes, quality tags and decorations stripped
 *  - a **group key** (lowercased alphanumerics of the base) used to fold quality variants
 *    of one channel into a single row, and to match against guide data
 *  - a **quality rank and label**, so the app can pick the best variant by default and
 *    offer the rest as a switch during playback
 *
 * This one object is the foundation of both headline features: EPG matching and quality
 * grouping. It is pure and heavily tested — when a channel matches the wrong guide entry,
 * the fix almost certainly starts here, in a test case named after the offending channel.
 */
object ChannelNameNormalizer {

    data class Normalized(
        /** Human-readable name with the junk removed: `BBC One`. */
        val baseName: String,
        /** Join/group key: lowercase alphanumerics only: `bbcone`. */
        val groupKey: String,
        /** Higher is better. 0 when the name carries no quality information. */
        val qualityRank: Int,
        /** What to show on the quality switch: `FHD`, `HD`, `RAW 60fps`, or `""`. */
        val qualityLabel: String,
        /** Country hint from a stripped prefix (`UK`, `US`…), or null. */
        val region: String?,
    )

    /** Quality/codec tokens, matched as whole words, case-insensitively. */
    private val QUALITY_RANKS = mapOf(
        "8K" to 500,
        "4K" to 400, "UHD" to 400, "FUHD" to 400, "2160P" to 400,
        "FHD" to 300, "1080" to 300, "1080P" to 300, "FULLHD" to 300,
        "HD" to 200, "720P" to 200, "720" to 200,
        "SD" to 100, "LQ" to 100, "480P" to 100, "576P" to 100,
    )

    /** Tokens that describe the stream but not its resolution. Stripped, kept in the label. */
    private val EXTRA_TOKENS = setOf(
        "RAW", "HEVC", "H265", "H.265", "H264", "H.264", "AV1",
        "50FPS", "60FPS", "25FPS", "30FPS", "FPS",
        "HDR", "DOLBY", "VISION", "PLUS",
    )

    /** Superscript/decorated variants some panels use, folded to ASCII before tokenising. */
    private val SUPERSCRIPT_MAP = mapOf(
        'ᴴ' to 'H', 'ᴰ' to 'D', 'ᵁ' to 'U', 'ᶠ' to 'F', 'ᴷ' to 'K',
        'ᔆ' to 'S', 'ˢ' to 'S', '⁴' to '4', '⁸' to '8',
    )

    /**
     * Leading country tag: `UK|`, `UK -`, `UK:`, `[UK]`, `(US)`, `USA |` and so on.
     * Two or three capitals only — long enough for ISO codes and `USA`, short enough that
     * `BEIN| SPORTS` is left alone.
     *
     * Two alternatives on purpose. Brackets are themselves the delimiter, so `[UK] ITV`
     * needs no separator after them; a bare code **must** have one, or `BBC ONE` would
     * lose its BBC.
     */
    private val COUNTRY_PREFIX = Regex(
        """^\s*(?:[\[(]([A-Z]{2,3})[\])]\s*[|:\-–—]?|([A-Z]{2,3})\s*[|:\-–—])\s*"""
    )

    /** Decorations providers use as separators or eye-catchers. */
    private val DECORATION = Regex("""[#*•●▪►▶✦★☆~_=]+""")

    private val MULTI_SPACE = Regex("""\s+""")

    fun normalize(raw: String): Normalized {
        var working = raw.trim()

        // Fold superscript decorations (ᴴᴰ → HD) so the token pass can see them.
        if (working.any { it in SUPERSCRIPT_MAP }) {
            working = buildString(working.length) {
                for (c in working) append(SUPERSCRIPT_MAP[c] ?: c)
            }
        }

        working = DECORATION.replace(working, " ")

        var region: String? = null
        COUNTRY_PREFIX.find(working)?.let { match ->
            region = match.groupValues[1].ifEmpty { match.groupValues[2] }
            working = working.removeRange(match.range)
        }

        // Tokenise and pull out quality/extra markers wherever they appear.
        var bestRank = 0
        val labelParts = ArrayList<String>(2)
        val keptTokens = ArrayList<String>()

        for (token in working.split(' ', '\t', '/').filter { it.isNotBlank() }) {
            val bare = token.trim('.', ',', ':', ';', '|', '-', '(', ')', '[', ']')
            if (bare.isEmpty()) continue
            val upper = bare.uppercase()

            val rank = QUALITY_RANKS[upper]
            when {
                rank != null -> {
                    if (rank > bestRank) bestRank = rank
                    labelParts += upper
                }
                upper in EXTRA_TOKENS -> labelParts += bare
                else -> keptTokens += bare
            }
        }

        val base = MULTI_SPACE.replace(keptTokens.joinToString(" "), " ").trim()
        val key = groupKeyOf(base)

        return Normalized(
            baseName = base.ifEmpty { raw.trim() },
            groupKey = key.ifEmpty { groupKeyOf(raw) },
            qualityRank = bestRank,
            qualityLabel = labelParts.joinToString(" "),
            region = region,
        )
    }

    /**
     * The canonical join key: lowercase letters and digits only.
     *
     * Aggressive on purpose. `BBC One`, `BBC1`, `bbc-one` and `B B C One` should all meet in
     * the middle — false negatives here mean channels with no guide, which is the failure
     * users actually notice. The word-number folding handles `BBC ONE` vs `BBC 1`.
     */
    fun groupKeyOf(name: String): String {
        // Split CamelCase first so `BbcOne` sees the same words as `BBC One`, then fold
        // number WORDS to digits — whole words only, never substrings, or `money` becomes
        // `m1y` on one side of a match and not the other.
        val spaced = CAMEL_BOUNDARY.replace(name, "$1 $2")
        return spaced.lowercase()
            .split(NON_ALNUM)
            .filter { it.isNotEmpty() }
            .joinToString("") { word -> NUMBER_WORDS[word] ?: word }
    }

    private val CAMEL_BOUNDARY = Regex("""([a-z])([A-Z])""")
    private val NON_ALNUM = Regex("""[^a-zA-Z0-9]+""")
    private val NUMBER_WORDS = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
    )
}
