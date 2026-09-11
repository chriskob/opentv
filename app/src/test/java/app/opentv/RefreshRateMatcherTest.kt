/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.core.RefreshRateMatcher
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The rule that decides which display mode a stream should be shown in.
 *
 * Written because the failure it prevents is invisible in code review and obvious on a television:
 * 25 fps IPTV on a 60 Hz box judders once a second, and the fix is to ask for 50 Hz — but only when
 * the rate divides exactly. A rule that picks the *nearest* mode instead hands 50 fps to a 60 Hz
 * display, which judders differently and blanks the screen while it switches. The cases below are the
 * broadcast rates this app actually meets, plus the pairings that must be refused.
 */
class RefreshRateMatcherTest {

    private fun mode(id: Int, refreshRate: Float) = RefreshRateMatcher.Mode(id, refreshRate)

    @Test
    fun `50 fps picks the 50 Hz mode over the 60 Hz one the box booted on`() {
        val picked = RefreshRateMatcher.pick(50f, listOf(mode(1, 60f), mode(2, 50f), mode(3, 30f)))
        assertThat(picked?.id).isEqualTo(2)
    }

    @Test
    fun `25 fps picks 50 Hz when the panel has no 25 Hz mode`() {
        // Two refreshes per frame is even; that is the whole point, not an approximation.
        val picked = RefreshRateMatcher.pick(25f, listOf(mode(1, 60f), mode(2, 50f)))
        assertThat(picked?.id).isEqualTo(2)
    }

    @Test
    fun `25 fps takes 100 Hz when 50 Hz is not offered`() {
        val picked = RefreshRateMatcher.pick(25f, listOf(mode(1, 60f), mode(2, 100f)))
        assertThat(picked?.id).isEqualTo(2)
    }

    @Test
    fun `a 24 fps film prefers 24 Hz over the 120 Hz mode that would also be exact`() {
        // Both are whole multiples; the lowest is the least opportunity for the panel to do damage.
        val picked = RefreshRateMatcher.pick(24f, listOf(mode(1, 120f), mode(2, 24f), mode(3, 60f)))
        assertThat(picked?.id).isEqualTo(2)
    }

    @Test
    fun `a 24 fps film falls back to 120 Hz when 24 Hz is absent and 60 Hz does not divide`() {
        val picked = RefreshRateMatcher.pick(24f, listOf(mode(1, 120f), mode(2, 60f)))
        assertThat(picked?.id).isEqualTo(1)
    }

    @Test
    fun `23_976 fps is shown on the 24 Hz mode`() {
        // 1.001:1, which the tolerance exists for.
        val picked = RefreshRateMatcher.pick(23.976f, listOf(mode(1, 60f), mode(2, 24f)))
        assertThat(picked?.id).isEqualTo(2)
    }

    @Test
    fun `29_97 fps is shown on the 30 Hz mode`() {
        val picked = RefreshRateMatcher.pick(29.97f, listOf(mode(1, 60f), mode(2, 30f)))
        assertThat(picked?.id).isEqualTo(2)
    }

    @Test
    fun `a 60 Hz-only panel is left alone for 50 fps content`() {
        // 60/50 is 1.2 refreshes a frame. Switching would buy nothing and cost a blank screen.
        assertThat(RefreshRateMatcher.pick(50f, listOf(mode(1, 60f)))).isNull()
    }

    @Test
    fun `a slower mode is never chosen, however close it is`() {
        assertThat(RefreshRateMatcher.pick(50f, listOf(mode(1, 30f), mode(2, 24f)))).isNull()
    }

    @Test
    fun `a mismatched rate that merely looks close is refused`() {
        // 24 fps on a 25 Hz mode is 4% out — nearer than 50 Hz, and still wrong.
        assertThat(RefreshRateMatcher.pick(24f, listOf(mode(1, 25f)))).isNull()
    }

    @Test
    fun `a stream with no frame rate reported is left alone`() {
        val modes = listOf(mode(1, 60f), mode(2, 50f))
        assertThat(RefreshRateMatcher.pick(0f, modes)).isNull()
        assertThat(RefreshRateMatcher.pick(Float.NaN, modes)).isNull()
        assertThat(RefreshRateMatcher.pick(-25f, modes)).isNull()
    }

    @Test
    fun `a display that reports no modes is left alone`() {
        assertThat(RefreshRateMatcher.pick(25f, emptyList())).isNull()
    }

    @Test
    fun `a mode reporting a zero refresh rate is ignored rather than dividing by zero`() {
        val picked = RefreshRateMatcher.pick(25f, listOf(mode(1, 0f), mode(2, 50f)))
        assertThat(picked?.id).isEqualTo(2)
    }
}
