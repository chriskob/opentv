/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.repo.EpgMatcher
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Matching a provider's live channels to the guide channels that describe them.
 *
 * The regression this covers: a playlist carrying both `BRAVO` and `BRAVO (WEST)` while the
 * guide only carries the first. Every lookup searched one direction — guide names that START WITH
 * the channel's key — so the variant, whose key is the guide's key with a tail, matched nothing
 * and showed "No guide information" however much guide data the provider had sent.
 */
class EpgMatcherVariantTest {

    private fun index(vararg aliases: Pair<String, String>) =
        EpgMatcher.buildIndex(aliases.toList())

    @Test
    fun `a regional variant inherits the guide channel it is named after`() {
        val idx = index("bravo.us" to "BRAVO")
        assertThat(idx.match("bravo")).isEqualTo("bravo.us")
        // The variant: guide key is a strict prefix of the channel key.
        assertThat(idx.match("bravowest")).isEqualTo("bravo.us")
    }

    @Test
    fun `two qualifiers are stripped in turn`() {
        val idx = index("buzzr.us" to "BUZZR")
        assertThat(idx.match("buzzrwest")).isEqualTo("buzzr.us")
        assertThat(idx.match("buzzrwesthd")).isEqualTo("buzzr.us")
    }

    /**
     * The reason this is a stripped stem and not a prefix search. Matching `foxnews` onto a `fox`
     * guide would attach the wrong channel's entire schedule — and it would look authoritative,
     * which is worse than showing no guide at all.
     */
    @Test
    fun `an unrelated shorter guide is never borrowed`() {
        val idx = index("fox.us" to "FOX", "foxnews.us" to "FOX NEWS")
        assertThat(idx.match("foxnews")).isEqualTo("foxnews.us")
        // No FOX NEWS guide? Then nothing, rather than FOX's schedule.
        val noNews = index("fox.us" to "FOX")
        assertThat(noNews.match("foxnews")).isNull()
    }

    @Test
    fun `a real match is never displaced by a variant stem`() {
        val idx = index("bravo.us" to "BRAVO", "bravowest.us" to "BRAVO WEST")
        // Both exist: each must get its own guide, not the other's.
        assertThat(idx.match("bravowest")).isEqualTo("bravowest.us")
        assertThat(idx.match("bravo")).isEqualTo("bravo.us")
    }

    @Test
    fun `stripping never eats the whole name`() {
        val idx = index("bravo.us" to "BRAVO")
        // A channel whose entire name is a qualifier must not have it stripped to an empty stem
        // and then be handed some unrelated guide.
        assertThat(idx.match("west")).isNull()
        // A single qualifier off a real name still resolves.
        assertThat(idx.match("bravohd")).isEqualTo("bravo.us")
    }

    @Test
    fun `the original direction still works`() {
        // Guide named with a suffix the channel lacks — the case that already worked.
        val idx = index("cnnintl.us" to "CNN INTERNATIONAL")
        assertThat(idx.match("cnn")).isEqualTo("cnnintl.us")
    }
}
