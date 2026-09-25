package app.opentv

import androidx.compose.ui.unit.dp
import app.opentv.ui.channels.BlockLayout
import app.opentv.ui.channels.firstBlockEndingAfter
import app.opentv.ui.channels.formatChannelNameForDisplay
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GuidePerformanceTest {
    @Test
    fun `binary programme window lookup skips ended blocks`() {
        val layouts = listOf(
            BlockLayout(0L, 10L, 10.dp, 0),
            BlockLayout(10L, 20L, 10.dp, 1),
            BlockLayout(20L, 40L, 20.dp, 2),
        )

        assertThat(firstBlockEndingAfter(layouts, -1L)).isEqualTo(0)
        assertThat(firstBlockEndingAfter(layouts, 10L)).isEqualTo(1)
        assertThat(firstBlockEndingAfter(layouts, 20L)).isEqualTo(2)
        assertThat(firstBlockEndingAfter(layouts, 40L)).isEqualTo(3)
    }

    @Test
    fun `channel display formatting handles common provider suffixes`() {
        assertThat(formatChannelNameForDisplay("CBS 2 CHICAGO (WBBM)")).isEqualTo("CBS 2 CHICAGO\n(WBBM)")
        assertThat(formatChannelNameForDisplay("FOX WEATHER HD")).isEqualTo("FOX WEATHER\n(HD)")
        assertThat(formatChannelNameForDisplay("Already\nFormatted")).isEqualTo("Already\nFormatted")
    }
}
