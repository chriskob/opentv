/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.torrent.PhoneAddPage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The page the phone sees after scanning the Torrents QR.
 *
 * The HTML is the part that cannot be checked by the compiler, and it has two jobs that are easy
 * to break silently: it must carry the token on every round trip, and it must never let text from
 * a torrent (a title, an engine message) become markup.
 */
class PhoneAddPageTest {

    @Test
    fun `form posts the link and the token back to the tv`() {
        val html = PhoneAddPage.form("abc123", error = null)

        assertThat(html).contains("method=\"post\"")
        assertThat(html).contains("action=\"/submit?t=abc123\"")
        assertThat(html).contains("name=\"t\" value=\"abc123\"")
        assertThat(html).contains("name=\"link\"")
    }

    @Test
    fun `engine messages cannot become markup`() {
        val html = PhoneAddPage.form("abc123", error = "<script>alert(1)</script>")

        assertThat(html).doesNotContain("<script>alert")
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")
    }

    @Test
    fun `torrent titles are escaped on the sent page`() {
        val html = PhoneAddPage.done("abc123", "<b>Big Buck Bunny</b>")

        assertThat(html).doesNotContain("<b>Big Buck Bunny</b>")
        assertThat(html).contains("&lt;b&gt;Big Buck Bunny&lt;/b&gt;")
        assertThat(html).contains("href=\"/?t=abc123\"")
    }

    @Test
    fun `untitled torrent still gets a readable confirmation`() {
        val html = PhoneAddPage.done("abc123", null)

        assertThat(html).contains("starting on the TV")
        assertThat(html).doesNotContain("%s")
    }

    @Test
    fun `page needs nothing from the network to render`() {
        val html = PhoneAddPage.form("abc123", error = null)

        // The phone may be on a Wi-Fi network with no route to the internet; every asset has to
        // be part of this document. A single <script>, <img> or @import would break that promise.
        assertThat(html).doesNotContain("<script")
        assertThat(html).doesNotContain("<img")
        assertThat(html).doesNotContain("src=\"http")
        assertThat(html).doesNotContain("@import")
    }

    @Test
    fun `the form carries the shortcut address when the server has one`() {
        val html = PhoneAddPage.form(
            token = "abc123",
            error = null,
            shortcutUrl = "http://192.168.1.128:8767/add?t=abc123&link=",
        )

        assertThat(html).contains("<details")
        assertThat(html).contains("http://192.168.1.128:8767/add?t=abc123&amp;link=")
        assertThat(html).contains("Shortcuts")
    }

    @Test
    fun `without a shortcut address there is no shortcut section`() {
        val html = PhoneAddPage.form("abc123", error = null)

        assertThat(html).doesNotContain("<details")
    }

    @Test
    fun `the shortcut address cannot become markup`() {
        val html = PhoneAddPage.form(
            token = "abc123",
            error = null,
            shortcutUrl = "http://t:1/add?t=abc&link=<script>alert(1)</script>",
        )

        assertThat(html).doesNotContain("<script>alert")
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;")
    }

    @Test
    fun `error text follows the failure kind`() {
        assertThat(PhoneAddPage.errorFor(PhoneAddPage.Result.Added("A"))).isNull()
        assertThat(PhoneAddPage.errorFor(PhoneAddPage.Result.Refused("no peers"))).isEqualTo("no peers")
        assertThat(PhoneAddPage.errorFor(PhoneAddPage.Result.InvalidLink)).isNotEmpty()
        assertThat(PhoneAddPage.errorFor(PhoneAddPage.Result.EngineUnavailable)).isNotEmpty()
    }
}
