/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opentv.data.model.Channel
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.ui.channels.HomeScreen
import app.opentv.ui.vod.MoviesScreen
import app.opentv.ui.vod.SeriesScreen

/**
 * The shell: a top navigation bar (Live TV / Movies / Shows) over a content area. Kept
 * dead simple — the tabs are just state, no nested nav graph — so a contributor can see the
 * whole top-level shape in one screen.
 */
enum class Tab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SHOWS("Shows") }

@Composable
fun MainScreen(
    isTelevision: Boolean,
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onOpenSeries: (Series) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onGuideSettings: () -> Unit,
) {
    var tab by remember { mutableStateOf(Tab.LIVE) }

    Column(Modifier.fillMaxSize()) {
        TopNav(current = tab, onSelect = { tab = it })

        when (tab) {
            Tab.LIVE -> HomeScreen(
                isTelevision = isTelevision,
                hasSources = hasSources,
                isSyncing = isSyncing,
                onPlayChannel = onPlayChannel,
                onAddSource = onAddSource,
                onRefresh = onRefresh,
                onGuideSettings = onGuideSettings,
            )
            Tab.MOVIES -> MoviesScreen(onPlayMovie = onPlayMovie)
            Tab.SHOWS -> SeriesScreen(onOpenSeries = onOpenSeries)
        }
    }
}

@Composable
private fun TopNav(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            "OpenTV",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(28.dp))
        Tab.entries.forEach { t ->
            val selected = t == current
            Text(
                t.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(t) }
                    .background(
                        if (selected) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.background,
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}
