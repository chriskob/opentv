/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.player.resolveGrowingLocator
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test

/**
 * The growing-recording data source must never block on the database.
 *
 * Media3 calls `DataSource.open()` synchronously on its loader thread. The recording locator used
 * to fall back to a `runBlocking` Room lookup when the live registry had no entry, stalling the
 * open behind whatever the database was doing. The locator is now resolved before playback starts
 * (the navigation sites register it from the already-loaded row), so `open()` only consults the
 * in-process registry and fails fast when nothing is registered.
 */
class GrowingRecordingLocatorTest {

    @Test
    fun `a registered live path resolves without the database`() {
        assertThat(resolveGrowingLocator(7L, "/data/rec-7.ts")).isEqualTo("/data/rec-7.ts")
    }

    @Test(expected = IOException::class)
    fun `a missing registry entry fails fast instead of blocking`() {
        resolveGrowingLocator(7L, null)
    }

    @Test(expected = IOException::class)
    fun `a blank registry entry fails fast instead of blocking`() {
        resolveGrowingLocator(7L, "   ")
    }
}
