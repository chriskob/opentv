/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.recording.SmbClient
import app.opentv.recording.SmbConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmbOwnShareTest {

    private val nas = SmbConfig(
        host = "nas.local",
        share = "media",
        folder = "OpenTV",
        username = "u",
        password = "p",
    )

    @Test
    fun `accepts a locator on the configured host and share`() {
        assertThat(SmbClient.isOwnShare(nas, "smb://nas.local/media/OpenTV/a.ts")).isTrue()
    }

    @Test
    fun `rejects a locator that points at another host`() {
        assertThat(SmbClient.isOwnShare(nas, "smb://attacker.example/media/a.ts")).isFalse()
    }

    @Test
    fun `rejects a locator that points at another share on the same host`() {
        assertThat(SmbClient.isOwnShare(nas, "smb://nas.local/private/a.ts")).isFalse()
    }

    @Test
    fun `rejects path traversal`() {
        assertThat(SmbClient.isOwnShare(nas, "smb://nas.local/media/../secret.ts")).isFalse()
    }

    @Test
    fun `rejects non smb locators`() {
        assertThat(SmbClient.isOwnShare(nas, "file:///sdcard/a.ts")).isFalse()
    }
}
