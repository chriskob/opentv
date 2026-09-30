/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.model.Programme
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The guide cell draws a NEW/LIVE chip, so a title that also ends in that word is saying it
 * twice. [Programme.guideDisplayTitle] peels the redundant trailing token off — and only that.
 */
class GuideDisplayTitleTest {

    private fun prog(
        title: String,
        isNew: Boolean = false,
        isLive: Boolean = false,
        category: String? = null,
    ) = Programme(
        feedId = 1L,
        epgChannelId = "test-channel",
        startUtcMillis = 1_700_000_000_000L,
        endUtcMillis = 1_700_001_800_000L,
        title = title,
        category = category,
        isNew = isNew,
        isLive = isLive,
    )

    @Test
    fun `trailing New is dropped when the cell shows a NEW chip`() {
        assertThat(prog("CBS News Chicago 5:00pm New", isNew = true).guideDisplayTitle())
            .isEqualTo("CBS News Chicago 5:00pm")
    }

    @Test
    fun `trailing Live is dropped when the cell shows a LIVE chip`() {
        assertThat(prog("WGN Evening News Live", isLive = true).guideDisplayTitle())
            .isEqualTo("WGN Evening News")
    }

    @Test
    fun `New inferred from the category is still redundant against the chip`() {
        assertThat(prog("NBC 5 News at 5PM New", category = "New").guideDisplayTitle())
            .isEqualTo("NBC 5 News at 5PM")
    }

    @Test
    fun `matching is case insensitive and trailing whitespace is ignored`() {
        assertThat(prog("  Evening News   LIVE  ", isLive = true).guideDisplayTitle())
            .isEqualTo("Evening News")
    }

    @Test
    fun `wrapped status tokens are peeled too`() {
        assertThat(prog("Show (New)", isNew = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show - Live.", isLive = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show: Live", isLive = true).guideDisplayTitle()).isEqualTo("Show")
    }

    @Test
    fun `both tokens go when both chips show`() {
        assertThat(prog("Evening Wrap New", isNew = true, isLive = true).guideDisplayTitle())
            .isEqualTo("Evening Wrap")
    }

    @Test
    fun `a title ending in the word is kept when no matching chip is shown`() {
        // Not flagged live, so "Live" is part of the title, not a status we are repeating.
        assertThat(prog("WGN Evening News Live").guideDisplayTitle())
            .isEqualTo("WGN Evening News Live")
    }

    @Test
    fun `a title that is only the status word keeps its name`() {
        assertThat(prog("New", isNew = true).guideDisplayTitle()).isEqualTo("New")
        assertThat(prog("Live", isLive = true).guideDisplayTitle()).isEqualTo("Live")
    }

    @Test
    fun `interior status words are untouched`() {
        assertThat(prog("Live PD", isNew = true, isLive = true).guideDisplayTitle())
            .isEqualTo("Live PD")
        assertThat(prog("New Amsterdam", isNew = true).guideDisplayTitle())
            .isEqualTo("New Amsterdam")
    }

    @Test
    fun `leading status tags are still stripped`() {
        assertThat(prog("[New] The Office", isNew = true).guideDisplayTitle())
            .isEqualTo("The Office")
    }

    @Test
    fun `a newline before the status counts as a boundary`() {
        // XMLTV keeps interior newlines and trim() only takes them off the ends, so this is the
        // shape that actually ships. A space-only boundary search misses it entirely.
        assertThat(prog("NBC 5 News at 5PM\nNew", isNew = true).guideDisplayTitle())
            .isEqualTo("NBC 5 News at 5PM")
        assertThat(prog("WGN Evening News\r\n Live ", isLive = true).guideDisplayTitle())
            .isEqualTo("WGN Evening News")
    }

    @Test
    fun `the token is matched on its letters so decoration does not matter`() {
        // Every wrapper a provider might use reduces to the same bare word.
        assertThat(prog("Show (New)", isNew = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show - Live.", isLive = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show: Live", isLive = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show \"New\"", isNew = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show [LIVE]", isLive = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show *New*", isNew = true).guideDisplayTitle()).isEqualTo("Show")
        assertThat(prog("Show …Live…", isLive = true).guideDisplayTitle()).isEqualTo("Show")
    }

    @Test
    fun `a token that only looks like a status is left alone`() {
        // "Newcastle" and "Lives" are not statuses: stripping them would rename real programmes.
        assertThat(prog("Newcastle United Report", isNew = true).guideDisplayTitle())
            .isEqualTo("Newcastle United Report")
        assertThat(prog("Two Lives", isLive = true).guideDisplayTitle()).isEqualTo("Two Lives")
    }

    @Test
    fun `superscript status words are peeled`() {
        // Verbatim from the ONN guide, codepoints included: providers spell these statuses in
        // Latin modifier letters, not ASCII. ᴺᵉʷ is U+1D3A U+1D49 U+02B7 and ᴸᶦᵛᵉ is
        // U+1D38 U+1DA6 U+1D5B U+1D49 - three earlier fixes passed on ASCII and failed here.
        assertThat(prog("CBS News Chicago 4:00pm ᴺᵉʷ", isNew = true).guideDisplayTitle())
            .isEqualTo("CBS News Chicago 4:00pm")
        assertThat(prog("Hannity ᴸᶦᵛᵉ", isLive = true).guideDisplayTitle())
            .isEqualTo("Hannity")
        assertThat(prog("Gutfeld! ᴺᵉʷ", isNew = true).guideDisplayTitle())
            .isEqualTo("Gutfeld!")
        assertThat(prog("WGN Evening News ᴸᶦᵛᵉ", isLive = true).guideDisplayTitle())
            .isEqualTo("WGN Evening News")
    }

    @Test
    fun `superscript status words are kept when no matching chip shows`() {
        assertThat(prog("Hannity ᴸᶦᵛᵉ").guideDisplayTitle()).isEqualTo("Hannity ᴸᶦᵛᵉ")
    }

    @Test
    fun `punctuation that belongs to the title survives`() {
        // Regression: shaving the separator too eagerly turned "Gutfeld!" into "Gutfeld".
        assertThat(prog("Gutfeld! ᴺᵉʷ", isNew = true).guideDisplayTitle()).isEqualTo("Gutfeld!")
        assertThat(prog("The Late Show. ᴸᶦᵛᵉ", isLive = true).guideDisplayTitle())
            .isEqualTo("The Late Show.")
        assertThat(prog("Who Do You Think You Are? ᴺᵉʷ", isNew = true).guideDisplayTitle())
            .isEqualTo("Who Do You Think You Are?")
    }

    @Test
    fun `real accents in a title are left intact`() {
        // Folding is for comparing the status token, not for rewriting the title.
        assertThat(prog("Café Society ᴺᵉʷ", isNew = true).guideDisplayTitle())
            .isEqualTo("Café Society")
        assertThat(prog("Ångström ᴸᶦᵛᵉ", isLive = true).guideDisplayTitle())
            .isEqualTo("Ångström")
    }

    @Test
    fun `a status on the first line of a two-line title is peeled`() {
        // The real shape, verbatim from the ONN: this provider puts the episode on line two and
        // hangs the status off the end of the show name, so peeling only the end of the whole
        // string - which is the episode - matched nothing.
        assertThat(prog("Wheel of Fortune ᴺᵉʷ\nCanyon Spirit", isNew = true).guideDisplayTitle())
            .isEqualTo("Wheel of Fortune\nCanyon Spirit")
        assertThat(prog("Survivor ᴺᵉʷ\nWeaponized Honesty", isNew = true).guideDisplayTitle())
            .isEqualTo("Survivor\nWeaponized Honesty")
        assertThat(prog("MLB Baseball ᴸᶦᵛᵉ\nWild Card Series", isLive = true).guideDisplayTitle())
            .isEqualTo("MLB Baseball\nWild Card Series")
    }

    @Test
    fun `the episode line is left alone`() {
        val shown = prog("Wheel of Fortune ᴺᵉʷ\nCanyon Spirit", isNew = true).guideDisplayTitle()
        assertThat(shown).contains("Canyon Spirit")
        assertThat(shown.endsWith("Canyon Spirit")).isTrue()
    }

    @Test
    fun `an ordinary title comes back unchanged`() {
        assertThat(prog("Below Deck Mediterranean", isNew = true).guideDisplayTitle())
            .isEqualTo("Below Deck Mediterranean")
    }
}