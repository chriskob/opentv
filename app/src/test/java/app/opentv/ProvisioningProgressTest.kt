/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.ui.RemoteProvisioningProgress
import app.opentv.ui.RemoteProvisioningProgress.Stage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the guide card is allowed to claim while the guide is being built.
 *
 * Written because the failure mode was a bar frozen at zero under the words "Awaiting guide
 * parsing…" for the several minutes a large provider's feeds take — indistinguishable from a hang.
 * The rule now is that the bar measures *something real* from the first moment: feed parsing until
 * the matcher runs, then channel matching. Nothing is shown until a total is known, because a bar
 * with no denominator is a lie dressed as progress.
 */
class ProvisioningProgressTest {

    private fun progress(
        feedsDone: Int = 0,
        feedsTotal: Int = 0,
        matched: Int = 0,
        total: Int = 0,
    ) = RemoteProvisioningProgress(
        stage = Stage.SYNCING_EPG,
        epgFeedsDone = feedsDone,
        epgFeedsTotal = feedsTotal,
        epgChannelsMatched = matched,
        epgChannelsTotal = total,
    )

    @Test
    fun `the bar measures feed parsing before there is anything to match`() {
        val p = progress(feedsDone = 2, feedsTotal = 4)

        assertThat(p.epgBarIsFeeds).isTrue()
        assertThat(p.epgFraction).isEqualTo(0.5f)
    }

    @Test
    fun `matching takes the bar over once channels are known`() {
        // Feeds finished, matching under way: the channel fraction is the one worth showing, because
        // it is what decides whether a channel has a guide at all.
        val p = progress(feedsDone = 4, feedsTotal = 4, matched = 30, total = 120)

        assertThat(p.epgBarIsFeeds).isFalse()
        assertThat(p.epgFraction).isEqualTo(0.25f)
    }

    @Test
    fun `feeds still to come leave the bar at zero rather than absent`() {
        // Zero is the honest reading here: the total is known, so a bar is meaningful even before
        // the first feed lands.
        val p = progress(feedsDone = 0, feedsTotal = 3)

        assertThat(p.epgFraction).isEqualTo(0f)
        assertThat(p.epgBarIsFeeds).isTrue()
    }

    @Test
    fun `nothing known yet is indeterminate, not zero`() {
        assertThat(progress().epgFraction).isNull()
        assertThat(progress().epgBarIsFeeds).isFalse()
    }
}
