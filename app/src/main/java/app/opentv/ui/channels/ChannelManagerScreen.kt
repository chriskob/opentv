/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage

/**
 * Channel manager: find a channel and hide it from the guide, or favourite it.
 *
 * Search-driven rather than a flat 20,000-row list, because that list is unscrollable on a
 * remote. Hiding acts on the whole logical channel (every quality variant), and hidden channels
 * still appear here — greyed — so they can be brought back.
 */
@Composable
fun ChannelManagerScreen(
    onBack: () -> Unit,
    viewModel: ChannelsViewModel = viewModel(),
) {
    var query by remember { mutableStateOf("") }
    val results by viewModel.managerResults.collectAsState()

    androidx.compose.runtime.LaunchedEffect(query) { viewModel.setManagerQuery(query) }
    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.channels_manager_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(20.dp))
            Text(
                query.ifEmpty { stringResource(R.string.channels_manager_hint) },
                style = MaterialTheme.typography.titleLarge,
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxSize()) {
            OnScreenKeyboard(
                onKey = { if (query.length < 40) query += it },
                onSpace = { if (query.length < 40) query += " " },
                onBackspace = { query = query.dropLast(1) },
                onClear = { query = "" },
            )

            Spacer(Modifier.width(28.dp))

            Column(Modifier.weight(1f).fillMaxSize()) {
                when {
                    query.isBlank() -> Hint(stringResource(R.string.channels_manager_search_hint))
                    query.trim().length < 2 -> Hint(stringResource(R.string.common_keep_typing))
                    results.isEmpty() -> Hint(stringResource(R.string.channels_manager_no_match, query))
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(results, key = { it.key }) { row ->
                            val hidden = row.variants.all { it.hidden }
                            ManagerRow(
                                row = row,
                                hidden = hidden,
                                onToggleHidden = { viewModel.setRowHidden(row, !hidden) },
                                onToggleFavourite = { viewModel.toggleFavourite(row) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManagerRow(
    row: ChannelsViewModel.Row,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val alpha = if (hidden) 0.5f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = row.primary.logoUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.primary.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (hidden) stringResource(R.string.channels_hidden) else stringResource(R.string.channels_showing),
                style = MaterialTheme.typography.bodyMedium,
                color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
        }

        IconButton(onClick = onToggleFavourite) {
            Icon(
                imageVector = if (row.primary.favourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (row.primary.favourite) stringResource(R.string.common_remove_favourite) else stringResource(R.string.common_favourite),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.channels_show), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            // Checked = visible; off = hidden. Reads the natural way: switch it off to hide.
            Switch(checked = !hidden, onCheckedChange = { onToggleHidden() })
        }
    }
}
