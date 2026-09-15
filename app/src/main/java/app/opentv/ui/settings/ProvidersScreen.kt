/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.LiveStreamFormat
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.*
import kotlinx.coroutines.launch

/**
 * The provider list: what's connected, with the plumbing to add another, edit settings, or remove one.
 */
@Composable
fun ProvidersScreen(
    onAddSource: () -> Unit,
    onOpenRemotePairing: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: SourcesViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var pendingRemove by remember { mutableStateOf<Source?>(null) }
    var editingSource by remember { mutableStateOf<Source?>(null) }

    editingSource?.let { src ->
        EditSourceDialog(
            source = src,
            onDismiss = { editingSource = null },
            onSave = { updated, resync ->
                viewModel.updateSource(updated, resync = resync)
                editingSource = null
            },
            onTest = { draft -> viewModel.testSource(draft) },
        )
    }

    pendingRemove?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = SettingsShape.Dialogs,
            title = {
                Text(
                    text = "Delete Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${source.name}\"? Channels and VOD associated with this playlist will be removed from OpenTV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                SettingsButton(
                    text = "Yes, Delete",
                    onClick = {
                        viewModel.delete(source)
                        pendingRemove = null
                    },
                    style = SettingsButtonStyle.Danger,
                )
            },
            dismissButton = {
                SettingsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { pendingRemove = null },
                    style = SettingsButtonStyle.Secondary,
                )
            },
        )
    }

    SettingsPage(
        title = stringResource(R.string.settings_providers_title),
        subtitle = stringResource(R.string.settings_providers_page_subtitle),
        onBack = onBack,
        actions = {
            SettingsButton(
                text = "Remote Edit / Pair",
                onClick = onOpenRemotePairing,
                style = SettingsButtonStyle.Secondary,
                icon = Icons.Filled.PhoneAndroid,
            )
            Spacer(Modifier.width(12.dp))
            SettingsButton(
                text = stringResource(R.string.providers_add),
                onClick = onAddSource,
                style = SettingsButtonStyle.Primary,
                icon = Icons.Filled.Add,
            )
        },
    ) {
        if (ui.sources.isEmpty()) {
            SettingsEmptyState(
                title = stringResource(R.string.providers_empty),
                icon = Icons.Filled.Dns,
            )
        } else {
            val enabledSources = remember(ui.sources) { ui.sources.filter { it.enabled } }
            val disabledSources = remember(ui.sources) { ui.sources.filterNot { it.enabled } }

            ProviderSection(
                title = "Enabled Playlists (${enabledSources.size})",
                icon = Icons.Filled.CheckCircle,
                sources = enabledSources,
                emptyMessage = "No playlists enabled. Turn on a playlist from the list below or add a new provider.",
                onEdit = { editingSource = it },
                onToggle = { source, enabled -> viewModel.setEnabled(source, enabled) },
                onDelete = { pendingRemove = it },
                onSetLiveFormat = { source, format -> viewModel.setLiveFormat(source, format) },
                showMove = true,
                onMove = { source, delta -> viewModel.moveSource(source.id, delta) },
            )

            if (disabledSources.isNotEmpty()) {
                Spacer(Modifier.height(SettingsSpacing.SectionGap))
                ProviderSection(
                    title = "Disabled Playlists (${disabledSources.size})",
                    icon = Icons.Filled.Block,
                    sources = disabledSources,
                    onEdit = { editingSource = it },
                    onToggle = { source, enabled -> viewModel.setEnabled(source, enabled) },
                    onDelete = { pendingRemove = it },
                    onSetLiveFormat = { source, format -> viewModel.setLiveFormat(source, format) },
                )
            }
        }
    }
}

/**
 * One section of the provider list. Enabled and disabled playlists are byte-identical apart from
 * their label/icon (and the enabled section's empty hint), so both call this.
 */
@Composable
private fun ProviderSection(
    title: String,
    icon: ImageVector,
    sources: List<Source>,
    onEdit: (Source) -> Unit,
    onToggle: (Source, Boolean) -> Unit,
    onDelete: (Source) -> Unit,
    onSetLiveFormat: (Source, LiveStreamFormat) -> Unit,
    emptyMessage: String? = null,
    showMove: Boolean = false,
    onMove: (Source, Int) -> Unit = { _, _ -> },
) {
    SettingsSection(title = title, icon = icon) {
        if (sources.isEmpty() && emptyMessage != null) {
            SettingsEmptyState(title = emptyMessage)
        } else {
            sources.forEachIndexed { index, source ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                ProviderRow(
                    source = source,
                    index = index,
                    count = sources.size,
                    showMove = showMove,
                    onMove = { onMove(source, it) },
                    onEdit = { onEdit(source) },
                    onToggle = { onToggle(source, it) },
                    onDelete = { onDelete(source) },
                    onSetLiveFormat = { onSetLiveFormat(source, it) },
                )
            }
        }
    }
}

/** A compact up/down reorder control for a playlist row. */
@Composable
private fun MoveButton(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(30.dp)
            .settingsFocus(shape = SettingsShape.Control, enabled = enabled, focusScale = 1.06f)
            .focusable(enabled)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ProviderRow(
    source: Source,
    index: Int,
    count: Int,
    showMove: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSetLiveFormat: (LiveStreamFormat) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Provider Info & Edit card (primary clickable element)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .settingsFocus(shape = SettingsShape.Row)
                    .focusable()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Kind Badge — genuinely semantic hues per provider kind.
                val kindColor = when (source.kind) {
                    SourceKind.XTREAM -> Color(0xFF29B6F6)
                    SourceKind.M3U -> Color(0xFF66BB6A)
                    SourceKind.STALKER -> Color(0xFFFFA726)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(kindColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = source.kind.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = kindColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = hostOf(source.url),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Visual edit indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Reorder controls — only on the enabled list, where the order actually shows in the app.
            if (showMove) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MoveButton(
                        icon = Icons.Filled.KeyboardArrowUp,
                        enabled = index > 0,
                        contentDescription = "Move up",
                        onClick = { onMove(-1) },
                    )
                    MoveButton(
                        icon = Icons.Filled.KeyboardArrowDown,
                        enabled = index < count - 1,
                        contentDescription = "Move down",
                        onClick = { onMove(1) },
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            // Delete action button
            Row(
                modifier = Modifier
                    .settingsFocus(shape = SettingsShape.Control, danger = true)
                    .focusable()
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
                    tint = SettingsDanger,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.common_delete),
                    color = SettingsDanger,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Toggle switch
            Switch(
                checked = source.enabled,
                onCheckedChange = onToggle,
                colors = settingsSwitchColors(),
                modifier = Modifier.focusProperties { canFocus = false },
            )
        }

        // Stream format picker for Xtream sources
        if (source.kind == SourceKind.XTREAM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Stream Format:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsSegmented(
                    options = listOf(
                        stringResource(R.string.provider_stream_format_hls),
                        stringResource(R.string.provider_stream_format_ts),
                    ),
                    selectedIndex = if (source.liveFormat == LiveStreamFormat.HLS) 0 else 1,
                    onSelect = { index ->
                        onSetLiveFormat(if (index == 0) LiveStreamFormat.HLS else LiveStreamFormat.MPEG_TS)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EditSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onSave: (Source, Boolean) -> Unit,
    onTest: suspend (Source) -> Result<String>,
) {
    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.url) }
    var username by remember { mutableStateOf(source.username ?: "") }
    var password by remember { mutableStateOf(source.password ?: "") }
    var mac by remember { mutableStateOf(source.macAddress ?: "") }
    var epgUrl by remember { mutableStateOf(source.epgUrl ?: "") }
    var userAgent by remember { mutableStateOf(source.userAgent) }
    var liveFormat by remember { mutableStateOf(source.liveFormat) }
    var resyncOnSave by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = SettingsShape.Dialogs,
        title = { Text("Edit Playlist Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TvOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist / Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                TvOutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        Text(
                            when (source.kind) {
                                SourceKind.XTREAM -> "Server Address"
                                SourceKind.M3U -> "Playlist URL"
                                SourceKind.STALKER -> "Portal URL"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (source.kind == SourceKind.XTREAM) {
                    TvOutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TvOutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StreamFormatSelector(
                        selected = liveFormat,
                        onSelect = { liveFormat = it },
                    )
                } else if (source.kind == SourceKind.STALKER) {
                    TvOutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        label = { Text("MAC Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                TvOutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("Custom EPG / XMLTV URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                TvOutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("User-Agent Header") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = resyncOnSave,
                        onCheckedChange = { resyncOnSave = it },
                    )
                    Text("Re-sync playlist and guide after saving", style = MaterialTheme.typography.bodyMedium)
                }

                if (testStatus != null) {
                    Text(
                        text = testStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                    SettingsButton(
                        text = "Test Connection",
                        onClick = {
                            testing = true
                            testStatus = "Testing connection…"
                            val draft = source.copy(
                                name = name.ifBlank { source.name },
                                url = url,
                                username = username.takeIf { it.isNotBlank() },
                                password = password.takeIf { it.isNotBlank() },
                                macAddress = mac.takeIf { it.isNotBlank() },
                                epgUrl = epgUrl.takeIf { it.isNotBlank() },
                                userAgent = userAgent.ifBlank { Source.DEFAULT_USER_AGENT },
                                liveFormat = liveFormat,
                            )
                            scope.launch {
                                val res = onTest(draft)
                                testing = false
                                testStatus = res.getOrElse { it.message ?: "Connection failed." }
                            }
                        },
                        style = SettingsButtonStyle.Secondary,
                        enabled = !testing,
                    )
                }
            }
        },
        confirmButton = {
            SettingsButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    val updated = source.copy(
                        name = name.ifBlank { source.name },
                        url = url,
                        username = username.takeIf { it.isNotBlank() },
                        password = password.takeIf { it.isNotBlank() },
                        macAddress = mac.takeIf { it.isNotBlank() },
                        epgUrl = epgUrl.takeIf { it.isNotBlank() },
                        userAgent = userAgent.ifBlank { Source.DEFAULT_USER_AGENT },
                        liveFormat = liveFormat,
                    )
                    onSave(updated, resyncOnSave)
                    onDismiss()
                },
                style = SettingsButtonStyle.Primary,
            )
        },
        dismissButton = {
            SettingsButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                style = SettingsButtonStyle.Secondary,
            )
        },
    )
}

/**
 * The per-source HLS / MPEG-TS picker. A segmented control with a one-line hint on when to reach
 * for it.
 */
@Composable
private fun StreamFormatSelector(
    selected: LiveStreamFormat,
    onSelect: (LiveStreamFormat) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.provider_stream_format),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        SettingsSegmented(
            options = listOf(
                stringResource(R.string.provider_stream_format_hls),
                stringResource(R.string.provider_stream_format_ts),
            ),
            selectedIndex = if (selected == LiveStreamFormat.HLS) 0 else 1,
            onSelect = { index -> onSelect(if (index == 0) LiveStreamFormat.HLS else LiveStreamFormat.MPEG_TS) },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.provider_stream_format_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Host only — the full URL carries the login and has no place on a settings list. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
