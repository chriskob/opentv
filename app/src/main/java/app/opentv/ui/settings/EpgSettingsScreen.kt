/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import app.opentv.ui.theme.AppTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import app.opentv.ui.components.TvOutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.EpgFeed
import app.opentv.ui.EpgViewModel

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
        AlertDialog(
            onDismissRequest = { pendingDeleteFeed = null },
            title = {
                Text(
                    text = "Delete Guide?",
                    color = Color(0xFFEF5350),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${feed.name}\"? Channel guide matching for this feed will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCFD8DC),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.remove(feed)
                        pendingDeleteFeed = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                    ),
                ) {
                    Text("Yes, Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFeed = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        // Top Action Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_guide_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Manage XMLTV guide feeds, auto-matching, and sync status",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            EpgActionButton(
                label = if (ui.syncing) stringResource(R.string.epg_updating) else stringResource(R.string.epg_update_now),
                icon = Icons.Filled.Refresh,
                enabled = !ui.syncing,
                onClick = { viewModel.refresh() },
            )

            Spacer(Modifier.width(14.dp))

            EpgBackButton(onBack)
        }

        ui.statusLine?.let { line ->
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF004D40).copy(alpha = 0.4f))
                    .border(1.dp, Color(0xFF26A69A).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF80CBC4),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        val enabledFeeds = remember(ui.feeds) { ui.feeds.filter { it.enabled } }
        val availableFeeds = remember(ui.feeds) { ui.feeds.filterNot { it.enabled } }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // ---- Section 1: Enabled Guides (On Top)
            item(key = "header_enabled") {
                GuideSectionHeader(
                    title = "Enabled Guides (${enabledFeeds.size})",
                    icon = Icons.Filled.CheckCircle,
                )
            }

            if (enabledFeeds.isEmpty()) {
                item(key = "empty_enabled") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF18222C))
                            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = "No guides enabled yet. Turn on a guide from the available list below or add a custom XMLTV feed.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8B9BA8),
                        )
                    }
                }
            } else {
                items(enabledFeeds, key = { "enabled_${it.id}" }) { feed ->
                    FeedRow(
                        feed = feed,
                        onEdit = { editingFeed = feed },
                        onToggle = { enabled -> viewModel.setEnabled(feed, enabled) },
                        onDelete = { pendingDeleteFeed = feed },
                    )
                }
            }

            // ---- Section 2: Available / Other Guides
            if (availableFeeds.isNotEmpty()) {
                item(key = "header_available") {
                    Spacer(Modifier.height(8.dp))
                    GuideSectionHeader(
                        title = "Available Guides (${availableFeeds.size})",
                        icon = Icons.Filled.LiveTv,
                    )
                }
                items(availableFeeds, key = { "available_${it.id}" }) { feed ->
                    FeedRow(
                        feed = feed,
                        onEdit = { editingFeed = feed },
                        onToggle = { enabled -> viewModel.setEnabled(feed, enabled) },
                        onDelete = { pendingDeleteFeed = feed },
                    )
                }
            }

            // ---- Section 3: Add Custom XMLTV Feed / Manage
            item(key = "footer_actions") {
                Spacer(Modifier.height(8.dp))
                GuideSectionHeader(
                    title = "Manage & Add Guides",
                    icon = Icons.Filled.Add,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!showAdd) {
                        EpgActionButton(
                            label = stringResource(R.string.epg_add_own),
                            icon = Icons.Filled.Add,
                            onClick = { showAdd = true },
                        )
                    }
                    if (ui.hasDeletedBuiltIns) {
                        EpgActionButton(
                            label = "Restore Default Guides",
                            icon = Icons.Filled.Refresh,
                            onClick = { viewModel.restoreDefaultFeeds() },
                        )
                    }
                }

                if (showAdd) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF18222C))
                            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
                            .padding(20.dp),
                    ) {
                        Column {
                            Text(
                                text = "Add Custom XMLTV Feed",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
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
                                Button(
                                    onClick = {
                                        viewModel.addCustom(newName, newUrl)
                                        newName = ""; newUrl = ""; showAdd = false
                                    },
                                    enabled = newUrl.isNotBlank(),
                                ) { Text(stringResource(R.string.epg_add_guide)) }
                                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.common_cancel)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideSectionHeader(title: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = AppTheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF1E2D3C))
        )
    }
}

@Composable
private fun FeedRow(
    feed: EpgFeed,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18222C))
            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Guide Info & Edit card (primary clickable element)
        var infoFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { infoFocused = it.isFocused }
                .clip(RoundedCornerShape(10.dp))
                .background(if (infoFocused) AppTheme.cardFocusBg else Color.Transparent)
                .then(
                    if (infoFocused) Modifier.border(2.dp, AppTheme.primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEdit,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (infoFocused) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .background(AppTheme.primary, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = feed.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    if (feed.builtIn) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AppTheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "BUILT-IN",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = AppTheme.light,
                            )
                        }
                    } else if (feed.providerSourceId != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "PROVIDER",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFD54F),
                            )
                        }
                    }
                }
                if (feed.lastResult.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = feed.lastResult,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = if (infoFocused) Color(0xFFB0BEC5) else Color.White.copy(alpha = 0.65f),
                    )
                } else if (!feed.url.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = feed.url,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = if (infoFocused) Color(0xFFB0BEC5) else Color(0xFF8B9BA8),
                        maxLines = 1,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            // Visual edit indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (infoFocused) AppTheme.primary.copy(alpha = 0.2f) else Color(0xFF263442).copy(alpha = 0.4f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = if (infoFocused) AppTheme.primary else Color(0xFF8B9BA8),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    color = if (infoFocused) Color.White else Color(0xFF8B9BA8),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Delete action button
        var deleteFocused by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .onFocusChanged { deleteFocused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (deleteFocused) Color(0xFFD32F2F).copy(alpha = 0.25f)
                    else Color(0xFF221518).copy(alpha = 0.5f),
                )
                .then(
                    if (deleteFocused) Modifier.border(1.5.dp, Color(0xFFEF5350), RoundedCornerShape(8.dp))
                    else Modifier.border(0.75.dp, Color(0xFF5A2C2C), RoundedCornerShape(8.dp)),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = if (deleteFocused) Color(0xFFFF5252) else Color(0xFFEF5350),
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.common_delete),
                color = if (deleteFocused) Color.White else Color(0xFFEF5350),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                fontWeight = if (deleteFocused) FontWeight.Bold else FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Toggle switch
        Switch(
            checked = feed.enabled,
            onCheckedChange = onToggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = AppTheme.primary,
                checkedTrackColor = AppTheme.dark.copy(alpha = 0.55f),
                uncheckedThumbColor = Color(0xFFB0BEC5),
                uncheckedTrackColor = Color(0xFF37474F),
            ),
        )
    }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (confirmingDelete) "Delete Guide?" else "Edit Guide Settings",
                color = if (confirmingDelete) Color(0xFFEF5350) else Color.White,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (confirmingDelete) {
                Text(
                    text = "Are you sure you want to delete \"${feed.name}\"? Channel guide matching for this feed will be removed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCFD8DC),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TvOutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Guide Name") },
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
                            color = Color(0xFF8B9BA8),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { enabled = !enabled }
                        ) {
                            Text("Enable Guide", style = MaterialTheme.typography.titleSmall, color = Color.White)
                            Text("Download and match channels with this guide", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8B9BA8))
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = AppTheme.primary,
                                checkedTrackColor = AppTheme.dark.copy(alpha = 0.55f),
                                uncheckedThumbColor = Color(0xFFB0BEC5),
                                uncheckedTrackColor = Color(0xFF37474F),
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (confirmingDelete) {
                Button(
                    onClick = {
                        onDelete(feed)
                        onDismiss()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                    ),
                ) {
                    Text("Yes, Delete")
                }
            } else {
                Button(
                    onClick = {
                        val trimmedUrl = url.trim().ifBlank { null }
                        onSave(feed.copy(name = name.trim().ifBlank { feed.name }, url = trimmedUrl, enabled = enabled))
                        onDismiss()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = AppTheme.dark,
                    ),
                ) {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = {
            if (confirmingDelete) {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { confirmingDelete = true },
                    ) {
                        Text(stringResource(R.string.common_delete), color = Color(0xFFEF5350))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        },
    )
}

@Composable
private fun EpgActionButton(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) Brush.linearGradient(listOf(AppTheme.dark, AppTheme.primary))
                else Brush.linearGradient(listOf(Color(0xFF1E2833), Color(0xFF1E2833))),
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.light, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(10.dp)),
            )
            .focusable(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) Color.White else AppTheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun EpgBackButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) Brush.linearGradient(listOf(AppTheme.dark, AppTheme.primary))
                else Brush.linearGradient(listOf(Color(0xFF1E2833), Color(0xFF1E2833))),
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.light, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(10.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.common_done),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

