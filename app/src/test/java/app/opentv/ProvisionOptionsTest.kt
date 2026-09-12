/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.pairing.ProvisionOptions
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * How a portal's content boxes become the flags the app stores.
 *
 * The bug this pins down: a portal may describe what to load by listing only the ticked boxes, so a
 * user who unticked Channels produced a payload with no `channels` key at all. Reading that absence
 * as the default (`true`) imported the channel list they had just excluded — the app had no way to
 * tell "the portal says yes" from "the portal did not say". These cases fix the meaning of each.
 */
class ProvisionOptionsTest {

    @Test
    fun `movies and shows ticked with channels unticked imports no channels`() {
        // The reported bug, exactly: the portal named two sections, so the unnamed one is off.
        val resolved = ProvisionOptions.resolve(live = null, vod = true, series = true)

        assertThat(resolved.includeLive).isFalse()
        assertThat(resolved.includeVod).isTrue()
        assertThat(resolved.includeSeries).isTrue()
    }

    @Test
    fun `a portal that states nothing keeps the historical defaults`() {
        // Legacy portals, and playlists added by hand: channels on, libraries off.
        val resolved = ProvisionOptions.resolve(live = null, vod = null, series = null)

        assertThat(resolved.includeLive).isTrue()
        assertThat(resolved.includeVod).isFalse()
        assertThat(resolved.includeSeries).isFalse()
    }

    @Test
    fun `an explicit no is honoured even when the other sections are named`() {
        val resolved = ProvisionOptions.resolve(live = false, vod = true, series = false)

        assertThat(resolved.includeLive).isFalse()
        assertThat(resolved.includeVod).isTrue()
        assertThat(resolved.includeSeries).isFalse()
    }

    @Test
    fun `channels ticked alone does not switch the libraries on`() {
        val resolved = ProvisionOptions.resolve(live = true, vod = null, series = null)

        assertThat(resolved.includeLive).isTrue()
        assertThat(resolved.includeVod).isFalse()
        assertThat(resolved.includeSeries).isFalse()
    }

    @Test
    fun `naming only a library leaves the other library off`() {
        // A portal that lists movies but not shows must not import shows by default either.
        val resolved = ProvisionOptions.resolve(live = null, vod = true, series = null)

        assertThat(resolved.includeLive).isFalse()
        assertThat(resolved.includeVod).isTrue()
        assertThat(resolved.includeSeries).isFalse()
    }

    @Test
    fun `all three declined is respected, not read as silence`() {
        val resolved = ProvisionOptions.resolve(live = false, vod = false, series = false)

        assertThat(resolved.includeLive).isFalse()
        assertThat(resolved.includeVod).isFalse()
        assertThat(resolved.includeSeries).isFalse()
    }
}
