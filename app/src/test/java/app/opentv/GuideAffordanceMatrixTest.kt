/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.ui.channels.GuideCellAction
import app.opentv.ui.channels.resolveGuideCellAction
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What a guide cell does when the viewer presses OK, decided by one pure function.
 *
 * A programme that has already aired is only actionable if the channel can actually serve an
 * archive stream; otherwise the cell must not claim a catch-up it cannot deliver, which is the
 * difference between "nothing happens" and a playback error the viewer cannot act on.
 */
class GuideAffordanceMatrixTest {

    @Test
    fun aProgrammeStillAiring_playsLive() {
        assertThat(resolveGuideCellAction(isPast = false, archiveCapable = true, showPreviousWithoutCatchup = false))
            .isEqualTo(GuideCellAction.PLAY_LIVE)
        assertThat(resolveGuideCellAction(isPast = false, archiveCapable = false, showPreviousWithoutCatchup = false))
            .isEqualTo(GuideCellAction.PLAY_LIVE)
    }

    @Test
    fun aPastProgrammeOnAnArchiveChannel_launchesCatchup() {
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = true, showPreviousWithoutCatchup = false))
            .isEqualTo(GuideCellAction.LAUNCH_CATCHUP)
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = true, showPreviousWithoutCatchup = true))
            .isEqualTo(GuideCellAction.LAUNCH_CATCHUP)
    }

    @Test
    fun aPastProgrammeWithoutArchive_isInertAndSilent() {
        // The default: the cell is shown (so the guide still reads as a timeline) but pressing
        // OK does nothing and reports nothing.
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = false, showPreviousWithoutCatchup = true))
            .isEqualTo(GuideCellAction.INERT)
    }

    @Test
    fun aPastProgrammeWithoutArchive_isHiddenWhenTheSettingIsOff() {
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = false, showPreviousWithoutCatchup = false))
            .isEqualTo(GuideCellAction.HIDE)
    }

    @Test
    fun archiveCapableResolvesEvenWhenTheTimeshiftUrlCannotBeBuilt() {
        // The badge may show (a portal claim) while the URL fails to resolve; the cell must
        // still resolve to a catch-up attempt, and the failure belongs to playback.
        assertThat(resolveGuideCellAction(isPast = true, archiveCapable = true, showPreviousWithoutCatchup = false))
            .isNotEqualTo(GuideCellAction.INERT)
    }
}
