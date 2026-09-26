package app.opentv

import androidx.compose.ui.input.key.Key
import app.opentv.ui.player.isPlayerUpKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerKeyRoutingTest {
    @Test
    fun `timeline recognizes all player up key aliases`() {
        assertThat(isPlayerUpKey(Key.DirectionUp, 19)).isTrue()
        assertThat(isPlayerUpKey(Key.ChannelUp, 166)).isTrue()
        assertThat(isPlayerUpKey(Key.PageUp, 92)).isTrue()
        assertThat(isPlayerUpKey(Key.DirectionUp, 166)).isTrue()
        assertThat(isPlayerUpKey(Key.DirectionUp, 92)).isTrue()
    }

    @Test
    fun `timeline does not classify down as an up key`() {
        assertThat(isPlayerUpKey(Key.DirectionDown, 20)).isFalse()
        assertThat(isPlayerUpKey(Key.DirectionLeft, 21)).isFalse()
        assertThat(isPlayerUpKey(Key.DirectionRight, 22)).isFalse()
    }
}
