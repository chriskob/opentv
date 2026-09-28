package app.opentv

import app.opentv.ui.player.archiveSkipTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A catch-up stream can only be requested by time, so a skip has to be turned back into "when".
 * Getting this wrong is why a forward skip used to seek a file the provider was still writing -
 * which re-buffers for minutes - instead of asking for the minute the viewer wanted.
 */
class ArchiveSkipTargetTest {

    // A one-hour programme starting at 17:00 UTC, played from its start.
    private val progStart = 1_700_000_000_000L
    private val now = progStart + 3 * 3_600_000L // three hours later

    @Test
    fun `in an archive the target is the programme start plus the playhead, plus the step`() {
        // 30 seconds into the programme, skipping forward 30s asks for 17:00:30.
        assertEquals(
            progStart + 60_000L,
            archiveSkipTarget(archiveStartMillis = progStart, positionMillis = 30_000L, deltaMillis = 30_000L, nowMillis = now),
        )
    }

    @Test
    fun `a burst of skips asks for three different minutes, not the same one three times`() {
        var position = 0L
        val targets = mutableListOf<Long>()
        repeat(3) {
            val t = archiveSkipTarget(progStart, position, deltaMillis = 30_000L, nowMillis = now)
            targets += t
            // The stream re-opens at the offset that was asked for, so the next press counts from
            // there - this is what makes a burst accumulate instead of overwriting itself.
            position = t - progStart
        }
        assertEquals(listOf(progStart + 30_000L, progStart + 60_000L, progStart + 90_000L), targets)
    }

    @Test
    fun `rewinding in an archive subtracts rather than jumping to the live edge`() {
        assertEquals(
            progStart + 20_000L,
            archiveSkipTarget(archiveStartMillis = progStart, positionMillis = 50_000L, deltaMillis = -30_000L, nowMillis = now),
        )
    }

    @Test
    fun `on live the playhead is now, because there is no programme to be relative to`() {
        // A back skip from the live edge is the classic "rewind 10s" on a live channel.
        assertEquals(
            now - 10_000L,
            archiveSkipTarget(archiveStartMillis = 0L, positionMillis = 0L, deltaMillis = -10_000L, nowMillis = now),
        )
    }

    @Test
    fun `a forward skip from live lands just ahead of the live edge`() {
        assertEquals(
            now + 30_000L,
            archiveSkipTarget(archiveStartMillis = 0L, positionMillis = 0L, deltaMillis = 30_000L, nowMillis = now),
        )
    }

    @Test
    fun `the programme start, not the playhead, anchors an archive target`() {
        // Anchoring on the playhead alone would produce a time near zero and ask the provider for
        // 1970, which is the failure mode this arithmetic exists to prevent.
        val target = archiveSkipTarget(progStart, positionMillis = 900_000L, deltaMillis = 0L, nowMillis = now)
        assertEquals(progStart + 900_000L, target)
        assert(target > progStart) { "target must not collapse to the epoch" }
    }
}
