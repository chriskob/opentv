package app.opentv

import app.opentv.ui.player.seekBaseFor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A catch-up seek re-buffers, so the playhead lags the seek that was just asked for. A burst of
 * skip presses therefore has to count from the pending target, or every press recomputes from the
 * same stale playhead and they all land in the same place — three Rights moving 30s, not 90s.
 */
class PlayerSeekAccumulationTest {

    @Test
    fun `a press right after a seek counts from the pending target, not the lagging playhead`() {
        // Playhead has not moved off 0 yet; we already asked for 30s.
        assertEquals(30_000L, seekBaseFor(pendingTarget = 30_000L, pendingAgeMillis = 80L, playhead = 0L))
    }

    @Test
    fun `three presses in a burst accumulate to three steps`() {
        val start = 0L
        var pending = -1L
        val targets = mutableListOf<Long>()
        repeat(3) {
            val base = seekBaseFor(pending, pendingAgeMillis = if (pending < 0L) 9_999L else 60L, playhead = start)
            val target = (base + 30_000L).coerceAtLeast(0L)
            targets += target
            pending = target
        }
        assertEquals(listOf(30_000L, 60_000L, 90_000L), targets)
    }

    @Test
    fun `a stale pending target is discarded in favour of the playhead`() {
        // Older than the accumulate window: the seek has landed, so trust where we actually are.
        assertEquals(120_000L, seekBaseFor(pendingTarget = 30_000L, pendingAgeMillis = 5_000L, playhead = 120_000L))
    }

    @Test
    fun `no pending target means the playhead`() {
        assertEquals(7_000L, seekBaseFor(pendingTarget = -1L, pendingAgeMillis = 0L, playhead = 7_000L))
    }

    @Test
    fun `a target recorded before the clock was readable does not win`() {
        // elapsedRealtime going backwards (a fresh process, a re-created state holder) would
        // otherwise produce a negative age and make a stale target look permanently fresh.
        assertEquals(4_000L, seekBaseFor(pendingTarget = 30_000L, pendingAgeMillis = -1L, playhead = 4_000L))
    }
}
