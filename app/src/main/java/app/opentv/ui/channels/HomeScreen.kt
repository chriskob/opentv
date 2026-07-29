/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.data.model.Channel
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Live TV: a category rail on the left, the channel list on the right.
 *
 * The rail is vertical because provider category lists run to dozens of entries and a
 * horizontal chip row hides all but the first few — on a d-pad, anything you cannot see
 * you cannot reach. Favourites and All are pinned at the top.
 *
 * The list shows what is on *now* and *next* against every channel. That doubles as a
 * permanent, glanceable health indicator for the guide: when the EPG stops updating, these
 * lines say so honestly instead of going quietly stale.
 */
@Composable
fun HomeScreen(
    isTelevision: Boolean,
    hasSources: Boolean,
    isSyncing: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    onGuideSettings: () -> Unit,
    viewModel: ChannelsViewModel = viewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val rows by viewModel.rows.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val favouritesOnly by viewModel.favouritesOnly.collectAsState()
    var selected by remember { mutableLongStateOf(-1L) }

    // Re-evaluate "now" once a minute so progress bars advance without leaving the screen.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.tick()
            delay(60_000)
        }
    }

    Row(Modifier.fillMaxSize()) {

        // ---- Category rail -----------------------------------------------------------------
        Column(
            Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp),
        ) {
            Text(
                "Live TV",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    RailEntry(
                        label = "★ Favourites",
                        selected = favouritesOnly,
                        onClick = viewModel::selectFavourites,
                    )
                }
                item {
                    RailEntry(
                        label = "All channels",
                        selected = !favouritesOnly && selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) },
                    )
                }
                items(categories, key = { "${it.sourceId}:${it.id}" }) { category ->
                    RailEntry(
                        label = category.name,
                        selected = !favouritesOnly && selectedCategory == category.id,
                        onClick = { viewModel.selectCategory(category.id) },
                    )
                }
            }
        }

        // ---- Channel list ------------------------------------------------------------------
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                IconButton(onClick = onGuideSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Guide settings")
                }
                IconButton(onClick = onAddSource) {
                    Icon(Icons.Default.Add, contentDescription = "Add source")
                }
            }

            if (rows.isEmpty()) {
                when {
                    favouritesOnly -> NoFavouritesState()
                    // A large provider takes a while to sync, and showing "No channels"
                    // during it reads as failure — which is how someone concludes an app
                    // is broken thirty seconds after installing it.
                    isSyncing || hasSources -> LoadingState(isSyncing)
                    else -> EmptyState(onAddSource)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        ChannelRow(
                            row = row,
                            isSelected = selected == row.primary.id,
                            onClick = {
                                selected = row.primary.id
                                onPlayChannel(row.primary)
                            },
                            onToggleFavourite = { viewModel.toggleFavourite(row) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailEntry(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun ChannelRow(
    row: ChannelsViewModel.Row,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val background =
        if (isSelected) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.background

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = row.primary.logoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.primary.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // One logical channel, several qualities: say so quietly. The switch
                // itself lives in the player, where the decision is actually made.
                if (row.variants.size > 1) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${row.variants.size} qualities",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (row.primary.qualityLabel.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.primary.qualityLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = row.now?.let { "${formatTime(it.startUtcMillis)}  ${it.title}" }
                    // Honest rather than blank. A user who sees this on every channel knows
                    // the guide is the problem, not their provider.
                    ?: "No guide information",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.now?.let { programme ->
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { programme.progressAt(now) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
            row.next?.let { next ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Next  ${formatTime(next.startUtcMillis)}  ${next.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (row.primary.favourite) Icons.Default.Star
                else Icons.Outlined.StarOutline,
                contentDescription = if (row.primary.favourite) "Remove favourite" else "Favourite",
            )
        }
    }
}

@Composable
private fun LoadingState(isSyncing: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text("Loading your channels", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                if (isSyncing) {
                    "Fetching the channel list and guide from your provider. A large " +
                        "provider can take a couple of minutes the first time."
                } else {
                    "Nothing came back from your provider last time. Press refresh to " +
                        "try again."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 460.dp),
            )
        }
    }
}

@Composable
private fun NoFavouritesState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No favourites yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Press the star on any channel and it lands here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(onAddSource: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No channels yet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add a provider to get started.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAddSource) { Text("Add a provider") }
        }
    }
}

/** UTC in the database, device zone on screen. Converted here and nowhere else. */
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(utcMillis: Long): String = timeFormat.format(Date(utcMillis))
