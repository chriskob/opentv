/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.EpgFeed
import app.opentv.ui.EpgViewModel
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme

/**
 * Guide settings: where guide data comes from.
 */
@Composable
fun EpgSettingsScreen(
    onBack: () -> Unit,
    viewModel: EpgViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newUrl by remember { mutableStateOf("") }

    var editingFeed by remember { mutableStateOf<EpgFeed?>(null) }
    var pendingDeleteFeed by remember { mutableStateOf<EpgFeed?>(null) }

    // Edit Guide Dialog (Name, URL, Enabled, Delete)
    editingFeed?.let { feed ->
        EditFeedDialog(
            feed = feed,
            onDismiss = { editingFeed = null },
            onSave = { updated ->
                viewModel.updateFeed(updated)
                editingFeed = null
            },
            onDelete = {
                viewModel.remove(it)
                editingFeed = null
            },
        )
    }

    // Direct Delete Confirmation Dialog (prevents focus jumping on TV)
    pendingDeleteFeed?.let { feed ->
        SettingsDialog(
            title = "Delete guide?",
            titleColor = SettingsDanger,
            message = "\"${feed.name}\" will be removed, and channel guide matching for this feed will go with it.",
            onDismiss = { pendingDeleteFeed = null },
            actions = {
                SettingsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { pendingDeleteFeed = null },
                    style = SettingsButtonStyle.Secondary,
                )
                Spacer(Modifier.width(12.dp))
                SettingsButton(
                    text = "Delete",
                    onClick = {
                        viewModel.remove(feed)
                        pendingDeleteFeed = null
                    },
                    style = SettingsButtonStyle.Danger,
                )
            },
        )
    }

    SettingsPage(
        title = stringResource(R.string.settings_guide_title),
        subtitle = stringResource(R.string.settings_guide_page_subtitle),
        onBack = onBack,
        actions = {
            SettingsButton(
                text = if (ui.syncing) stringResource(R.string.epg_updating) else stringResource(R.string.epg_update_now),
                onClick = { viewModel.refresh() },
                enabled = !ui.syncing,
                icon = Icons.Filled.Refresh,
                style = SettingsButtonStyle.Primary,
            )
        },
    ) {
        ui.statusLine?.let { line ->
            val statusIsError = line.contains("fail", ignoreCase = true) || line.contains("error", ignoreCase = true)
            val statusColor = if (statusIsError) SettingsDanger else AppTheme.primary
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(SettingsShape.Row)
                    .background(statusColor.copy(alpha = 0.12f))
                    .border(1.dp, statusColor.copy(alpha = 0.5f), SettingsShape.Row)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                )
            }
            Spacer(Modifier.height(SettingsSpacing.SectionGap))
        }

        val enabledFeeds = remember(ui.feeds) { ui.feeds.filter { it.enabled } }
        val availableFeeds = remember(ui.feeds) { ui.feeds.filterNot { it.enabled } }

        // ---- Section 1: Enabled Guides (On Top)
        SettingsSection(
            title = "Enabled Guides (${enabledFeeds.size})",
            icon = Icons.Filled.CheckCircle,
        ) {
            if (enabledFeeds.isEmpty()) {
                Text(
                    text = "No guides enabled yet. Turn on a guide from the available list below or add a custom XMLTV feed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                enabledFeeds.forEach { feed ->
                    FeedRow(
                        feed = feed,
                        onEdit = { editingFeed = feed },
                        onToggle = { enabled -> viewModel.setEnabled(feed, enabled) },
                        onDelete = { pendingDeleteFeed = feed },
                    )
                }
            }
        }

        // ---- Section 2: Available / Other Guides
        if (availableFeeds.isNotEmpty()) {
            Spacer(Modifier.height(SettingsSpacing.SectionGap))
            SettingsSection(
                title = "Available Guides (${availableFeeds.size})",
                icon = Icons.Filled.LiveTv,
            ) {
                availableFeeds.forEach { feed ->
                    FeedRow(
                        feed = feed,
                        onEdit = { editingFeed = feed },
                        onToggle = { enabled -> viewModel.setEnabled(feed, enabled) },
                        onDelete = { pendingDeleteFeed = feed },
                    )
                }
            }
        }

        // ---- Section 3: Add Custom XMLTV Feed / Manage
        Spacer(Modifier.height(SettingsSpacing.SectionGap))
        SettingsSection(
            title = "Manage & Add Guides",
            icon = Icons.Filled.Add,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!showAdd) {
                    SettingsButton(
                        text = stringResource(R.string.epg_add_own),
                        onClick = { showAdd = true },
                        icon = Icons.Filled.Add,
                        style = SettingsButtonStyle.Primary,
                    )
                }
                if (ui.hasDeletedBuiltIns) {
                    SettingsButton(
                        text = "Restore Default Guides",
                        onClick = { viewModel.restoreDefaultFeeds() },
                        icon = Icons.Filled.Refresh,
                        style = SettingsButtonStyle.Secondary,
                    )
                }
            }

            if (showAdd) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Add Custom XMLTV Feed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                TvOutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.epg_name_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                TvOutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text(stringResource(R.string.epg_xmltv_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsButton(
                        text = stringResource(R.string.epg_add_guide),
                        onClick = {
                            viewModel.addCustom(newName, newUrl)
                            newName = ""; newUrl = ""; showAdd = false
                        },
                        enabled = newUrl.isNotBlank(),
                        style = SettingsButtonStyle.Primary,
                    )
                    SettingsButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { showAdd = false },
                        style = SettingsButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedRow(
    feed: EpgFeed,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    // OK on the row edits the guide; enable/disable is its own control on the right, so pressing
    // the row no longer silently switches the feed on or off.
    SettingsNavRow(
        title = feed.name,
        subtitle = feedSubtitle(feed),
        onClick = onEdit,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsSwitch(
                    checked = feed.enabled,
                    onToggle = onToggle,
                )
                SettingsIconButton(
                    icon = Icons.Filled.Delete,
                    onClick = onDelete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = SettingsDanger,
                )
            }
        },
    )
}

private fun feedSubtitle(feed: EpgFeed): String? {
    val badge = when {
        feed.builtIn -> "BUILT-IN"
        feed.providerSourceId != null -> "PROVIDER"
        else -> null
    }
    val detail = feed.lastResult.ifBlank { feed.url.orEmpty() }
    return listOfNotNull(badge, detail.ifBlank { null }).joinToString(" · ").ifBlank { null }
}

@Composable
private fun EditFeedDialog(
    feed: EpgFeed,
    onDismiss: () -> Unit,
    onSave: (EpgFeed) -> Unit,
    onDelete: (EpgFeed) -> Unit,
) {
    var name by remember { mutableStateOf(feed.name) }
    var url by remember { mutableStateOf(feed.url ?: "") }
    var enabled by remember { mutableStateOf(feed.enabled) }
    var confirmingDelete by remember { mutableStateOf(false) }

    SettingsDialog(
        title = if (confirmingDelete) "Delete guide?" else "Edit guide",
        titleColor = if (confirmingDelete) SettingsDanger else MaterialTheme.colorScheme.onSurface,
        message = if (confirmingDelete) {
            "\"${feed.name}\" will be removed, and channel guide matching for this feed will go with it."
        } else {
            null
        },
        onDismiss = onDismiss,
        content = {
            if (!confirmingDelete) {
                TvOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Guide name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (feed.providerSourceId == null) {
                    TvOutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("XMLTV URL (.xml / .xml.gz)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "URL is derived from your IPTV provider source.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SettingsToggleRow(
                    title = "Enable guide",
                    subtitle = "Download and match channels with this guide",
                    checked = enabled,
                    onToggle = { enabled = it },
                )
            }
        },
        actions = {
            if (confirmingDelete) {
                SettingsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { confirmingDelete = false },
                    style = SettingsButtonStyle.Secondary,
                )
                Spacer(Modifier.width(12.dp))
                SettingsButton(
                    text = "Delete",
                    onClick = {
                        onDelete(feed)
                        onDismiss()
                    },
                    style = SettingsButtonStyle.Danger,
                )
            } else {
                SettingsButton(
                    text = stringResource(R.string.common_delete),
                    onClick = { confirmingDelete = true },
                    style = SettingsButtonStyle.Danger,
                    icon = Icons.Filled.Delete,
                )
                Spacer(Modifier.weight(1f))
                SettingsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onDismiss,
                    style = SettingsButtonStyle.Secondary,
                )
                Spacer(Modifier.width(12.dp))
                SettingsButton(
                    text = stringResource(R.string.common_save),
                    onClick = {
                        val trimmedUrl = url.trim().ifBlank { null }
                        onSave(feed.copy(name = name.trim().ifBlank { feed.name }, url = trimmedUrl, enabled = enabled))
                        onDismiss()
                    },
                    style = SettingsButtonStyle.Primary,
                )
            }
        },
    )
}
