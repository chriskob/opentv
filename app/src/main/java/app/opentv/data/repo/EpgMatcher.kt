/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import app.opentv.data.parser.ChannelNameNormalizer

/**
 * Joins provider channels to guide channels when nobody gave us a join key.
 *
 * The provider says `UK| BBC ONE FHD`. The guide says `BBC One` (id `bbc1.uk`). The only
 * bridge between them is the name, so both sides are pushed through
 * [ChannelNameNormalizer.groupKeyOf] and matched on the result.
 *
 * ## The design rule: prefer no match to a wrong match
 *
 * A missing guide entry reads as "provider doesn't do EPG" and can be fixed with a manual
 * override. A *wrong* one shows EastEnders against a sports channel, and the user reasonably
 * concludes the whole guide is broken. So the fuzzy tier only accepts a prefix match when it
 * is **unambiguous** — exactly one guide channel fits. `bbcone` will claim `BBC One` when
 * that is the only candidate, but if the guide carries `BBC One London` *and* `BBC One
 * Wales` and nothing plain, we refuse to guess between regions.
 */
object EpgMatcher {

    /** One guide channel, pre-normalised. */
    data class Alias(
        val epgId: String,
        /** Normalised display-name or id: `bbcone`, `bbc1uk`. */
        val key: String,
    )

    class Index internal constructor(
        private val exact: Map<String, String>,
        private val sortedKeys: List<Alias>,
        private val prefixes: Map<String, String?>,
    ) {
        /** Returns the epg id for a provider channel's group key, or null. */
        fun match(groupKey: String): String? {
            if (groupKey.length < MIN_KEY_LENGTH) return null

            exact[groupKey]?.let { return it }

            var found: String? = null
            if (prefixes.containsKey(groupKey)) {
                found = prefixes[groupKey]
                if (found == null) return null
            }

            var low = 0
            var high = sortedKeys.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (sortedKeys[mid].key < groupKey) low = mid + 1 else high = mid
            }
            for (i in low until sortedKeys.size) {
                val alias = sortedKeys[i]
                if (!alias.key.startsWith(groupKey)) break
                if (found == null) {
                    found = alias.epgId
                } else if (found != alias.epgId) {
                    return null
                }
            }
            if (found != null) return found

            // The other direction. Providers ship one guide channel and several live feeds for
            // it, which is why a playlist carries both `BRAVO` and `BRAVO (WEST)` while the guide
            // only has the first. Those variants are the guide's name PLUS a qualifier, so their
            // key is the guide key with a tail — and every lookup above searched the opposite way,
            // aliases that START WITH the channel's key. Without this the whole regional-variant
            // family reads "No guide information" however much guide data the provider sent.
            //
            // Done as a stripped stem, not a loose prefix. An arbitrary prefix would map
            // `foxnews` onto a `fox` guide, attaching the wrong channel's schedule — worse than
            // showing none, because it looks authoritative. Only known qualifiers are removed,
            // and only when what remains still matches a guide exactly.
            for (stem in variantStems(groupKey)) {
                exact[stem]?.let { return it }
            }
            return null
        }
    }

    /**
     * Candidate stems for a key that is a guide channel plus a feed qualifier, longest qualifier
     * removed first: `bravowesthd` → `bravowest` → `bravo`.
     */
    private fun variantStems(groupKey: String): List<String> {
        val stems = ArrayList<String>(2)
        var rest = groupKey
        // At most two: feeds are labelled at most that often (`bravo west hd`).
        repeat(2) {
            val cut = FEED_QUALIFIERS.firstOrNull { rest.length > it.length + MIN_KEY_LENGTH && rest.endsWith(it) }
                ?: return stems
            rest = rest.dropLast(cut.length)
            stems += rest
        }
        return stems
    }

    /**
     * Builds the lookup from every `<channel>` element seen across every enabled guide.
     *
     * Both the display-name (`BBC One`) and the raw id (`bbc1.uk`) are indexed — providers
     * that *do* fill in `epg_channel_id` often use the id form, and it costs nothing to
     * accept either.
     */
    fun buildIndex(aliases: Iterable<Pair<String, String>>): Index {
        val exact = HashMap<String, String>()
        val ambiguous = HashSet<String>()
        val prefixes = HashMap<String, String?>()
        val all = ArrayList<Alias>()

        for ((epgId, name) in aliases) {
            for (candidate in listOf(name, epgId)) {
                val key = ChannelNameNormalizer.normalize(candidate).groupKey
                if (key.length < MIN_KEY_LENGTH) continue
                all += Alias(epgId, key)
                val existing = exact.putIfAbsent(key, epgId)
                if (existing != null && existing != epgId) {
                    ambiguous += key
                }
                for (end in MIN_KEY_LENGTH..key.length) {
                    val prefix = key.take(end)
                    if (!prefixes.containsKey(prefix)) {
                        prefixes[prefix] = epgId
                    } else if (prefixes[prefix] != epgId) {
                        prefixes[prefix] = null
                    }
                }
            }
        }
        ambiguous.forEach(exact::remove)
        all.sortWith(compareBy<Alias> { it.key }.thenBy { it.epgId })

        return Index(exact, all, prefixes)
    }

    /**
     * Below this, keys are too generic to mean anything — `e4` is real, but `tv`, `hd` and
     * single letters match everything. 2 keeps `e4`/`5usa`-style names alive.
     */
    private const val MIN_KEY_LENGTH = 2

    /**
     * Tails a provider appends to a feed name when it ships several feeds for one guide channel:
     * `BRAVO (WEST)`, `BUZZR PLUS`, `E4 HD`. Longest first, so `…WESTHD` strips `hd` then
     * `west`. Deliberately only region/quality/feed words — never a word that could be part of a
     * channel's actual name.
     */
    private val FEED_QUALIFIERS = listOf(
        "west", "east", "north", "south", "central", "midwest", "national",
        "plus", "family", "extra", "hd", "sd", "uhd", "4k", "raw", "feed", "backup", "alt",
    ).sortedByDescending { it.length }
}
