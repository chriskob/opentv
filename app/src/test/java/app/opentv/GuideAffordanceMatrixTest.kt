/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.ui.channels.GuideCellAction
import app.opentv.ui.channels.guideCellTakesFocus
import app.opentv.ui.channels.resolveGuideCellAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What a guide cell does when the viewer presses OK, and whether the cursor may land on it.
 *
 * A programme that has already aired is only actionable if the channel can actually serve an
 * archive stream; otherwise the cell must not claim a catch-up it cannot deliver — the difference
 * between "nothing happens" and a playback error the viewer cannot act on.
 */
class GuideAffordanceMatrixTest {

    @Test
    fun aProgrammeStillAiring_playsLive() {
        assertThat(resolveGuideCellAction(isPast = false, archiveCapable = true))
            .isEqualTo(GuideCellAction.PLAY_LIVE)
        assertThat(resolveGuideCellAction(isPast = false, archiveCapable = false))
            .isEqualTo(GuideCellAction.PLAY_LIVE)
    }

    @Test
    fun aPastProgrammeOnAnArchiveChannel_launchesCatchup() {
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = true))
            .isEqualTo(GuideCellAction.LAUNCH_CATCHUP)
    }

    @Test
    fun aPastProgrammeWithoutArchive_isInert_whateverTheSettingSays() {
        // The toggle never turns an unsupported archive into a playable one, and never throws the
        // cell away: the action is inert either way.
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = false))
            .isEqualTo(GuideCellAction.INERT)
    }

    @Test
    fun anInertCellIsSteppedOverUnlessTheViewerAskedToSeeIt() {
        // Default: the cursor never lands somewhere that cannot be played.
        assertThat(guideCellTakesFocus(GuideCellAction.INERT, showPreviousWithoutCatchup = false))
            .isFalse()
        // Opted in: it is focusable and inspectable, still inert on OK.
        assertThat(guideCellTakesFocus(GuideCellAction.INERT, showPreviousWithoutCatchup = true))
            .isTrue()
    }

    @Test
    fun liveAndArchiveCellsAlwaysTakeFocus() {
        assertThat(guideCellTakesFocus(GuideCellAction.PLAY_LIVE, showPreviousWithoutCatchup = false)).isTrue()
        assertThat(guideCellTakesFocus(GuideCellAction.LAUNCH_CATCHUP, showPreviousWithoutCatchup = false)).isTrue()
        assertThat(guideCellTakesFocus(GuideCellAction.LAUNCH_CATCHUP, showPreviousWithoutCatchup = true)).isTrue()
    }

    @Test
    fun archiveCapableResolvesEvenWhenTheTimeshiftUrlCannotBeBuilt() {
        // The badge may show (a portal claim) while the URL fails to resolve; the cell must still
        // resolve to a catch-up attempt, and the failure belongs to playback.
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = true))
            .isNotEqualTo(GuideCellAction.INERT)
    }
}
