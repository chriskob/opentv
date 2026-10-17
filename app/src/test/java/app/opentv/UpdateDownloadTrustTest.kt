/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.update.UpdateChecker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UpdateDownloadTrustTest {

    @Test
    fun `accepts a GitHub release asset over https`() {
        assertThat(
            UpdateChecker.isTrustedDownload(
                "https://github.com/chriskob/opentv/releases/download/v1/opentv.apk",
            ),
        ).isTrue()
    }

    @Test
    fun `accepts GitHub's CDN host over https`() {
        assertThat(
            UpdateChecker.isTrustedDownload("https://objects.githubusercontent.com/x/opentv.apk"),
        ).isTrue()
    }

    @Test
    fun `rejects plain http`() {
        assertThat(UpdateChecker.isTrustedDownload("http://github.com/x/opentv.apk")).isFalse()
    }

    @Test
    fun `rejects another host`() {
        assertThat(UpdateChecker.isTrustedDownload("https://evil.example/opentv.apk")).isFalse()
    }

    @Test
    fun `rejects a lookalike suffix`() {
        assertThat(UpdateChecker.isTrustedDownload("https://github.com.evil.example/x.apk")).isFalse()
    }
}
