/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.PlaybackErrors
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The text a viewer reads when a stream will not play.
 *
 * These matter more than they look. A provider gateway timeout was previously reported as a bare
 * spinner that could persist for minutes, which is indistinguishable from a hung app — the viewer
 * cannot tell whether to wait, retry, or give up on the provider. Each case now has to name what
 * went wrong and, where it differs, say whose fault it is.
 */
class PlaybackErrorsTest {

    @Test
    fun `gateway timeout says it is the provider and not the device`() {
        val text = PlaybackErrors.describeHttpStatus(504)
        assertThat(text).contains("504")
        assertThat(text).contains("provider")
        // The single most useful thing this message can say: there is nothing to fix locally.
        assertThat(text).contains("not a fault on this device")
    }

    @Test
    fun `bad gateway and service unavailable are named separately`() {
        assertThat(PlaybackErrors.describeHttpStatus(502)).contains("upstream")
        assertThat(PlaybackErrors.describeHttpStatus(503)).contains("unavailable")
        assertThat(PlaybackErrors.describeHttpStatus(500)).contains("internal error")
        assertThat(PlaybackErrors.describeHttpStatus(408)).contains("too long")
    }

    @Test
    fun `connection limit and credentials keep pointing at the fix`() {
        // 403 is the one a single-connection account hits constantly, so it has to name the cause.
        assertThat(PlaybackErrors.describeHttpStatus(403)).contains("another device")
        assertThat(PlaybackErrors.describeHttpStatus(401)).contains("username and password")
        assertThat(PlaybackErrors.describeHttpStatus(429)).contains("rate-limiting")
        assertThat(PlaybackErrors.describeHttpStatus(404)).contains("Refresh the channel list")
    }

    @Test
    fun `an unrecognised status still says what it was`() {
        assertThat(PlaybackErrors.describeHttpStatus(418)).contains("418")
        assertThat(PlaybackErrors.describeHttpStatus(599)).contains("Nothing is wrong on this device")
    }
}
