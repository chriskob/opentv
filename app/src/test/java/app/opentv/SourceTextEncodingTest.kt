/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The sources speak UTF-8, and only UTF-8.
 *
 * At some point a set of files was re-encoded by hand with the wrong decoder: the UTF-8 bytes of
 * real glyphs were read as Windows-1252 and saved again, so the source text itself holds sequences
 * like the three characters `a-circumflex Euro cent` where a bullet "•" was meant. Line comments
 * wear them invisibly, but some string literals reach the screen: the player's playlist header
 * ("My playlist • US GENERAL"), its "11:00 AM – 12:00 PM" range and "— 40 min" duration, and the
 * pairing screen's dashes and ellipses (the maintainer's photo, 2026-10-18 - an earlier release
 * fixed part of this family and missed these). The 2026-10-18 sweep rewrote every occurrence in
 * `src/main` through an explicit code-point map; this scan keeps the whole main source set clean,
 * so the next one is caught by a test instead of by a television.
 *
 * Sequences are built from code points here so this file itself stays plain ASCII.
 */
class SourceTextEncodingTest {

    private fun seq(vararg codePoints: Int): String =
        codePoints.map { it.toChar() }.joinToString("")

    /** Every mojibake sequence found in `src/main` on 2026-10-18, with the glyph it stands for. */
    private val mojibake: List<Pair<String, String>> = listOf(
        seq(0x00E2, 0x20AC, 0x201D) to "\u2014 (em dash)",
        seq(0x00E2, 0x20AC, 0x201C) to "\u2013 (en dash)",
        seq(0x00E2, 0x20AC, 0x00A2) to "\u2022 (bullet)",
        seq(0x00E2, 0x20AC, 0x00A6) to "\u2026 (ellipsis)",
        seq(0x00E2, 0x2020, 0x2019) to "\u2192 (right arrow)",
        seq(0x00E2, 0x2020, 0x00BA) to "\u21BA (restart arrow)",
        seq(0x00E2, 0x2020, 0x201D) to "\u2194 (left-right arrow)",
        seq(0x00E2, 0x2014, 0x20AC) to "\u25C0 (left triangle)",
        seq(0x00E2, 0x2013, 0x00B6) to "\u25B6 (right triangle)",
        seq(0x00E2, 0x2014, 0x008F) to "\u25CF (record dot)",
        seq(0x00C2, 0x00B7) to "\u00B7 (middle dot)",
        seq(0x00C2, 0x00B1) to "\u00B1 (plus-minus)",
        seq(0x00C3, 0x2014) to "\u00D7 (times)",
    )

    @Test
    fun theMainSourcesContain_noReEncodedGlyphs() {
        val offenders = mutableListOf<String>()
        File("src/main").walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml", "java") }
            .forEach { file ->
                val text = file.readText()
                mojibake.forEach { (bad, glyph) ->
                    if (text.contains(bad)) offenders += "${file.path} has $glyph"
                }
            }
        assertThat(offenders).isEmpty()
    }
}
