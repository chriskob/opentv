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
    fun `an ordinary title comes back unchanged`() {
        assertThat(prog("Below Deck Mediterranean", isNew = true).guideDisplayTitle())
            .isEqualTo("Below Deck Mediterranean")
    }
}