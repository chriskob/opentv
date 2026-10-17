/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.parser.ChannelNameNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NonLatinChannelNamesTest {

    @Test
    fun `different Greek channels do not collapse onto the same group key`() {
        val ert = ChannelNameNormalizer.groupKeyOf("ΕΡΤ1")
        val net = ChannelNameNormalizer.groupKeyOf("ΝΕΤ1")

        assertThat(ert).isNotEqualTo(net)
        assertThat(ert).isNotEqualTo("1")
    }

    @Test
    fun `Cyrillic names keep their letters in the group key`() {
        assertThat(ChannelNameNormalizer.groupKeyOf("Первый канал")).isEqualTo("первыйканал")
    }
}
