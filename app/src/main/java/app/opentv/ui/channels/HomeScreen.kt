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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.FilterChip
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
 * The channel list.
 *
 * Shows what is on *now* and what is on *next* against every channel. That sounds obvious,
 * and it is exactly what people miss when a guide silently stops updating — so the "now"
 * line doubles as a permanent, glanceable health indicator for the EPG.
 */
@Composable
fun HomeScreen(
    isTelevision: Boolean,
    onPlayChannel: (Channel) -> Unit,
    onAddSource: () -> Unit,
    onRefresh: () -> Unit,
    viewModel: ChannelsViewModel = viewModel(),
) {
    val categories by viewModel.categories.collectAsState()
    val rows by viewModel.rows.collectAsState()
    var selected by remember { mutableLongStateOf(-1L) }

    // Re-evaluate "now" once a minute so the progress bars advance without the user
    // having to leave and come back.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.tick()
            delay(60_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Live TV", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onAddSource) {
                Icon(Icons.Default.Add, contentDescription = "Add source")
            }
        }

        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.selectCategory(null) },
                        label = { Text("All") },
                    )
                }
                items(categories, key = { "${it.sourceId}:${it.id}" }) { category ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.selectCategory(category.id) },
                        label = { Text(category.name, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (rows.isEmpty()) {
            EmptyState(onAddSource)
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 24.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { it.channel.id }) { row ->
                    ChannelRow(
                        row = row,
                        isSelected = selected == row.channel.id,
                        onClick = {
                            selected = row.channel.id
                            onPlayChannel(row.channel)
                        },
                        onToggleFavourite = { viewModel.toggleFavourite(row.channel) },
                    )
                }
            }
        }
    }
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
        else MaterialTheme.colorScheme.surface

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
            model = row.channel.logoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                row.channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                imageVector = if (row.channel.favourite) Icons.Default.Star
                else Icons.Outlined.StarOutline,
                contentDescription = if (row.channel.favourite) "Remove favourite" else "Favourite",
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
