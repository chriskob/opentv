package app.opentv

import app.opentv.ui.player.archiveRequestFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A timeshift stamp only has minute resolution, so a wanted instant has to be split into "the
 * minute to request" and "the leftover to seek within". Keeping the leftover under a minute is the
 * whole point: it is the part that depends on the player honouring a seek into a recording whose
 * length it cannot know yet, and that seek is exactly what got silently dropped.
 */
class ArchiveRequestTest {

    @Test
    fun `a target on the minute needs no offset at all`() {
        val r = archiveRequestFor(1_700_000_040_000L)
        assertEquals(1_700_000_040_000L, r.streamStartMillis)
        assertEquals(0L, r.offsetInStreamMillis)
    }

    @Test
    fun `a target mid-minute splits into that minute plus the remainder`() {
        val r = archiveRequestFor(1_700_000_075_000L)
        assertEquals(1_700_000_040_000L, r.streamStartMillis)
        assertEquals(35_000L, r.offsetInStreamMillis)
    }

    @Test
    fun `the offset is never a whole minute or more, whatever the target`() {
        // 30 minutes is the skip that used to hang: floored to the minute it leaves 0s behind, so
        // the request itself carries the whole move and nothing has to be seeked.
        assertEquals(0L, archiveRequestFor(1_700_000_040_000L + 1_800_000L).offsetInStreamMillis)
        var t = 1_700_000_000_000L
        repeat(1_000) {
            val r = archiveRequestFor(t)
            assertTrue(
                "offset ${r.offsetInStreamMillis} out of range at $t",
                r.offsetInStreamMillis in 0L until 60_000L,
            )
            t += 7919L // coprime-ish stride so every residue gets visited
        }
    }

    @Test
    fun `the two parts always add back up to the target`() {
        var t = 1_700_000_003_123L
        repeat(500) {
            val r = archiveRequestFor(t)
            assertEquals(t, r.streamStartMillis + r.offsetInStreamMillis)
            t += 104_729L
        }
    }

    @Test
    fun `the stream start is always a whole minute`() {
        var t = 1_700_000_000_000L
        repeat(200) {
            assertEquals(0L, archiveRequestFor(t).streamStartMillis % 60_000L)
            t += 33_331L
        }
    }
}
